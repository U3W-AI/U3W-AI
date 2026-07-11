# 🤖 AIGC框架完整功能说明
> 更新日期：2026-07-11

> **作者**：15年经验Java开发工程师
> **更新时间**：2026-07-11
> **版本**：v3.0（性能优化版）
> **适用人群**：刚毕业的新手开发者 + 深入研究AIGC的技术专家

---

## 📖 文档导航

### ⭐⭐⭐ 快速必读（10分钟上手）
- [1. 框架核心概念](#1-框架核心概念)
- [2. 消息协议规范](#2-消息协议规范)
- [3. 关键代码位置](#3-关键代码位置)
- [4. 快速开始](#4-快速开始)

### ⭐⭐ 核心流程（深入理解）
- [5. 完整业务流程](#5-完整业务流程)
- [6. 数据存储机制](#6-数据存储机制)
- [7. 性能优化方案](#7-性能优化方案)

### ⭐ 深入研究（架构设计）
- [8. 代码隔离性设计](#8-代码隔离性设计)
- [9. 扩展新AI平台](#9-扩展新ai平台)
- [10. 上下文对话机制](#10-上下文对话机制)
- [11. 故障排查指南](#11-故障排查指南)

---

## 源码校准说明（2026-07-10）

1. 当前前端入口为 `FBSir-ui/src/views/business/content/aigc/index.vue`，接口封装位于 `FBSir-ui/src/api/aigc/` 与 `FBSir-ui/src/api/business/content/aigc/output.js`。
2. 当前 Engine 的 AI 控制器为 `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/ai/DeepSeekController.java`，已实现的 AI 查询能力是 `AI_DEEPSEEK_QUERY`；其他平台名称、控制器和 SQL 片段仅是扩展模板，不代表已交付能力。
3. 新增平台必须同时实现 Engine 能力、前端触发入口、消息存储处理及自动化测试；不要把本文的示例文件名当作当前已存在文件。

---

## 1. 框架核心概念

### 1.1 什么是AIGC框架？

AIGC框架是基于WebSocket的**AI内容生成协同框架**，实现了前端、Admin服务、Engine服务和AI平台之间的完整通信链路。

```
前端界面 ⟷ Admin服务 ⟷ Engine服务 ⟷ AI平台（当前为 DeepSeek）
   │          │              │              │
   └──显示───┴──存储──────┴──调用────────┴──生成内容
```

**关键特点**：
- ✅ **先存后发架构**：Admin预先保存请求，确保数据不丢失
- ✅ **实时存储机制**：Engine每条消息立即存储到数据库
- ✅ **批量优化**：日志和截图攒批更新，减少90%数据库压力
- ✅ **任务隔离**：每个AI独立处理，互不影响

### 1.2 核心参数说明

| 参数名 | 生成位置 | 作用 | 示例值                                    |
|--------|---------|------|----------------------------------------|
| **sessionId** | 前端生成UUID | 业务会话唯一标识，数据库主键 | `550e8400-e29b-41d4-a716-446655440000` |
| **chatId** | 前端生成UUID | 多轮对话分组ID，用于上下文复用 | 与sessionId类似                           |
| **aiType** | 前端指定 | AI类型标识，用于存储和显示区分 | 当前链路为 `"deepseek"`                |
| **userId** | Admin注入 | 用户身份标识 | `"103"`                                |
| **payload** | 前端构造 | 请求参数载荷，Admin透传不解析 | `{query, enableDeepThinking, ...}`     |

**必传参数**（缺一不可）：
1. ✅ `sessionId` - 没有则无法存储
2. ✅ `aiType` - 没有则无法区分AI
3. ✅ `engineId` - 没有则无法路由到正确的Engine

**注意**：`userId` 由Admin通过Token自动解析注入，前端无需传递

---

## 2. 消息协议规范

### 2.1 消息类型命名规范 ⚠️ 重要

#### 🔥 核心隔离原则：AI业务 vs 非AI业务

**非AI业务消息**（❌ 不含 `AI_` 前缀）：
```
DEEPSEEK_CHECK_LOGIN    - 登录状态检测（非AI业务）
DEEPSEEK_SCAN_LOGIN     - 扫码登录（非AI业务）
TASK_LOG                - 通用任务日志（非AI业务）
TASK_SCREENSHOT         - 通用任务截图（非AI业务）
TASK_RESULT             - 通用任务结果（非AI业务）
```

**AI业务消息**（✅ 必含 `AI_` 前缀）：
```
AI_DEEPSEEK_QUERY       - DeepSeek AI咨询（AI业务）
AI_TASK_LOG             - AI进度日志（Admin自动追加到data.progressLogs）
AI_TASK_SCREENSHOT      - AI执行截图（Admin自动追加到data.screenshots）
AI_TASK_RESULT          - AI最终结果（Admin合并保存到data字段）
AI_TASK_ERROR           - AI错误信息（Admin记录错误）
```

#### 🎯 消息隔离保证

| 模块 | 只处理 | 不处理 | 验证方式 |
|------|--------|--------|---------|
| **AIGC前端** (`aigc/index.vue`) | `AI_TASK_*` | `TASK_*` | 搜索代码无`TASK_LOG`等 |
| **登录前端** (`loginManager/index.vue`) | `TASK_*` | `AI_TASK_*` | 搜索代码无`AI_TASK_LOG`等 |
| **Admin存储** (`EngineMessageRouter`) | `AI_TASK_*` | `TASK_*` | 只存储AI消息到聊天历史 |

### 2.2 消息流向示意图

```
┌─────────┐                    ┌─────────┐                    ┌─────────┐
│  前端    │ ─── AI请求 ─────→ │  Admin  │ ─── 透传 ────────→│ Engine  │
│         │                    │         │                    │         │
│ 发送请求 │                    │ 预保存  │                    │ 调用AI  │
│ 显示结果 │                    │ 存储    │                    │ 返回    │
└─────────┘ ←── 转发结果 ───── └─────────┘ ←── AI_TASK_* ─── └─────────┘
```

### 2.3 标准请求示例

```javascript
// 前端发送（userId由Admin通过Token自动注入，前端无需传递）
{
  "type": "AI_DEEPSEEK_QUERY",
  "engineId": "engine-001",                              // ✅ 必传，从用户配置获取
  "chatId": "chat-001",                                  // ✅ 顶层chatId（多轮对话分组）
  "payload": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000", // ✅ 必传，本次请求唯一ID
    "chatId": "chat-001",                                // ✅ payload中备份
    "aiType": "deepseek",                                // ✅ 必传，AI类型标识
    "query": "什么是人工智能？",
    "enableDeepThinking": false,
    "enableWebSearch": false
  }
}
```

```java
// Engine响应（userId由Admin注入后透传）
{
  "type": "AI_TASK_LOG",
  "userId": "103",
  "payload": {
    "sessionId": "550e8400-e29b-41d4-a716-446655440000",  // ✅ 回传
    "aiType": "deepseek",                                  // ✅ 回传
    "message": "正在打开DeepSeek...",
    "timestamp": 1704700800000
  }
}
```

---

## 3. 关键代码位置

### 3.1 Engine端（AI调用层）

| 文件路径 | 作用 | 关键方法 |
|---------|------|---------|
| `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/ai/DeepSeekController.java` | DeepSeek AI控制器 | `handleAiQuery()` - AI咨询入口 |
| `engine/capability/base/StreamTaskHelper.java` | 流式任务辅助类 | `startAiStreamTask()` - 启动AI任务<br>`startStreamTask()` - 启动通用任务 |
| `engine/websocket/message/MessageType.java` | 消息类型定义 | `AI_TASK_*` - AI消息枚举 |

**StreamTaskHelper使用示例**：
```java
// AI业务使用 startAiStreamTask（自动发送 AI_TASK_* 消息）
StreamTask task = startAiStreamTask(userId, sessionId, "deepseek", 6000);
task.sendLog("正在连接DeepSeek...");      // → AI_TASK_LOG
task.sendScreenshot(screenshotUrl);        // → AI_TASK_SCREENSHOT
task.sendSuccess("完成", resultData);      // → AI_TASK_RESULT
task.sendError("错误信息");                // → AI_TASK_ERROR

// 登录等通用业务使用 startStreamTask（发送 TASK_* 消息）
StreamTask task = startStreamTask(userId, sessionId, 2000);
task.sendLog("页面加载中...");             // → TASK_LOG
```

### 3.2 Admin端（存储层）

| 文件路径 | 作用 | 关键方法 |
|---------|------|---------|
| `business/websocket/server/EngineMessageRouter.java` | Engine消息路由器 | `processRealtimeStorage()` - 实时存储入口<br>`appendProgressLog()` - 日志追加<br>`appendScreenshot()` - 截图追加 |
| `business/websocket/server/ClientMessageRouter.java` | 客户端消息路由器 | AI请求预保存（150-197行） |
| `business/aigc/service/AigcBatchUpdateService.java` | 批量更新服务 | `addProgressLog()` - 日志入队<br>`scheduledFlushAll()` - 定时刷新 |
| `business/aigc/service/IAigcService.java` | AIGC数据服务 | `saveChatData()` - 保存数据<br>`getChatBySessionId()` - 查询历史 |
| `business/aigc/mapper/AigcMapper.xml` | MyBatis映射 | SQL语句定义 |

**EngineMessageRouter处理流程**：
```java
// 1. 收到Engine消息
processRealtimeStorage(message, session) {
    // 2. 跳过登录类消息
    if (type.contains("LOGIN")) return;
    
    // 3. 只处理AI_TASK_*消息
    if (!MessageType.isAiTaskResponse(type)) return;
    
    // 4. 根据类型分发
    if ("AI_TASK_LOG".equals(type)) {
        appendProgressLog(...);  // → 批量队列
    } else if ("AI_TASK_SCREENSHOT".equals(type)) {
        appendScreenshot(...);   // → 批量队列
    } else if ("AI_TASK_RESULT".equals(type)) {
        saveAiResult(...);       // → 直接保存
    }
}
```

### 3.3 前端（展示层）

| 文件路径 | 作用 | 关键方法 |
|---------|------|---------|
| `FBSir-ui/src/views/business/content/aigc/index.vue` | AIGC主页面 | WebSocket 消息处理、请求发送与历史加载 |
| `FBSir-ui/src/api/aigc/assistant.js` | API封装 | `getChatHistory()` - 获取历史 |

---

## 4. 快速开始

### 4.1 新手开发者：快速添加新AI

**场景**：已有DeepSeek，想添加通义千问

#### 步骤1：前端配置（5分钟）

```javascript
// 扩展示例：当前源码未提供统一 ai.config.js；请在现有 AIGC 页面中补充平台入口
export const AI_CONFIGS = [
  {
    id: 'deepseek',
    name: 'DeepSeek',
    enabled: true
  },
  {
    id: 'tongyi',        // ✅ 新增
    name: '通义千问',     // ✅ 新增
    enabled: true        // ✅ 新增
  }
]
```

#### 步骤2：Engine添加处理器（10分钟）

```java
// 扩展示例：需要新建 FBSir-engine/.../controller/ai/TongyiController.java
@Controller
public class TongyiController extends StreamTaskHelper {
    
    @StreamCapability(type = "AI_TONGYI_QUERY")
    public void handleQuery(EngineMessage message) {
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);
        
        // 🤖 启动AI流式任务（自动发送AI_TASK_*消息）
        StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
        
        task.sendLog("正在连接通义千问...");
        String answer = tongyiUtil.query(query);
        task.sendSuccess("完成", Map.of("answer", answer));
    }
}
```

**完成！** Admin和前端AIGC模块无需任何修改！

---

### 4.2 框架改造者：理解核心架构

#### 关键设计点

1. **消息类型隔离**
   - `AI_TASK_*` → AIGC业务专用
   - `TASK_*` → 通用业务专用
   - 通过前缀区分，避免冲突

2. **StreamTaskHelper双模式**
   ```java
   startAiStreamTask()     → 发送 AI_TASK_* 消息
   startStreamTask()       → 发送 TASK_* 消息
   ```

3. **Admin存储过滤**
   ```java
   if (!type.startsWith("AI_TASK_")) return;  // 只存储AI消息
   ```

4. **前端消息隔离**
   - `aigc/index.vue` → 只处理 `AI_TASK_*`
   - `loginManager/index.vue` → 只处理 `TASK_*`

---

### 4.3 启动服务

```bash
# 1. 启动Engine（AI调用服务）
cd FBSir-engine
mvn spring-boot:run

# 2. 启动Admin（存储服务）
cd FBSir-business
mvn spring-boot:run

# 3. 启动前端
cd FBSir-ui
npm run dev
```

### 4.4 配置用户主机ID

1. 登录系统：http://localhost:8080
2. 进入 **个人中心** → **主机配置**
3. 设置主机ID：`engine-001`
4. 点击"检测连接"验证Engine在线

### 4.5 测试AI咨询

1. 进入 **内容管理** → **AI助手**
2. 输入问题："什么是人工智能？"
3. 点击"发送"
4. 观察控制台日志：
   ```
   [AIGC存储] 提取参数 - sessionId: xxx, aiType: deepseek
   [批量队列] 添加日志 - 会话: xxx, 当前队列大小: 1
   [批量刷新] 完成 - 成功: 1, 失败: 0
   ```

---

## 5. 完整业务流程

### 5.1 阶段1：前端发起请求

```javascript
// 前端操作（FBSir-ui/src/views/business/content/aigc/index.vue）
const sendPrompt = () => {
  const sessionId = uuidv4()  // 生成业务会话ID
  
  sendWebSocketMessage('AI_DEEPSEEK_QUERY', {
    sessionId: sessionId,
    chatId: currentChatId.value,  // 多轮对话分组ID
    aiType: 'deepseek',
    query: promptInput.value,
    enableDeepThinking: false,
    enableWebSearch: false
  })
}
```

### 5.2 阶段2：Admin预保存

```java
// Admin操作（business/websocket/server/ClientMessageRouter.java:150-197）
if (type.startsWith("AI_")) {
    AiRequest aiRequest = new AiRequest();
    aiRequest.setSessionId(sessionId);
    aiRequest.setChatId(chatId);
    aiRequest.setUserId(userId);
    aiRequest.setUserPrompt(userPrompt);
    aiRequest.setAiType(aiType);
    aiRequest.setPayload(payloadMap);  // 完整payload透传
    
    // 🔥 预保存到数据库（先存后发架构）
    aigcService.saveInitialRequest(aiRequest);
    
    // 转发给Engine
    engineSession.sendMessage(message);
}
```

**存储内容**：
```sql
INSERT INTO wc_chat_history (id, user_id, userPrompt, chat_id, data)
VALUES ('session-001', '103', '什么是人工智能？', 'chat-001', '{}');
```

### 5.3 阶段3：Engine调用AI

```java
// Engine操作（FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/ai/DeepSeekController.java）
@StreamCapability(type = "AI_DEEPSEEK_QUERY")
public void handleAiQuery(EngineMessage message) {
    String sessionId = extractSessionId(message);
    String aiType = extractAiType(message);
    
    // 🤖 启动AI流式任务（自动发送AI_TASK_*消息）
    StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
    
    try {
        task.sendLog("正在打开DeepSeek...");  // → AI_TASK_LOG
        
        // 调用DeepSeek API
        String aiResponse = deepSeekUtil.query(page, query);
        
        task.sendScreenshot(screenshotUrl);    // → AI_TASK_SCREENSHOT
        
        Map<String, Object> result = new HashMap<>();
        result.put("answer", aiResponse);
        result.put("chatId", deepseekChatId);
        
        task.sendSuccess("完成", result);      // → AI_TASK_RESULT
        
    } catch (Exception e) {
        task.sendError("AI调用失败: " + e.getMessage());  // → AI_TASK_ERROR
    }
}
```

### 5.4 阶段4：Admin批量存储（性能优化）

```java
// Admin操作（business/aigc/service/AigcBatchUpdateService.java）

// 收到AI_TASK_LOG消息
processRealtimeStorage(message) {
    appendProgressLog(...);  // 加入批量队列
}

// 收到AI_TASK_SCREENSHOT消息
processRealtimeStorage(message) {
    appendScreenshot(...);   // 加入批量队列
}

// 定时批量刷新（每500ms或积累10条）
@Scheduled(fixedDelay = 500)
public void scheduledFlushAll() {
    // 🔥 一次性合并所有日志和截图
    Map<String, Object> existingChat = aigcService.getChatBySessionId(sessionId);
    
    // 批量追加日志
    List<Map<String, Object>> logs = dataMap.get("progressLogs");
    logs.addAll(batch.progressLogs);  // 10条日志一次性添加
    
    // 批量追加截图
    List<String> screenshots = dataMap.get("screenshots");
    screenshots.addAll(batch.screenshots);  // 5张截图一次性添加
    
    // 🔥 一次UPDATE更新数据库
    aigcService.updateChatData(existingChat);
    
    // 性能提升：15-30次UPDATE → 1-2次UPDATE（减少90%）
}
```

### 5.5 阶段5：Admin保存最终结果

```java
// 收到AI_TASK_RESULT消息
saveAiResult(userId, sessionId, chatId, aiType, payload) {
    // 读取现有记录
    Map<String, Object> existingChat = aigcService.getChatBySessionId(sessionId);
    
    // 🔥 合并progressLogs和screenshots（不覆盖）
    Map<String, Object> mergedData = new HashMap<>();
    if (existingDataMap.get("progressLogs") != null) {
        mergedData.put("progressLogs", existingDataMap.get("progressLogs"));
    }
    if (existingDataMap.get("screenshots") != null) {
        mergedData.put("screenshots", existingDataMap.get("screenshots"));
    }
    
    // 添加最终结果
    mergedData.put("answer", payload.get("answer"));
    mergedData.put("shareUrl", payload.get("shareUrl"));
    mergedData.put("chatId", payload.get("chatId"));  // DeepSeek会话ID
    
    // 保存
    chatData.put("data", JSON.toJSONString(mergedData));
    chatData.put("deepseekChatId", payload.get("chatId"));  // 用于上下文复用
    aigcService.saveChatData(chatData);
}
```

### 5.6 阶段6：前端接收显示

```javascript
// 前端操作（FBSir-ui/src/views/business/content/aigc/index.vue）
const handleWebSocketMessage = (data) => {
  const message = JSON.parse(data)
  const messageType = message.type
  
  // 处理AI_TASK_LOG
  if (messageType === 'AI_TASK_LOG') {
    addProgressLog(payload.message, payload.aiType)
  }
  
  // 处理AI_TASK_SCREENSHOT
  if (messageType === 'AI_TASK_SCREENSHOT') {
    screenshots.value.push(payload.screenshotUrl)
  }
  
  // 处理AI_TASK_RESULT
  if (messageType === 'AI_TASK_RESULT' && payload.data.answer) {
    results.value.push({
      aiName: 'DeepSeek',
      content: payload.data.answer,
      shareUrl: payload.data.shareUrl
    })
    ElMessage.success('DeepSeek回复完成')
  }
}
```

---

## 6. 数据存储机制

### 6.1 数据库表结构

```sql
CREATE TABLE `wc_chat_history` (
  `id` varchar(255) NOT NULL COMMENT '主键ID（sessionId）',
  `user_id` varchar(10) COMMENT '用户ID',
  `userPrompt` longtext COMMENT '用户提问',
  `data` longtext COMMENT '完整数据（JSON格式）',
  `chat_id` varchar(36) COMMENT '内部chatID（用于多轮对话）',
  `deepseek_chat_id` varchar(100) COMMENT 'DeepSeek会话ID（用于上下文复用）',
  `yb_chat_id` varchar(100) COMMENT '元宝会话ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_user_chat`(`user_id`, `chat_id`),
  INDEX `idx_deepseek`(`deepseek_chat_id`)
) ENGINE=InnoDB;
```

### 6.2 存储数据示例

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "103",
  "userPrompt": "什么是人工智能？",
  "chatId": "chat-001",
  "deepseekChatId": "ds_chat_123456",
  "data": {
    "progressLogs": [
      {
        "content": "正在打开DeepSeek...",
        "timestamp": 1704700800000,
        "aiType": "deepseek"
      },
      {
        "content": "登录验证通过，准备发送问题...",
        "timestamp": 1704700801000,
        "aiType": "deepseek"
      },
      {
        "content": "AI正在思考中...",
        "timestamp": 1704700806000,
        "aiType": "deepseek"
      }
    ],
    "screenshots": [
      "http://localhost:8080/profile/upload/engine/screenshot_1.png",
      "http://localhost:8080/profile/upload/engine/screenshot_2.png"
    ],
    "query": "什么是人工智能？",
    "answer": "人工智能（AI）是计算机科学的一个分支...",
    "shareUrl": "https://chat.deepseek.com/a/chat/s/ds_chat_123456",
    "elapsedTime": 15,
    "mode": "normal"
  }
}
```

### 6.3 存储时机总结

| 阶段 | 触发时机 | 存储内容 | 数据库操作 |
|------|---------|---------|-----------|
| **阶段1** | 前端发送请求 | 初始请求 | `INSERT` 初始记录 |
| **阶段2** | Engine发送日志 | progressLogs | 批量队列攒批 |
| **阶段3** | Engine发送截图 | screenshots | 批量队列攒批 |
| **阶段4** | 定时500ms | 批量刷新 | `UPDATE` 合并数据 |
| **阶段5** | Engine发送结果 | 最终结果 | `UPDATE` 完整数据 |

---

## 7. 性能优化方案

### 7.1 批量攒批优化（v3.0新增）

**优化前的问题**：
```
1次AI咨询 = 10条日志 × 1次UPDATE + 5张截图 × 1次UPDATE
         = 15次数据库往返 + 15次行锁竞争
```

**优化后的方案**：
```java
// AigcBatchUpdateService.java
private final Map<String, SessionUpdateBatch> batchQueue = new ConcurrentHashMap<>();

// 日志和截图加入队列
public void addProgressLog(...) {
    batch.addProgressLog(logEntry);
    
    // 达到阈值立即刷新
    if (batch.getTotalSize() >= 10) {
        flushSession(sessionId);
    }
}

// 定时批量刷新（每500ms）
@Scheduled(fixedDelay = 500)
public void scheduledFlushAll() {
    // 一次性合并所有日志和截图
    logs.addAll(batch.progressLogs);       // 10条日志
    screenshots.addAll(batch.screenshots); // 5张截图
    
    // 一次UPDATE
    aigcService.updateChatData(existingChat);
}
```

**性能提升**：
- 数据库UPDATE：15-30次 → 1-2次（**减少90%**）
- 响应延迟：最多500ms（用户无感知）
- 行锁竞争：大幅降低

### 7.2 异常处理增强

```java
// 指数退避重试机制
private void flushBatch(SessionUpdateBatch batch) {
    int retryCount = 0;
    
    while (retryCount < 3) {
        try {
            doFlushBatch(batch);
            return;  // 成功返回
        } catch (Exception e) {
            retryCount++;
            if (retryCount < 3) {
                long backoffMs = 100L * (1 << (retryCount - 1));  // 100ms, 200ms, 400ms
                Thread.sleep(backoffMs);
            }
        }
    }
    
    // 🔥 重试3次失败后告警（不静默）
    log.error("[批量刷新] 重试3次后仍失败 - 会话: {}, 错误: {}", 
        batch.sessionId, e.getMessage(), e);
    
    // TODO: 发送告警（钉钉/企微/邮件）
    // alertService.sendAlert("AIGC批量更新失败", batch.sessionId, e);
}
```

---

## 8. 代码隔离性设计

### 8.1 Engine端隔离

**当前目录结构**：
```
FBSir-engine/src/main/java/com/wx/fbsir/engine/
├── controller/ai/DeepSeekController.java
├── controller/yuanqi/YuanQiNodeController.java
├── capability/base/StreamTaskHelper.java
└── utils/ai/DeepSeekUtil.java
```

**StreamTaskHelper双模式**：
```java
// AI业务专用
protected StreamTask startAiStreamTask(String userId, String sessionId, String aiType, long intervalMillis) {
    return new StreamTask(userId, sessionId, aiType, intervalMillis, true);  // isAiTask=true
}

// 通用业务专用
protected StreamTask startStreamTask(String userId, String sessionId, long intervalMillis) {
    return new StreamTask(userId, sessionId, "unknown", intervalMillis, false);  // isAiTask=false
}

// StreamTask根据isAiTask自动选择消息类型
public void sendLog(String message) {
    String messageType = isAiTask ? 
        MessageType.AI_TASK_LOG.getCode() :    // AI业务 → AI_TASK_LOG
        MessageType.TASK_LOG.getCode();        // 通用业务 → TASK_LOG
    // ...发送消息
}
```

### 8.2 Admin端隔离

**EngineMessageRouter隔离**：
```java
// =========================================================================
// 🤖 AIGC消息处理模块（174-268行）
// 说明：所有AI相关消息的存储和处理逻辑，与通用WebSocket消息完全隔离
// 支持消息类型：AI_TASK_LOG、AI_TASK_SCREENSHOT、AI_TASK_RESULT、AI_TASK_ERROR
// =========================================================================

private void processRealtimeStorage(EngineMessage message, EngineSession session) {
    String type = message.getType();
    
    // 🎯 跳过登录类消息
    if (type != null && (type.contains("LOGIN") || type.contains("CHECK"))) {
        return;
    }
    
    // 🤖 只处理AIGC消息（AI_TASK_*格式）
    if (!MessageType.isAiTaskResponse(type)) {
        return;  // 非AI任务消息，跳过存储
    }
    
    // 根据消息类型分发
    if ("AI_TASK_LOG".equals(type)) {
        appendProgressLog(...);  // → 批量队列
    } // ...
}
```

**业务存储层**：
```
FBSir-business/src/main/java/com/wx/fbsir/business/aigc/
├── controller/AigcController.java
└── service/
```

### 8.3 前端隔离

**当前目录结构**：
```
FBSir-ui/src/views/business/content/aigc/
└── index.vue          ← AIGC 主页面
```

**消息处理隔离**：
```javascript
// FBSir-ui/src/views/business/content/aigc/index.vue

// ==========================================================================
// 🤖 AIGC消息处理（仅支持AI_TASK_*格式）
// ==========================================================================

if (messageType === 'AI_TASK_LOG') {
  // AI日志处理
}
if (messageType === 'AI_TASK_SCREENSHOT') {
  // AI截图处理
}
if (messageType === 'AI_TASK_RESULT') {
  // AI结果处理
}

// ==========================================================================
// 🤖 AIGC消息处理结束
// ==========================================================================
```

---

## 9. 扩展新AI平台

### 9.1 扩展步骤（仅需Engine端）

```java
// 1. 扩展示例：创建新的 AI 控制器（当前源码未包含 TongyiController）
@Controller
public class TongyiController extends StreamTaskHelper {
    
    @StreamCapability(
        type = "AI_TONGYI_QUERY",
        description = "通义千问AI咨询"
    )
    public void handleAiQuery(EngineMessage message) {
        String sessionId = extractSessionId(message);
        String aiType = extractAiType(message);  // "tongyi"
        String query = message.getPayloadValue("query");
        
        // 🤖 启动AI流式任务
        StreamTask task = startAiStreamTask(userId, sessionId, aiType, 6000);
        
        try {
            task.sendLog("正在连接通义千问...");
            
            // 调用通义千问API
            String aiResponse = tongyiUtil.query(query);
            
            task.sendScreenshot(screenshotUrl);
            
            Map<String, Object> result = new HashMap<>();
            result.put("answer", aiResponse);
            result.put("chatId", tongyiChatId);
            
            task.sendSuccess("完成", result);
            
        } catch (Exception e) {
            task.sendError("通义千问调用失败: " + e.getMessage());
        }
    }
}
```

```sql
-- 2. 数据库添加会话ID字段（可选）
ALTER TABLE wc_chat_history 
ADD COLUMN tongyi_chat_id VARCHAR(100) COMMENT '通义千问会话ID';
```

```javascript
// 3. 前端扩展示例（FBSir-ui/src/views/business/content/aigc/index.vue）
const enabledAIs = ref([
  {
    id: 'deepseek',
    name: 'DeepSeek',
    enabled: true
  },
  {
    id: 'tongyi',      // 新增
    name: '通义千问',   // 新增
    enabled: false
  }
])
```

**完成！Admin端无需任何修改！**

---

## 10. 上下文对话机制

### 10.1 核心概念

| 概念 | 作用 | 生成时机 | 示例 |
|------|------|---------|------|
| **chatId** | 多轮对话分组ID | 前端首次对话生成，后续复用 | `chat-001` |
| **sessionId** | 单轮对话唯一ID | 前端每次发送生成 | `session-001`, `session-002` |
| **deepseekChatId** | AI平台会话ID | AI平台返回，用于上下文复用 | `ds_chat_123456` |

### 10.2 3轮连续对话示例

```
第1轮：
前端 → chatId: "chat-001", sessionId: "session-001", query: "介绍Spring Boot"
     → Admin保存：sessionId="session-001", chatId="chat-001"
     → Engine返回：deepseekChatId="ds_123"
     → Admin更新：deepseekChatId="ds_123"

第2轮：
前端 → chatId: "chat-001", sessionId: "session-002", query: "自动配置原理"
     → Admin查询chat-001历史，获取deepseekChatId="ds_123"
     → 🔥 传递deepseekChatId给Engine，实现上下文连续
     → Engine使用ds_123继续对话
     → Admin保存：sessionId="session-002", chatId="chat-001", deepseekChatId="ds_123"

第3轮：
前端 → chatId: "chat-001", sessionId: "session-003", query: "示例代码"
     → 🔥 继续复用deepseekChatId="ds_123"
     → Admin保存：sessionId="session-003", chatId="chat-001", deepseekChatId="ds_123"
```

**数据库最终状态**：
```
wc_chat_history:
+-------------+----------+-------+-----------------+
| id          | chat_id  | user  | deepseek_chat_id|
+-------------+----------+-------+-----------------+
| session-001 | chat-001 | user1 | ds_123          |
| session-002 | chat-001 | user1 | ds_123          |
| session-003 | chat-001 | user1 | ds_123          |
+-------------+----------+-------+-----------------+
```

### 10.3 实现代码（Admin端）

```java
// ClientMessageRouter中预保存时传递chatId
aiRequest.setChatId(chatId);  // 前端传递的多轮对话分组ID

// Engine处理时获取已有的AI会话ID
String existingAiChatId = getExistingAiChatId(chatId, aiType);
if (existingAiChatId != null) {
    // 传递给AI平台，实现上下文连续
    aiRequest.put("aiChatId", existingAiChatId);
}
```

---

## 11. 故障排查指南

### 11.1 常见问题

**问题1：AI咨询无响应**
```bash
# 检查用户主机ID配置
SELECT user_id, host_id FROM sys_user WHERE user_id = 103;

# 检查Engine在线状态
tail -f logs/sys-info.log | grep "Engine.*online"

# 检查WebSocket连接
浏览器控制台 → Network → WS → 查看连接状态
```

**问题2：数据存储失败**
```bash
# 检查批量更新服务日志
tail -f logs/sys-info.log | grep "批量刷新"

# 检查数据库连接
SHOW PROCESSLIST;

# 检查表结构
DESCRIBE wc_chat_history;
```

**问题3：历史记录不显示日志**
```sql
-- 检查data字段是否包含progressLogs
SELECT 
    id,
    JSON_EXTRACT(data, '$.progressLogs') as logs,
    JSON_EXTRACT(data, '$.screenshots') as screenshots
FROM wc_chat_history 
WHERE user_id = '103'
ORDER BY create_time DESC
LIMIT 1;
```

### 11.2 关键日志位置

```bash
# Admin服务日志
tail -f logs/sys-info.log

# Engine服务日志
tail -f logs/engine.log

# 批量更新服务日志（重点关注）
tail -f logs/sys-info.log | grep "\[批量"

# WebSocket消息日志
tail -f logs/sys-info.log | grep "\[AIGC"
```

### 11.3 性能监控

```sql
-- 查询最近的批量更新统计
SELECT 
    DATE_FORMAT(create_time, '%Y-%m-%d %H:%i') as time,
    COUNT(*) as update_count
FROM wc_chat_history 
WHERE create_time > DATE_SUB(NOW(), INTERVAL 1 HOUR)
GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d %H:%i')
ORDER BY time DESC;

-- 查询数据库慢查询
SHOW VARIABLES LIKE 'slow_query%';
```

---

## 📞 技术支持

### 架构设计原则总结

作为15年经验的Java开发工程师，本框架遵循以下设计原则：

1. **职责分离**：Engine调用、Admin存储、前端展示各司其职
2. **性能优先**：批量攒批减少90%数据库压力
3. **容错设计**：3次重试+指数退避+告警机制
4. **扩展友好**：新增AI仅需Engine端添加处理器
5. **代码隔离**：AIGC代码完全独立，易于维护

### 文档更新记录

- **v3.0** (2026-01-09)：性能优化版，批量攒批+异常增强
- **v2.0** (2026-01-08)：消息协议规范化，AI_TASK_*格式
- **v1.0** (2025-12-25)：初始版本，AIGC框架搭建

---

**Happy Coding! 🚀**
