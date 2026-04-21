# 设计：Skill API 安全加固（15-add-skill-api-security）

## 1. 认证链路设计（Filter 顺序）

```
HTTP Request
  │
  ├─ URL 不匹配 /fbs/skill-api/** → filterChain.doFilter()（跳过，走 JWT 链路）
  │
  └─ URL 匹配 /fbs/skill-api/**
       │
       ├─ Step 1：API Key 校验（现有逻辑不变）
       │    ├─ X-FBS-API-Key Header 缺失 → 401 SKILL_API_KEY_INVALID
       │    ├─ Key 不存在于 fbs_api_key 表 → 401 SKILL_API_KEY_INVALID
       │    └─ Key 已禁用 (status!=1) → 403 SKILL_API_KEY_DISABLED
       │
       ├─ Step 2：时间戳校验（新增）
       │    ├─ X-FBS-Timestamp Header 缺失 → 401 SKILL_API_TIMESTAMP_MISSING
       │    ├─ |serverTime - timestamp| > 5min → 401 SKILL_API_TIMESTAMP_EXPIRED
       │    └─ 时间戳有效 → 继续
       │
       ├─ Step 3：签名校验（新增）
       │    ├─ X-FBS-Signature Header 缺失 → 401 SKILL_API_SIGNATURE_INVALID
       │    ├─ HMAC 重新计算不匹配 → 401 SKILL_API_SIGNATURE_INVALID
       │    └─ 签名匹配 → 继续
       │
       ├─ Step 4：速率限制（现有逻辑不变）
       │    └─ 超限 → 429 SKILL_API_RATE_LIMITED
       │
       └─ Step 5：设置 SecurityContext + chain.doFilter()
            └─ → FbsSkillApiController 正常处理
```

**校验顺序原则**：先快后慢。API Key 查表（有索引）→ 时间戳解析（纯数学）→ 签名 HMAC 计算（CPU 密集）。任一步失败即返回，不继续。

---

## 2. 签名串格式

### Skill 端签名生成（`backend-api.mjs._signRequest`）

```javascript
import { createHmac } from 'crypto';

function _signRequest(apiKey, timestamp, body) {
  const message = `${timestamp}\n${JSON.stringify(body)}`;
  const signature = createHmac('sha256', apiKey)
    .update(message)
    .digest('hex');
  return signature;
}
```

**签名消息格式**：`${timestamp}\n${JSON.stringify(body)}`

- `timestamp`：Unix 毫秒时间戳（`Date.now()`），字符串形式
- `\n`：换行符（LF，不是 CRLF）
- `body`：`JSON.stringify(body)`，与发送的 request body 完全一致

**⚠️ 关键约束**：后端签名校验必须使用**原始 request body 字符串**（即 `RepeatedlyReadRequestWrapper` 缓存的字节），**不做反序列化再序列化**。原因：`JSON.stringify` 的 key 顺序取决于插入顺序，后端反序列化再序列化可能产生不同字符串，导致签名不匹配。

**示例**：

```
timestamp = "1745143200000"
body      = {"userId":1001,"genre":"genealogy","usageRecordId":"task-abc123"}

message   = "1745143200000\n{\"userId\":1001,\"genre\":\"genealogy\",\"usageRecordId\":\"task-abc123\"}"
signature = HMAC-SHA256(apiKey, message) → hex string (64 chars)
```

### 后端签名校验（`FbsApiKeyAuthService.verifySignature`）

```java
public boolean verifySignature(String apiKey, String timestamp, String body, String signature) {
    String message = timestamp + "\n" + body;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] computed = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    String computedHex = Hex.encodeHexString(computed);
    return MessageDigest.isEqual(computedHex.getBytes(), signature.getBytes());
}
```

**关键**：使用 `MessageDigest.isEqual()` 而非 `String.equals()`，防止时序攻击。

---

## 3. Request Body 重复读取

### 问题

`FbsApiKeyAuthFilter` 需要读取 request body 计算签名，但 `HttpServletRequest.getInputStream()` 只能读一次。后续 `FbsSkillApiController` 的 `@RequestBody` 也需要读取 body，会得到空流。

### 方案：自定义 `RepeatedlyReadRequestWrapper`

`ContentCachingRequestWrapper` 的 `getContentAsByteArray()` 在 `chain.doFilter()` 完成后才填充——这意味着签名校验时缓存还是空的，需要先主动消费 input stream。但 Spring 文档对此行为的说明含糊，实际调试成本高。

**直接使用自定义 `RepeatedlyReadRequestWrapper`**，在构造时立即读取 body 到 `byte[]` 缓存，后续无论 Filter 还是 Controller 读取都从缓存返回：

```java
public class RepeatedlyReadRequestWrapper extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    public RepeatedlyReadRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() {
        return new CachedBodyServletInputStream(cachedBody);
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
```

### Filter 内使用方式

```java
// FbsApiKeyAuthFilter.doFilterInternal()

// Step 0: 包装 request（立即缓存 body）
RepeatedlyReadRequestWrapper wrappedRequest = new RepeatedlyReadRequestWrapper(request);

// Step 1-2: API Key + 时间戳校验（不需要 body）

// Step 3: 签名校验（从缓存读取 body）
String body = new String(wrappedRequest.cachedBody, StandardCharsets.UTF_8);
boolean valid = authService.verifySignature(apiKey, timestamp, body, signature);

if (!valid) {
    // 401 SKILL_API_SIGNATURE_INVALID
    return;
}

// Step 4: 速率限制

// Step 5: 传递 wrappedRequest，Controller 可正常读取 body
filterChain.doFilter(wrappedRequest, response);
```

**关键**：签名消息使用**原始 request body 字符串**，不做反序列化再序列化（避免 key 顺序/空格差异导致签名不匹配）。

---

## 4. 时间戳窗口

- **窗口大小**：5 分钟（±5 分钟，共 10 分钟有效窗口）
- **时钟偏差容忍**：客户端与服务器时间差在 5 分钟内均可接受
- **时间戳来源**：`X-FBS-Timestamp` Header，Unix 毫秒时间戳
- **校验逻辑**：`Math.abs(System.currentTimeMillis() - timestamp) > 300_000`

**不使用 nonce + 服务端存储**的原因：
- 需要持久化存储（Redis/DB），增加依赖
- MVP 阶段时间戳窗口足够防重放
- 后续如需更严格防重放，可加 nonce + Redis TTL

---

## 5. API Key 去规律化

### 当前格式

```
fbs_ + Base64.getUrlEncoder().withoutPadding().encodeToString(SecureRandom(24B))
= fbs_ + 32字符 [A-Za-z0-9_-]
= 36 字符
```

### 新格式

```
fbs_ + Hex.encodeHexString(SecureRandom(32B))
= fbs_ + 64字符 [0-9a-f]
= 68 字符
```

### 后端改动

```java
// FbsApiKeyBusinessServiceImpl.generateUniqueKey()
private String generateUniqueKey() {
    SecureRandom random = new SecureRandom();
    byte[] bytes = new byte[32];  // 24 → 32
    random.nextBytes(bytes);
    String encoded = Hex.encodeHexString(bytes);  // Base64 → Hex
    String candidate = KEY_PREFIX + encoded;

    // 确保唯一
    FbsApiKey existing = apiKeyMapper.selectByApiKey(candidate);
    if (existing != null) {
        return generateUniqueKey();
    }
    return candidate;
}
```

### 数据库迁移

```sql
-- V20260420__15-add-skill-api-security__api_key_varchar_expand.sql
ALTER TABLE fbs_api_key MODIFY COLUMN api_key VARCHAR(128) NOT NULL COMMENT 'API Key（Hex(32B)格式，68字符）';
```

### Skill 端改动

```javascript
// api-key-config.mjs
// 旧：const API_KEY_RE = /^fbs_[a-zA-Z0-9]{32}$/;
const API_KEY_RE = /^fbs_[0-9a-f]{64}$/;
```

### 向后兼容

- 旧 Key（36字符 Base64）与新 Key（68字符 hex）长度不同，Skill 端正则改后旧 Key 会被拒绝
- 内测期 Key 数量少，在后台重新生成即可
- 后端 `fbs_api_key` 表仍可存储旧 Key（VARCHAR(128) 足够），只是 Skill 端不认

---

## 6. 积分文件完整性校验

### 写入时

```javascript
// credits-ledger.mjs._computeLedgerHmac()
function _computeLedgerHmac(balance, totalEarned, totalSpent, apiKey) {
  const message = JSON.stringify({ balance, total_earned: totalEarned, total_spent: totalSpent });
  return createHmac('sha256', apiKey).update(message).digest('hex');
}

// writeLedger() 中
const hmac = apiKey ? _computeLedgerHmac(balance, totalEarned, totalSpent, apiKey) : undefined;
const data = { balance, total_earned: totalEarned, total_spent: totalSpent };
if (hmac) data._hmac = hmac;
fs.writeFileSync(path, JSON.stringify(data, null, 2));
```

### 读取时

```javascript
// readLedger() 中
if (data._hmac && apiKey) {
  const expected = _computeLedgerHmac(data.balance, data.total_earned, data.total_spent, apiKey);
  if (expected !== data._hmac) {
    // HMAC 不匹配 → 文件被篡改
    process.stderr.write('[credits-ledger] HMAC mismatch, local balance reset\n');
    data.balance = 0;
    data._hmacInvalid = true;  // 标记，供 getBalance() 判断需从后端拉取
  }
}
```

---

## 7. 错误码汇总（与 #5 对齐）

| HTTP Status | 错误码 | 说明 | 来源 |
|-------------|--------|------|------|
| 401 | `SKILL_API_KEY_INVALID` | API Key 缺失/不存在 | #5 |
| 401 | `SKILL_API_SIGNATURE_INVALID` | 签名缺失/不匹配 | **#15 新增** |
| 401 | `SKILL_API_TIMESTAMP_MISSING` | 时间戳缺失 | **#15 新增** |
| 401 | `SKILL_API_TIMESTAMP_EXPIRED` | 时间戳过期 | **#15 新增** |
| 403 | `SKILL_API_KEY_DISABLED` | API Key 已禁用 | #5 |
| 429 | `SKILL_API_RATE_LIMITED` | 速率超限 | #5 |

**规则**：所有认证/签名/时间戳失败归 401；Key 状态问题归 403；限流归 429。

**⚠️ 关于 API Key 存储方式**：本方案 HMAC 签名需用数据库中的明文 API Key 重新计算签名，因此 `fbs_api_key.api_key` 字段必须存明文，**不再迁移到 SHA-256 hash**（#5 原计划的 hash 迁移路线已取消）。

---

## 8. 纯本地模式与网络故障

### 纯本地模式（无 API Key）

无 API Key 时：
- `backend-api.mjs` 不生成签名/时间戳 Header，不发送后端请求
- `credits-ledger.mjs` 不计算/校验 HMAC
- 后端 Filter 不拦截（请求走 JWT 链路或不发送）

### 配了 API Key 但后端不可达

有 API Key 但网络不通时：
- `backend-api.mjs._post()` 生成签名/时间戳 Header 并发送请求
- 请求失败（网络错误/超时）→ `_post()` 返回 `null` + stderr warn
- `credits-ledger.mjs.getBalance()` fallback 到本地文件读取
- **积分文件 HMAC**：由于后端不可达，本地余额可能滞后。恢复后下次 `getBalance()` 会从后端拉取最新余额
- **签名/时间戳不影响本地操作**——签名只在发送请求时生成，后端不通就不发
