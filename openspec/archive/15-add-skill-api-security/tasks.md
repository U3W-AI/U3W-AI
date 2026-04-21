# 任务清单：Skill API 安全加固（15-add-skill-api-security）

> **排序原则**：最小可跑通主链路优先——先让最基础的信任链路（Key 格式 → 签名 → Filter 校验）跑通，再做积分文件 HMAC。

## 任务 1：API Key 新格式（后端 + Skill）

> **目标**：Key 去规律化，前后端格式统一，为后续签名提供密钥基础

- [ ] 1.1 `FbsApiKeyBusinessServiceImpl.generateUniqueKey()` 改为 `Hex(32B)`：`SecureRandom(32B)` → `Hex.encodeHexString()`，总长 68 字符
- [ ] 1.2 `fbs_api_key` 表 `api_key` 字段 `VARCHAR(64)` → `VARCHAR(128)`（新建迁移 SQL）
- [ ] 1.3 `api-key-config.mjs` 的 `API_KEY_RE` 改为 `/^fbs_[0-9a-f]{64}$/`
- [ ] 1.4 `api-key-config.mjs` 的 `_validateKey()` + `setApiKey()` 错误提示改为「64位hex随机串」
- [ ] 1.5 测试：`FbsApiKeyBusinessServiceTest` mock Key 更新为 68 字符 hex 格式
- [ ] 1.6 文档：`SKILL.md` 引导文案改为「格式：fbs_ + 64位hex」

**验证点**：新 Key 格式后端可生成、Skill 可校验，旧格式 Key Skill 端友好拒绝

## 任务 2：Skill 端签名基础设施

> **目标**：Skill 发出的请求带上签名 + 时间戳，为后端校验提供输入

- [ ] 2.1 `backend-api.mjs` 新增 `_signRequest(apiKey, timestamp, body)` 方法：`HMAC-SHA256(apiKey, timestamp + '\n' + body)`，返回 signature hex
- [ ] 2.2 `_post()` 中添加 `X-FBS-Timestamp`（`Date.now()`）和 `X-FBS-Signature` Header
- [ ] 2.3 确保无 API Key 时不生成签名（纯本地模式不受影响）
- [ ] 2.4 验证：有 API Key 时请求包含 Timestamp + Signature Header

**验证点**：Skill 发出的请求包含正确的签名和时间戳 Header；纯本地模式不发

## 任务 3：后端 Wrapper + Filter 校验

> **目标**：后端能读取 body 做签名校验，Filter 链路完整拦截无签名/过期/篡改请求

- [ ] 3.1 新建 `RepeatedlyReadRequestWrapper` 类（构造时立即缓存 body 到 `byte[]`），替代 `ContentCachingRequestWrapper`（后者在 `chain.doFilter()` 完成后才填充缓存，签名校验时不可用）
- [ ] 3.2 `FbsApiKeyAuthService` 新增 `verifyTimestamp(timestamp)` 方法：`|serverTime - timestamp| < 5min`
- [ ] 3.3 `FbsApiKeyAuthService` 新增 `verifySignature(apiKey, timestamp, body, signature)` 方法，使用 `MessageDigest.isEqual()` 防时序攻击
- [ ] 3.4 `FbsApiKeyAuthFilter.doFilterInternal()` 开头用 `RepeatedlyReadRequestWrapper` 包装 request
- [ ] 3.5 Filter 校验链：API Key → 时间戳（缺失→401 `SKILL_API_TIMESTAMP_MISSING`；过期→401 `SKILL_API_TIMESTAMP_EXPIRED`）→ 签名（缺失/不匹配→401 `SKILL_API_SIGNATURE_INVALID`）→ 速率限制
- [ ] 3.6 确保后续 Controller 仍能正常读取 request body
- [ ] 3.7 单元测试：签名校验通过/失败/时间戳过期/时间戳缺失场景
- [ ] 3.8 集成测试：签名校验通过的请求，Controller 仍能正确解析 body

**验证点**：无签名→401，篡改 body→401，过期时间戳→401，合法请求→200 + Controller 正常解析 body

## 任务 4：集成验证（端到端主链路）

> **目标**：全链路跑通——Key 格式 + 签名 + Filter 校验 + 纯本地模式

- [ ] 4.1 新生成的 API Key 格式为 `fbs_` + 64位hex，Skill 端校验通过
- [ ] 4.2 旧格式 API Key（36字符 Base64）Skill 端校验失败 + 友好提示
- [ ] 4.3 Skill 发送带签名的请求 → 后端校验通过 → 正常返回
- [ ] 4.4 Skill 发送无签名的请求 → 后端拒绝 401
- [ ] 4.5 Skill 发送篡改 body 的请求（签名不匹配）→ 后端拒绝 401
- [ ] 4.6 Skill 发送过期时间戳的请求 → 后端拒绝 401
- [ ] 4.7 纯本地模式（无 API Key）→ 不发送签名 → 后端不拦截 → Skill 正常运行

**验证点**：主链路端到端通过，纯本地模式不受影响

## 任务 5：积分文件 HMAC 校验

> **目标**：本地积分文件防篡改——这是独立于请求签名的本地完整性保护，放在主链路之后

- [ ] 5.1 新增 `_computeLedgerHmac(balance, total_earned, total_spent, apiKey)` 方法
- [ ] 5.2 `writeLedger()` 写入时附加 `_hmac` 字段
- [ ] 5.3 `readLedger()` 读取时校验 `_hmac`，校验失败 → 清零 balance + warn + 强制从后端拉取
- [ ] 5.4 无 API Key 时跳过 HMAC（纯本地模式无完整性保护）
- [ ] 5.5 验证：手动篡改 `credits-ledger.json` 的 balance 后读取，触发校验失败 + 余额清零 + 从后端拉取

**验证点**：篡改文件→HMAC 不匹配→余额清零→后端同步恢复

## 任务 6：文档更新

- [ ] 6.1 `SKILL.md` 安全说明章节：API Key 新格式 + 请求签名机制 + 时间戳防重放 + 积分文件校验
- [ ] 6.2 API 文档更新：`/fbs/skill-api/**` 端点新增必填 Header 说明 + 错误码表
