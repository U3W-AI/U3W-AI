# 常驻规范：Skill API 安全（skill-api-security）

> 来源：#15 add-skill-api-security，归档于 2026-04-20

---

## 需求

### R1：API Key 去规律化

后端生成 API Key 时使用 `fbs_` + `Hex(SecureRandom(32B))` 格式：
- 前缀 `fbs_`（4 字符）+ 64 位 hex 字符串（`[0-9a-f]`），总长 68 字符，熵 256 bit
- Skill 端校验正则：`/^fbs_[0-9a-f]{64}$/`
- 数据库字段：`fbs_api_key.api_key VARCHAR(128)`
- API Key 明文存储，不迁移到 SHA-256 hash（HMAC 签名依赖明文）

### R2：请求签名（HMAC-SHA256）

Skill 向后端 `/fbs/skill-api/**` 发送请求时，必须附加：
1. `X-FBS-Timestamp`：Unix 毫秒时间戳
2. `X-FBS-Signature`：`HMAC-SHA256(apiKey, timestamp + '\n' + body)` 的十六进制表示

签名消息格式：`${timestamp}\n${JSON.stringify(body)}`

后端使用 `MessageDigest.isEqual()` 比对签名（防时序攻击），使用原始 request body 字符串（`RepeatedlyReadRequestWrapper` 缓存），不做反序列化再序列化。

纯本地模式（无 API Key）不生成签名，不发送后端请求。

### R3：时间戳防重放

后端收到 `/fbs/skill-api/**` 请求时：
- 缺少 `X-FBS-Timestamp` → 401 `SKILL_API_TIMESTAMP_MISSING`
- `|serverTime - timestamp| > 5min` → 401 `SKILL_API_TIMESTAMP_EXPIRED`

### R4：积分文件完整性校验

`credits-ledger.mjs` 写入时附加 `_hmac = HMAC-SHA256(apiKey, JSON.stringify({balance, total_earned, total_spent}))`，读取时校验，校验失败则 balance 清零 + 标记需从后端拉取。无 API Key 时跳过 HMAC。

---

## 技术约束

- API Key 明文存储（不迁 SHA-256 hash），HMAC 签名需原文重算
- `FbsApiKeyAuthFilter` 使用 `RepeatedlyReadRequestWrapper`（非 `ContentCachingRequestWrapper`）包装 request
- 签名比对用 `MessageDigest.isEqual()`（防时序攻击）
- 积分文件 HMAC 比对用普通字符串比较（本地文件场景时序攻击风险极低）
- **URL 拼接安全**：`FBS_API_BASE_URL` 末尾斜杠会导致 URL 双斜杠（如 `http://localhost:8080//fbs/skill-api/user/info`），后端返回 404/500，`getBalance()` fallback 到本地缓存导致余额不更新。`backend-api.mjs._apiUrl()` 和 `api-key-config.mjs.setApiBaseUrl()` 必须去除末尾斜杠再拼接

---

## Filter 校验链

```
API Key → 时间戳 → 签名 → 速率限制
```

| 步骤 | 失败响应 | 错误码 |
|------|---------|--------|
| API Key 无效 | 401 | `SKILL_API_KEY_INVALID` |
| API Key 禁用 | 403 | `SKILL_API_KEY_DISABLED` |
| 时间戳缺失 | 401 | `SKILL_API_TIMESTAMP_MISSING` |
| 时间戳过期 | 401 | `SKILL_API_TIMESTAMP_EXPIRED` |
| 签名缺失/不匹配 | 401 | `SKILL_API_SIGNATURE_INVALID` |
| 速率超限 | 429 | `SKILL_API_RATE_LIMITED` |

---

## 延期事项

- API Key 加密存储（OS 密钥链）→ 实现复杂度高 + HMAC 签名依赖明文
- API Key 轮换 → P2，当前可手动禁用+重新创建
- 后端响应签名 → P2，HTTPS 已提供传输层保护
- Redis 限流 → P3，内存级限流暂时够用
