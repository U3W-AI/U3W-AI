# 提案：Skill API 安全加固（add-skill-api-security）

## Why

**问题**：#14 完成后，Skill 与后端通过 API Key（`X-FBS-API-Key` Header）明文通信，存在以下安全风险：

| 风险 | 现状 | 影响 |
|------|------|------|
| **API Key 生成有规律** | `fbs_` + Base64(24B) = 36字符，字符集 `[A-Za-z0-9_-]`，编码规律明显 | 容易被识别为 FBS 系统密钥，降低暴力搜索空间 |
| **API Key 明文存储** | `FBS_API_KEY` 环境变量明文，`~/.fbs/config.json` 明文写入 | 本机用户可直接读取 Key（**本期不修**，HMAC 签名依赖明文存储） |
| **请求可篡改** | 后端只校验 API Key 是否有效，不校验请求体完整性 | 中间人可修改扣减金额/用户ID |
| **请求可重放** | 无时间戳校验，同一请求可被截获后重复发送 | 积分被重复扣减或重复赚取 |
| **积分可窃取** | 本地 `credits-ledger.json` 可直接编辑 | 用户可手动改余额绕过门禁 |

**背景**：
- `FbsApiKeyAuthFilter` 只检查 API Key 是否存在且启用 + 速率限制，不校验请求签名或时间戳
- `backend-api.mjs` 的 `_post()` 只发送 `Content-Type` + `X-FBS-API-Key` 两个 Header
- `api-key-config.mjs` 将 API Key 明文写入 `~/.fbs/config.json`
- `credits-ledger.mjs` 写入 `credits-ledger.json` 时无完整性校验

---

## What Changes

**核心决策：最小安全加固——API Key 去规律化 + 请求签名 + 时间戳防重放 + 积分文件校验，全部为本轮 MVP。API Key 加密存储延期（实现复杂度高，单独迭代）。**

### P1：API Key 去规律化

**问题**：当前 `generateUniqueKey()` 使用 `fbs_` + `Base64.getUrlEncoder().withoutPadding().encodeToString(24B)`，生成的 Key：
- 前缀固定 `fbs_`，一眼识别为 FBS 系统密钥
- 字符集 `[A-Za-z0-9_-]` 是 URL-safe Base64 特征，编码规律明显
- 长度 36 字符偏短，且与其他 FBS Token 格式一致

**方案**：改用 `fbs_` + `Hex(32B)` 生成：
- 格式：`fbs_` + 64 位 hex 字符串，总长 68 字符
- 字符集 `[0-9a-f]`，与 SHA-256 hash 格式一致，不暴露编码规律
- 熵 256 bit（32 字节），暴力破解不可能
- 前缀 `fbs_` 保留（后端 Filter 需要快速判断请求类型）

**需同步修改**（6 处）：

| # | 位置 | 当前 | 改为 |
|---|------|------|------|
| 1 | `FbsApiKeyBusinessServiceImpl.generateUniqueKey()` | `Base64(24B)` → 36字符 | `Hex(32B)` → 68字符 |
| 2 | `fbs_api_key` 表 `api_key` 字段 | `VARCHAR(64)` | `VARCHAR(128)` |
| 3 | `api-key-config.mjs` → `API_KEY_RE` | `/^fbs_[a-zA-Z0-9]{32}$/` | `/^fbs_[0-9a-f]{64}$/` |
| 4 | `api-key-config.mjs` → `_validateKey()` 错误提示 | `32位字母数字随机串` | `64位hex随机串` |
| 5 | `api-key-config.mjs` → `setApiKey()` 错误提示 | 同上 | 同上 |
| 6 | 测试 + 文档中引用旧格式的 mock Key / 描述 | 36字符 Base64 | 68字符 hex |

**兼容性**：旧格式 Key（36字符 Base64）和新格式 Key（68字符 hex）长度不同，Skill 端正则改后旧 Key 校验失败。内测期 Key 数量少，重新生成即可。

### P1：请求签名（HMAC-SHA256）

1. **Skill 端**：`backend-api.mjs` 的 `_post()` 在每次请求时生成签名
   - 新增 `X-FBS-Timestamp`：Unix 毫秒时间戳
   - 新增 `X-FBS-Signature`：`HMAC-SHA256(apiKey, timestamp + '\n' + body)`
   - 签名消息 = `${timestamp}\n${JSON.stringify(body)}`
2. **后端**：`FbsApiKeyAuthFilter` 在认证通过后校验签名
   - 读取 `X-FBS-Timestamp`，拒绝超过 5 分钟的请求
   - 用数据库中的 API Key 重新计算 HMAC，比对签名
   - 签名不匹配 → 401 `SKILL_API_SIGNATURE_INVALID`

### P1：时间戳防重放

- 请求必须携带 `X-FBS-Timestamp`（Unix 毫秒）
- 后端校验 `|serverTime - timestamp| < 5min`
- 超时 → 401 `SKILL_API_TIMESTAMP_EXPIRED`

### P1：积分文件完整性校验

- `credits-ledger.mjs` 写入 `credits-ledger.json` 时，附加 `_hmac` 字段
  - `_hmac = HMAC-SHA256(apiKey, JSON.stringify({balance, total_earned, total_spent}))`
- 读取时校验 `_hmac`，校验失败 → 清零本地余额 + 强制从后端拉取

### 延期（不在此提案中）

- ~~API Key 加密存储（OS 密钥链）~~ → 实现复杂度高（Windows DPAPI + macOS Keychain），且 HMAC 签名方案依赖明文存储，单独迭代
- ~~API Key 轮换~~ → P2，当前可手动禁用+重新创建
- ~~后端响应签名~~ → P2，HTTPS 已提供传输层保护
- ~~Redis 限流~~ → P3，内存级限流暂时够用

### 与 #14 已有成果的关系

| 安全维度 | #14 现状 | 本提案 |
|----------|---------|--------|
| API Key 格式 | Base64(24B) 36字符，规律明显 | Hex(32B) 68字符，去规律化 |
| API Key 传输 | 明文 Header | 不变（HTTPS 保护传输层） |
| API Key 存储 | 明文 `~/.fbs/config.json` | 延期加密存储（HMAC 签名依赖明文，不迁 hash） |
| 请求完整性 | 无校验 | HMAC-SHA256 签名 |
| 请求防重放 | 无 | 5 分钟时间戳窗口 |
| 积分文件完整性 | 无 | 写入 HMAC + 读取校验 |

---

## Impact

### 受影响的规范
- 本提案**新增 capability `skill-api-security`**，归档后常驻规范路径为 `openspec/specs/skill-api-security/spec.md`
- 规范差异见 `specs/skill-api-security/spec-delta.md`

### 受影响的代码（后端）
- `FbsApiKeyBusinessServiceImpl.java` - `generateUniqueKey()` 改用 Hex(32B)
- `FbsApiKeyAuthFilter.java` - 新增签名校验 + 时间戳校验逻辑
- `FbsApiKeyAuthService.java` - 新增签名验证方法
- `fbs_api_key` 表 - `api_key` 字段 VARCHAR(64) → VARCHAR(128)

### 受影响的代码（Skill 端）
- `api-key-config.mjs` - `API_KEY_RE` 正则 + 错误提示更新
- `backend-api.mjs` - `_post()` 生成签名 + 时间戳 Header
- `credits-ledger.mjs` - 写入时附加 `_hmac`，读取时校验

### 不受影响的代码
- `FbsSkillApiController.java` - 签名校验在 Filter 层完成，Controller 无需改动
- `entitlement.mjs` - 无安全相关改动
- `fbs-points.mjs` - 无安全相关改动

### API 变更
- 无新增端点
- 所有 `/fbs/skill-api/**` 请求新增两个必填 Header：`X-FBS-Timestamp` + `X-FBS-Signature`
- **破坏性变更 1**：无签名的请求将被拒绝（401）
- **破坏性变更 2**：旧格式 API Key（36字符）不再被 Skill 端接受，需重新生成

### 用户影响
- Skill 端自动生成签名，用户无需手动操作
- 无 API Key 的纯本地模式不受影响（不发送后端请求）
- 已部署的 Skill 版本需更新（向后不兼容，但当前处于内测期）
- 旧 API Key 需在后台重新生成（内测期 Key 数量少，可接受）

### 风险
- 时钟偏差：客户端/服务器时间不同步可能导致误拒。缓解：5 分钟窗口较宽
- 签名计算性能：HMAC-SHA256 计算量极小，不构成瓶颈

---

## 时间线评估

中等规模，涉及 API Key 格式改造 + 后端 Filter 签名校验 + Skill 端签名生成 + 积分文件校验，预计 2-3 天
