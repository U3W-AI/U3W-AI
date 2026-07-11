# DeepSeek AI 调试指南

> **更新日期**：2026-07-11

> **重要更新 (2026-01-13)**
> - ✅ **消息类型隔离**：AI业务使用 `AI_TASK_*`，非AI业务使用 `TASK_*`
> - ✅ **登录检测**：`DEEPSEEK_CHECK_LOGIN` → 返回 `TASK_RESULT`（非AI业务）
> - ✅ **扫码登录**：`DEEPSEEK_SCAN_LOGIN` → 返回 `TASK_LOG`、`TASK_SCREENSHOT`、`TASK_RESULT`
> - ✅ **AI对话**：`AI_DEEPSEEK_QUERY` → 返回 `AI_TASK_LOG`、`AI_TASK_SCREENSHOT`、`AI_TASK_RESULT`

## 快速开始

### 1. 获取Token

```bash
export TOKEN=$(curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')
```

### 2. WebSocket连接

```bash
websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}"
```

---

## 消息类型

### 1. 检查登录状态（单次返回）

**发送请求**：
```json
{"type":"AI_DEEPSEEK_CHECK_LOGIN","engineId":"engine-001"}
```

**接收响应**（单次返回）：
```json
{
  "type": "TASK_RESULT",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_CHECK_LOGIN_001",
    "success": true,
    "message": "DeepSeek已登录，用户: 张三",
    "data": {
      "isLoggedIn": true,
      "userName": "张三",
      "platform": "DeepSeek"
    },
    "timestamp": 1735113600000
  }
}
```

---

### 2. 扫码登录（流式返回）

**发送请求**：
```json
{"type":"AI_DEEPSEEK_SCAN_LOGIN","engineId":"engine-001"}
```

**接收响应**（多次，每10秒推送一次二维码）：

进度通知1：
```json
{
  "type": "TASK_PROGRESS",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_SCAN_LOGIN_001",
    "message": "请使用微信扫码登录（已等待10秒）",
    "qrCodeUrl": "http://localhost:8080/screenshots/admin/deepseek_qrcode_1.png",
    "status": "waiting",
    "elapsedSeconds": 10,
    "screenshotCount": 1,
    "timestamp": 1735113610000
  }
}
```

进度通知2（10秒后）：
```json
{
  "type": "TASK_PROGRESS",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_SCAN_LOGIN_001",
    "message": "请使用微信扫码登录（已等待20秒）",
    "qrCodeUrl": "http://localhost:8080/screenshots/admin/deepseek_qrcode_2.png",
    "status": "waiting",
    "elapsedSeconds": 20,
    "screenshotCount": 2,
    "timestamp": 1735113620000
  }
}
```

最终结果（登录成功）：
```json
{
  "type": "TASK_RESULT",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_SCAN_LOGIN_001",
    "success": true,
    "message": "登录成功！欢迎，张三",
    "data": {
      "success": true,
      "userName": "张三",
      "qrCodeUrl": "http://localhost:8080/screenshots/admin/deepseek_qrcode_3.png",
      "loginTime": 35
    },
    "timestamp": 1735113635000
  }
}
```

---

### 3. AI咨询 - 普通模式（流式返回）

**发送请求**（一行格式）：
```json
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"什么是人工智能？","enableDeepThinking":false,"enableWebSearch":false,"sessionId":"your-session-uuid","aiType":"deepseek"}}
```

**接收响应**（多次，每6秒推送一次进度）：

进度通知1：
```json
{
  "type": "TASK_PROGRESS",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_QUERY_001",
    "message": "正在发送问题...",
    "status": "sending",
    "mode": "normal",
    "timestamp": 1735113600000
  }
}
```

进度通知2：
```json
{
  "type": "TASK_PROGRESS",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_QUERY_001",
    "message": "AI正在思考中（已等待6秒）...",
    "status": "processing",
    "screenshotUrl": "http://localhost:8080/screenshots/admin/deepseek_progress_1.png",
    "elapsedSeconds": 6,
    "screenshotCount": 1,
    "timestamp": 1735113606000
  }
}
```

最终结果：
```json
{
  "type": "TASK_RESULT",
  "userId": "admin",
  "payload": {
    "requestId": "admin_20251225_DEEPSEEK_QUERY_001",
    "success": true,
    "message": "DeepSeek回复完成",
    "data": {
      "answer": "人工智能（AI）是计算机科学的一个分支...",
      "chatId": "abc123def456",
      "shareUrl": "https://chat.deepseek.com/a/chat/s/abc123def456",
      "conversationScreenshot": "http://localhost:8080/screenshots/admin/deepseek_conversation_abc123def456.png",
      "mode": "normal",
      "query": "什么是人工智能？",
      "elapsedTime": 18
    },
    "timestamp": 1735113618000
  }
}
```

---

### 4. AI咨询 - 深度思考模式

**发送请求**（一行格式）：
```json
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"请深度分析量子计算的发展趋势","enableDeepThinking":true,"enableWebSearch":false}}
```

**说明**：响应格式同普通模式，但等待时间更长（最多22.5分钟）

---

### 5. AI咨询 - 联网搜索模式

**发送请求**（一行格式）：
```json
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"2024年AI领域有哪些最新突破？","enableDeepThinking":false,"enableWebSearch":true}}
```

**说明**：响应格式同普通模式，但会包含最新网络信息

---

### 6. AI咨询 - 深度思考+联网搜索

**发送请求**（一行格式）：
```json
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"分析当前AI行业的发展趋势和挑战","enableDeepThinking":true,"enableWebSearch":true}}
```

**说明**：最强模式，等待时间最长（最多30分钟）

---

### 7. 继续会话

**发送请求**（使用上次返回的chatId，一行格式）：
```json
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"能举个具体的例子吗？","enableDeepThinking":false,"enableWebSearch":false,"chatId":"abc123def456"}}
```

**说明**：传入chatId后，AI能理解上下文，实现连续对话

---

## 完整测试流程

```bash
# 1. 获取Token
export TOKEN=$(curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# 2. 连接WebSocket
websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}"

# 3. 检查登录状态
{"type":"AI_DEEPSEEK_CHECK_LOGIN","engineId":"engine-001"}

# 4. 如果未登录，执行扫码登录
{"type":"AI_DEEPSEEK_SCAN_LOGIN","engineId":"engine-001"}

# 5. 登录成功后，开始提问
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"什么是人工智能？","enableDeepThinking":false,"enableWebSearch":false}}

# 6. 继续对话（使用返回的chatId）
{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"能举个例子吗？","enableDeepThinking":false,"enableWebSearch":false,"chatId":"abc123def456"}}
```

---

## 参数说明

### AI_DEEPSEEK_CHECK_LOGIN
- **type**: `AI_DEEPSEEK_CHECK_LOGIN`
- **engineId**: Engine ID（必填）

### AI_DEEPSEEK_SCAN_LOGIN
- **type**: `AI_DEEPSEEK_SCAN_LOGIN`
- **engineId**: Engine ID（必填）

### AI_DEEPSEEK_QUERY
- **type**: `AI_DEEPSEEK_QUERY`
- **engineId**: Engine ID（必填）
- **payload.query**: 用户问题（必填）
- **payload.enableDeepThinking**: 是否启用深度思考（可选，默认false）
- **payload.enableWebSearch**: 是否启用联网搜索（可选，默认false）
- **payload.chatId**: DeepSeek会话ID（可选，用于继续对话）
- **payload.sessionId**: 业务会话ID（推荐，用于全链路追踪和数据库存储）
- **payload.aiType**: AI类型（推荐，如 `deepseek`，用于数据库存储）

---

## 超时时间

| 模式 | 最大等待时间 |
|------|-------------|
| 检查登录 | 30秒 |
| 扫码登录 | 5分钟 |
| 普通模式 | 5分钟 |
| 深度思考 | 22.5分钟 |
| 联网搜索 | 15分钟 |
| 深度+联网 | 30分钟 |

---

## 常见错误

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| `未登录，请先完成扫码登录` | 用户未登录 | 先执行 DEEPSEEK_SCAN_LOGIN |
| `问题内容不能为空` | query参数为空 | 检查payload.query |
| `登录超时，请重新尝试` | 5分钟内未扫码 | 重新发起扫码登录 |

---

## 调试技巧

### 1. 查看详细日志
```bash
tail -f logs/application.log | grep DeepSeek
```

### 2. 只看最终结果
```bash
# 在websocat中，TASK_RESULT表示最终结果
# TASK_PROGRESS表示进度更新
```

### 3. 保存响应
```bash
# 将所有响应保存到文件
websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}" | tee deepseek_test.log
```

---

## 快速测试脚本

```bash
#!/bin/bash

# 获取Token
TOKEN=$(curl -s -X POST http://localhost:8080/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

echo "Token: $TOKEN"

# 测试登录检测
echo '{"type":"AI_DEEPSEEK_CHECK_LOGIN","engineId":"engine-001"}' | \
websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}"

# 测试AI咨询
echo '{"type":"AI_DEEPSEEK_QUERY","engineId":"engine-001","payload":{"query":"你好","enableDeepThinking":false,"enableWebSearch":false}}' | \
websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}"
```

---

**注意事项：**
1. 所有消息类型都以 `AI_` 前缀开头，例如 `AI_DEEPSEEK_QUERY`
2. 前端生成 `sessionId` 用于业务会话追踪（与Admin的requestId区分）
3. 所有返回消息都携带 `sessionId` 和 `aiType` 字段，用于数据库存储
4. Engine返回的消息类型为：
   - `TASK_LOG`: 日志消息（payload.message, sessionId, aiType）
   - `TASK_SCREENSHOT`: 截图消息（payload.screenshotUrl, sessionId, aiType）
   - `TASK_PROGRESS`: 进度消息（payload.message, sessionId, aiType）
   - `TASK_RESULT`: 最终结果（payload.data包含answer, chatId, shareUrl, sessionId, aiType等）
5. 流式任务会收到多次 TASK_LOG/TASK_SCREENSHOT/TASK_PROGRESS，最后收到一次 TASK_RESULT
6. 单次任务只会收到一次 TASK_RESULT

---

## 返回消息格式说明

### TASK_LOG
```json
{
  "messageType": "TASK_LOG",
  "userId": "1",
  "payload": {
    "sessionId": "your-session-uuid",
    "aiType": "deepseek",
    "message": "正在发送问题...",
    "timestamp": 1767779926009
  }
}
```

### TASK_SCREENSHOT
```json
{
  "messageType": "TASK_SCREENSHOT",
  "userId": "1",
  "payload": {
    "sessionId": "your-session-uuid",
    "aiType": "deepseek",
    "screenshotUrl": "http://localhost:8080/profile/engine/1/...",
    "timestamp": 1767779934746
  }
}
```

### TASK_RESULT (成功)
```json
{
  "messageType": "TASK_RESULT",
  "userId": "1",
  "payload": {
    "sessionId": "your-session-uuid",
    "aiType": "deepseek",
    "success": true,
    "message": "DeepSeek回复完成",
    "data": {
      "query": "什么是人工智能？",
      "answer": "<html内容>...",
      "chatId": "2281bcec-ec30-4bc8-a0fb-51bce44344a2",
      "shareUrl": "https://chat.deepseek.com/a/chat/s/xxx",
      "conversationScreenshot": "http://localhost:8080/...",
      "mode": "normal",
      "elapsedTime": 21
    },
    "timestamp": 1767779950008
  }
}
```

---

## 字段说明

| 字段 | 说明 | 来源 |
|------|------|------|
| `sessionId` | 业务会话ID，用于全链路追踪和数据库存储 | 前端生成 |
| `aiType` | AI类型标识（如 `deepseek`），用于数据库存储 | 前端传递 |
| `requestId` | 系统级请求ID，用于Admin路由 | Admin自动生成 |
| `chatId` | DeepSeek平台的会话ID，用于继续对话 | Engine提取 |
