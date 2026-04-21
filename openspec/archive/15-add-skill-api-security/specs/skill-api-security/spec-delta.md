# 规范差异：Skill API 安全加固

本文件包含对 FBS-BookWriter Skill API 通信安全的规范变更。

---

## ADDED Requirements

### Requirement: API Key 去规律化

WHEN 后端生成新 API Key，
系统 SHALL 使用 `fbs_` + `Hex(SecureRandom(32B))` 格式：
- 前缀 `fbs_`（4 字符）+ 64 位 hex 字符串（`[0-9a-f]`）
- 总长度 68 字符
- 熵 256 bit

WHEN Skill 端校验 API Key 格式，
系统 SHALL 使用正则 `/^fbs_[0-9a-f]{64}$/`，
IF Key 不匹配此格式，
THEN 系统 SHALL 返回 null + 输出 warn 日志「API Key 格式无效（需 fbs_ 前缀 + 64位hex随机串）」。

WHEN 后端存储 API Key 到 `fbs_api_key` 表，
`api_key` 字段 SHALL 为 `VARCHAR(128)`。

#### Scenario: 新格式 Key 生成

GIVEN 管理员或用户调用生成 API Key 接口
WHEN 后端生成新 Key
THEN Key 格式 SHALL 为 `fbs_` + 64 位 hex
AND Key 总长度 SHALL 为 68 字符
AND Key 中 `fbs_` 之后的部分 SHALL 仅包含 `[0-9a-f]`

#### Scenario: Skill 端接受新格式 Key

GIVEN 用户配置了新格式 API Key `fbs_a3f7c9e2b1d8...`（68字符）
WHEN Skill 端调用 `getApiKey()` 校验
THEN 校验通过，返回完整 Key

#### Scenario: Skill 端拒绝旧格式 Key

GIVEN 用户配置了旧格式 API Key `fbs_xYz9AbC123...`（36字符，含大写字母）
WHEN Skill 端调用 `getApiKey()` 校验
THEN 校验失败，返回 null + warn 日志

### Requirement: 请求签名（HMAC-SHA256）

WHEN Skill 向后端 `/fbs/skill-api/**` 发送 POST 请求，
系统 SHALL 在请求中附加以下 Header：
1. `X-FBS-Timestamp`：Unix 毫秒时间戳（`Date.now()`）
2. `X-FBS-Signature`：`HMAC-SHA256(apiKey, timestamp + '\n' + body)` 的十六进制表示

AND 签名消息格式 SHALL 为 `${timestamp}\n${JSON.stringify(body)}`。

WHEN 后端收到 `/fbs/skill-api/**` 请求，
后端 SHALL 在 API Key 认证通过后，使用数据库中的 API Key 重新计算 HMAC，
IF 计算结果与 `X-FBS-Signature` 不匹配，
THEN 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_SIGNATURE_INVALID`。

AND 后端 SHALL 使用 `MessageDigest.isEqual()` 比对签名，防止时序攻击。

AND 后端签名校验 SHALL 使用原始 request body 字符串（从 `RepeatedlyReadRequestWrapper` 缓存），不做反序列化再序列化（避免 key 顺序差异导致签名不匹配）。

WHEN Skill 处于纯本地模式（无 API Key），
系统 SHALL 不生成签名 Header，且不发送后端请求。

#### Scenario: 正常签名请求

GIVEN Skill 配置了有效 API Key
WHEN Skill 发送 POST `/fbs/skill-api/user/info`，body 为 `{}`
THEN 请求 SHALL 包含 `X-FBS-Timestamp`（当前毫秒时间戳）
AND 请求 SHALL 包含 `X-FBS-Signature`（HMAC-SHA256 签名）
AND 后端校验签名通过后正常处理请求

#### Scenario: 签名不匹配（请求被篡改）

GIVEN Skill 发送的请求 body 在传输中被修改
WHEN 后端用 API Key 重新计算 HMAC
THEN 计算结果与 `X-FBS-Signature` 不匹配
AND 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_SIGNATURE_INVALID`

#### Scenario: 签名缺失

GIVEN 请求不包含 `X-FBS-Signature` Header
WHEN 后端检测到签名缺失
THEN 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_SIGNATURE_INVALID`

### Requirement: 时间戳防重放

WHEN 后端收到 `/fbs/skill-api/**` 请求，
IF 请求不包含 `X-FBS-Timestamp` Header，
THEN 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_TIMESTAMP_MISSING`。

IF `|serverTime - timestamp| > 5 分钟`，
THEN 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_TIMESTAMP_EXPIRED`。

IF `|serverTime - timestamp| ≤ 5 分钟`，
THEN 后端 SHALL 继续签名校验流程。

#### Scenario: 时间戳过期

GIVEN 请求的 `X-FBS-Timestamp` 为 6 分钟前
WHEN 后端校验时间戳
THEN 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_TIMESTAMP_EXPIRED`

#### Scenario: 时间戳缺失

GIVEN 请求不包含 `X-FBS-Timestamp` Header
WHEN 后端检测到时间戳缺失
THEN 后端 SHALL 返回 HTTP 401，错误码 `SKILL_API_TIMESTAMP_MISSING`

#### Scenario: 时间戳在有效窗口内

GIVEN 请求的 `X-FBS-Timestamp` 为 30 秒前
WHEN 后端校验时间戳
THEN 时间戳校验通过
AND 后端继续签名校验

### Requirement: 积分文件完整性校验

WHEN `credits-ledger.mjs` 写入 `credits-ledger.json`，
IF API Key 已配置，
系统 SHALL 附加 `_hmac` 字段，值为 `HMAC-SHA256(apiKey, JSON.stringify({balance, total_earned, total_spent}))`。

WHEN `credits-ledger.mjs` 读取 `credits-ledger.json`，
IF API Key 已配置且文件包含 `_hmac` 字段，
系统 SHALL 校验 HMAC，
IF 校验失败（文件被篡改），
THEN 系统 SHALL 将 `balance` 清零 + 记录 warn 日志 + 标记需从后端重新拉取。

WHEN API Key 未配置（纯本地模式），
系统 SHALL 跳过 HMAC 校验（无完整性保护）。

#### Scenario: 积分文件被手动篡改

GIVEN 用户手动修改 `credits-ledger.json` 的 `balance` 从 100 改为 9999
WHEN Skill 读取 `credits-ledger.json`
THEN HMAC 校验失败
AND `balance` 被清零
AND 日志记录 `credits-ledger HMAC mismatch, local balance reset`
AND 后续 `getBalance()` 从后端拉取真实余额

#### Scenario: 正常写入和读取

GIVEN API Key 已配置，当前 balance=1641
WHEN Skill 写入 `credits-ledger.json`
THEN 文件 SHALL 包含 `_hmac` 字段
WHEN Skill 再次读取
THEN HMAC 校验通过，balance=1641

#### Scenario: 纯本地模式无校验

GIVEN API Key 未配置
WHEN Skill 写入 `credits-ledger.json`
THEN 文件 SHALL 不包含 `_hmac` 字段
WHEN Skill 读取
THEN 不执行 HMAC 校验

---

## MODIFIED Requirements

### Requirement: API Key 生成格式

**Previous**：`generateUniqueKey()` 使用 `fbs_` + `Base64.getUrlEncoder().withoutPadding().encodeToString(SecureRandom(24B))`，总长 36 字符，字符集 `[A-Za-z0-9_-]`。

**Now**：`generateUniqueKey()` 使用 `fbs_` + `Hex.encodeHexString(SecureRandom(32B))`，总长 68 字符，字符集 `[0-9a-f]`。

AND `fbs_api_key` 表 `api_key` 字段从 `VARCHAR(64)` 改为 `VARCHAR(128)`。

AND Skill 端 `api-key-config.mjs` 的 `API_KEY_RE` 从 `/^fbs_[a-zA-Z0-9]{32}$/` 改为 `/^fbs_[0-9a-f]{64}$/`。

### Requirement: Skill API 请求认证流程

**Previous**：`FbsApiKeyAuthFilter` 校验流程为两步：
1. API Key 校验（`X-FBS-API-Key` 是否有效）
2. 速率限制检查

错误码：401 `SKILL_API_KEY_INVALID` / 403 `SKILL_API_KEY_DISABLED` / 429 `SKILL_API_RATE_LIMITED`

**Now**：`FbsApiKeyAuthFilter` 校验流程扩展为四步：
1. API Key 校验（原有逻辑不变：无效→401 `SKILL_API_KEY_INVALID`，禁用→403 `SKILL_API_KEY_DISABLED`）
2. 时间戳校验（新增：缺失→401 `SKILL_API_TIMESTAMP_MISSING`，过期→401 `SKILL_API_TIMESTAMP_EXPIRED`）
3. 签名校验（新增：缺失/不匹配→401 `SKILL_API_SIGNATURE_INVALID`）
4. 速率限制检查（原有逻辑不变：超限→429 `SKILL_API_RATE_LIMITED`）

校验顺序：API Key → 时间戳 → 签名 → 速率限制。任一步失败即返回，不继续后续校验。

AND `FbsApiKeyAuthFilter` SHALL 使用 `RepeatedlyReadRequestWrapper`（而非 `ContentCachingRequestWrapper`）包装 request，在构造时立即缓存 body 到 `byte[]`，使 Filter 和 Controller 均可读取 body。

---

## REMOVED Requirements

（无移除需求）

---

## 备注

- API Key 格式从 Base64 改为 Hex，去掉了 URL-safe Base64 的编码规律，使 Key 看起来与 SHA-256 hash 格式一致
- 签名使用 HMAC-SHA256（而非 RSA/ECDSA），因为：API Key 本身就是共享密钥，且 HMAC 计算更快、实现更简单
- 后端签名比对使用 `MessageDigest.isEqual()` 而非 `String.equals()`，防止时序攻击
- 5 分钟时间窗口是业界常见的防重放策略，允许客户端/服务器有一定时钟偏差
- `credits-ledger.json` 的 HMAC 保护是本地完整性校验，不替代后端权威余额
- 积分文件 HMAC 比对使用 `!==`（普通字符串比较）而非 constant-time 比较——本地文件校验场景下时序攻击风险极低（攻击者需本机执行代码观察比对耗时，若已有本机执行能力则可直接编辑文件绕过 HMAC），故不引入额外复杂度
- 后端校验签名需要读取 request body，使用 `RepeatedlyReadRequestWrapper` 包装 request（构造时立即缓存 body 到 `byte[]`），使 Filter 和 Controller 均可读取 body
- **破坏性变更 1**：#14 之前的 Skill 版本发送的请求无签名/时间戳，会被后端拒绝。当前处于内测期，可接受
- **破坏性变更 2**：旧格式 API Key（36字符 Base64）不再被 Skill 端正则接受，需重新生成。内测期 Key 数量少，可接受
