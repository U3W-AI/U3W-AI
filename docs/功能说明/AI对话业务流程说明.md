# AI对话业务流程说明

## 📋 概述

本文档详细说明了AI对话业务的完整流程，包括数据存储策略、任务隔离机制和状态管理。

**最新更新**（2026-01-08）：
- ✅ 规范Engine消息类型，区分登录和AI咨询业务
- ✅ 实现进度日志和截图的追加式存储
- ✅ 增强实时存储机制，确保数据完整性

---

## 🎯 核心设计原则

### 1. **实时存储，避免数据丢失**
- Admin收到Engine消息时立即保存到数据库
- 不依赖前端手动保存
- 即使WebSocket断开，数据也已安全存储
- **进度日志和截图实时追加**，用户可查看历史完整执行过程

### 2. **任务独立，互不影响**
- 每个AI任务独立处理和存储
- 某个AI失败不影响其他AI继续执行
- 每个AI的结果单独保存到数据库

### 3. **业务隔离，清晰边界**
- **登录类消息**不含`AI_`前缀（DEEPSEEK_CHECK_LOGIN、DEEPSEEK_SCAN_LOGIN）
- **AI咨询消息**保留`AI_`前缀（AI_DEEPSEEK_QUERY）
- **通用任务消息**用于数据存储（TASK_LOG、TASK_SCREENSHOT、TASK_RESULT）
- 保证业务逻辑的独立性

---

## 🔄 完整业务流程

### 阶段1：前端发起请求

```
前端 → WebSocket → Admin
```

**前端操作**：
```javascript
// 发送AI请求（不再手动保存数据库）
sendWebSocketMessage('AI_DEEPSEEK_QUERY', {
  query: promptInput.value,
  chatId: currentChatId.value,
  sessionId: sessionId,
  aiType: 'deepseek'
})
```

**关键点**：
- ✅ 前端只负责发送请求和显示结果
- ✅ 不再调用`saveChatData`等数据库API
- ✅ 数据存储完全由后端自动处理

---

### 阶段2：Admin处理请求

```
Admin接收请求 → 存储初始请求 → 转发给Engine
```

**Admin操作**（`AigcController.handleAiRequest`）：

```java
// 1. 生成请求ID（全链路跟踪）
String requestId = UUID.randomUUID().toString();

// 2. 设置用户信息
aiRequest.setUserId(userId.toString());
aiRequest.setUsername(username);

// 3. 获取用户主机ID
String hostId = aigcService.getUserHostId(userId);

// 4. 🔥 保存初始请求到数据库
aigcService.saveInitialRequest(aiRequest);

// 5. 转发到Engine处理
// engineSessionManager.sendToEngine(hostId, aiRequest);
```

**存储内容**：
- sessionId（会话唯一标识）
- userId（用户ID）
- userPrompt（用户问题）
- aiType（AI类型：deepseek、yuanbao等）
- requestTime（请求时间）

---

### 阶段3：Engine处理并返回消息

```
Engine处理 → 返回进度/截图/结果 → Admin接收
```

**Engine消息类型规范**（已更新）：

### 请求消息类型

| 消息类型 | 说明 | 是否包含AI_前缀 |
|---------|------|----------------|
| `DEEPSEEK_CHECK_LOGIN` | 登录状态检测 | ❌ 否（非AI业务） |
| `DEEPSEEK_SCAN_LOGIN` | 扫码登录 | ❌ 否（非AI业务） |
| `AI_DEEPSEEK_QUERY` | AI咨询 | ✅ 是（AI业务） |

### 响应消息类型（通用）

| 消息类型 | 说明 | 存储方式 |
|---------|------|---------|
| `TASK_LOG` | 进度日志 | 实时追加到data.progressLogs数组 |
| `TASK_SCREENSHOT` | 执行截图 | 实时追加到data.screenshots数组 |
| `TASK_RESULT` | 最终结果 | 保存完整结果到data字段 |

---

### 阶段4：Admin实时存储

```
Admin收到消息 → 判断类型 → 实时存储 → 转发给前端
```

**EngineMessageRouter处理逻辑**（已更新）：

```java
private void processRealtimeStorage(EngineMessage message, EngineSession session) {
    String type = message.getType();
    
    // 🎯 跳过登录类消息（不含AI_前缀）
    if (type != null && (type.contains("LOGIN") || type.contains("CHECK"))) {
        log.debug("[实时存储] 跳过登录类消息 - 类型: {}", type);
        return;
    }
    
    // 🎯 只处理通用任务消息（TASK_LOG、TASK_SCREENSHOT、TASK_RESULT）
    if (type == null || (!type.equals("TASK_LOG") && !type.equals("TASK_SCREENSHOT") && !type.equals("TASK_RESULT"))) {
        return;
    }
    
    // 根据消息类型处理存储
    if ("TASK_LOG".equals(type)) {
        // 进度日志消息 - 追加到数据库
        appendProgressLog(userId, sessionId, aiType, payload);
        
    } else if ("TASK_SCREENSHOT".equals(type)) {
        // 截图消息 - 追加到数据库
        appendScreenshot(userId, sessionId, aiType, payload);
        
    } else if ("TASK_RESULT".equals(type)) {
        // 最终结果 - 保存完整结果到聊天历史
        saveAiResult(userId, sessionId, aiType, userPrompt, payload, type);
    }
}
```

**关键改进**：
- ✅ 明确区分登录消息和AI业务消息
- ✅ 使用通用消息类型（TASK_*）而非AI特定类型
- ✅ 进度日志和截图**追加式存储**，支持历史查询

**存储时机**（已优化）：

1. **进度日志**（`TASK_LOG`）✅ **实时追加**
   ```java
   // 获取现有聊天记录
   Map<String, Object> existingChat = aigcService.getChatBySessionId(sessionId);
   
   // 构建日志对象
   Map<String, Object> logEntry = new HashMap<>();
   logEntry.put("content", logMessage);
   logEntry.put("timestamp", payload.get("timestamp"));
   logEntry.put("aiType", aiType);
   
   // 追加到progressLogs数组
   List<Map<String, Object>> logs = dataMap.get("progressLogs");
   logs.add(logEntry);
   
   // 更新数据库
   aigcService.updateChatData(chatData);
   ```
   - 每条日志实时追加到`data.progressLogs`数组
   - 用户查看历史可看到完整执行过程

2. **执行截图**（`TASK_SCREENSHOT`）✅ **实时追加**
   ```java
   // 追加到screenshots数组
   List<String> screenshots = dataMap.get("screenshots");
   screenshots.add(screenshotUrl);
   
   // 更新数据库
   aigcService.updateChatData(chatData);
   ```
   - 每张截图实时追加到`data.screenshots`数组
   - 支持轮播查看历史截图

3. **最终结果**（`TASK_RESULT`）✅ **完整保存**
   - 保存完整结果到`wc_chat_history`表
   - 包含：sessionId、userId、userPrompt、data、chatId
   - 根据AI类型设置对应字段（deepseekChatId、ybChatId）
   - 触发会话状态管理，标记AI任务完成

---

### 阶段5：会话状态管理

```
存储后 → 更新会话状态 → 检查是否全部完成
```

**AiSessionStateManager机制**：

```java
// 标记AI任务完成
boolean allCompleted = sessionStateManager.markAiCompleted(sessionId, aiType);

if (allCompleted) {
    log.info("🎉 整轮对话完成 - 会话: {}, 所有AI任务已完成", sessionId);
    // 可以触发额外业务逻辑：发送通知、生成报告等
}
```

**状态跟踪**：
- ✅ 跟踪每个AI任务的状态（started、completed、failed）
- ✅ 判断整轮对话是否完成
- ✅ 某个AI失败不影响其他AI的完成判断

---

### 阶段6：前端接收并显示

```
Admin转发消息 → WebSocket → 前端接收 → 更新UI
```

**前端处理**（已更新）：
```javascript
// 收到WebSocket消息
const handleWebSocketMessage = (data) => {
  const message = JSON.parse(data)
  const messageType = message.type
  const payload = message.payload
  
  // 🎯 处理通用任务消息（TASK_*）
  if (messageType === 'TASK_LOG') {
    // 显示进度日志
    addProgressLog({
      content: payload.message,
      timestamp: payload.timestamp,
      aiType: payload.aiType
    })
  }
  
  if (messageType === 'TASK_SCREENSHOT') {
    // 显示执行截图
    screenshots.value.push(payload.screenshotUrl)
  }
  
  if (messageType === 'TASK_RESULT') {
    // 显示最终结果（数据已在后端存储）
    if (payload.success) {
      results.value.push({
        aiType: payload.aiType,
        sessionId: payload.sessionId,
        data: payload.data
      })
      ElMessage.success('AI处理完成')
    } else {
      ElMessage.error(payload.errorMessage || 'AI处理失败')
    }
  }
}
```

**关键点**：
- ✅ 前端只负责UI展示，不直接操作数据库
- ✅ 使用通用消息类型（TASK_*），不依赖具体AI类型
- ✅ 刷新页面可从数据库恢复完整历史（包括日志和截图）
- ✅ 实时显示进度，用户体验流畅

---

## 🗄️ 数据库存储策略

### 表结构：`wc_chat_history`

```sql
CREATE TABLE `wc_chat_history` (
  `id` varchar(255) NOT NULL COMMENT '主键ID（sessionId）',
  `user_id` varchar(10) COMMENT '用户ID',
  `userPrompt` longtext COMMENT '用户提问',
  `data` longtext COMMENT '完整数据（JSON格式）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deepseek_chat_id` varchar(100) COMMENT 'DeepSeek会话ID',
  `yb_chat_id` varchar(100) COMMENT '元宝会话ID',
  `chat_id` varchar(36) COMMENT '内部chatID',
  PRIMARY KEY (`id`),
  INDEX `idx_user_chat`(`user_id`, `chat_id`),
  INDEX `idx_deepseek`(`deepseek_chat_id`),
  INDEX `idx_yuanbao`(`yb_chat_id`)
) ENGINE=InnoDB COMMENT='聊天历史记录表（简化版）';
```

### 存储内容示例（已优化）

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userId": "1",
  "userPrompt": "帮我写一篇关于AI的文章",
  "chatId": "550e8400-e29b-41d4-a716-446655440000",
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
        "content": "AI正在思考中（已等待6秒）...",
        "timestamp": 1704700806000,
        "aiType": "deepseek"
      }
    ],
    "screenshots": [
      "http://localhost:8080/profile/upload/engine/user1_deepseek_progress_1.png",
      "http://localhost:8080/profile/upload/engine/user1_deepseek_progress_2.png",
      "http://localhost:8080/profile/upload/engine/user1_deepseek_conversation_ds_chat_123456.png"
    ],
    "query": "帮我写一篇关于AI的文章",
    "answer": "AI是人工智能的缩写...",
    "shareUrl": "https://chat.deepseek.com/a/chat/s/ds_chat_123456",
    "elapsedTime": 15,
    "mode": "normal"
  }
}
```

**关键改进**：
- ✅ `progressLogs`数组：存储完整执行日志，支持历史回顾
- ✅ `screenshots`数组：存储所有截图URL，支持轮播查看
- ✅ 数据结构化存储，前端展示更灵活

---

## 🔐 任务隔离机制

### 1. **消息级别隔离**（已更新）

```java
// 🎯 跳过登录类消息（不含AI_前缀）
if (type != null && (type.contains("LOGIN") || type.contains("CHECK"))) {
    log.debug("[实时存储] 跳过登录类消息 - 类型: {}", type);
    return;
}

// 🎯 只处理通用任务消息（TASK_LOG、TASK_SCREENSHOT、TASK_RESULT）
if (type == null || (!type.equals("TASK_LOG") && !type.equals("TASK_SCREENSHOT") && !type.equals("TASK_RESULT"))) {
    return;  // 非任务消息，不处理
}
```

**消息类型区分**：
- 登录类：`DEEPSEEK_CHECK_LOGIN`、`DEEPSEEK_SCAN_LOGIN` - 不处理存储
- AI咨询：`AI_DEEPSEEK_QUERY` - 请求类型标识
- 任务消息：`TASK_LOG`、`TASK_SCREENSHOT`、`TASK_RESULT` - 存储处理

### 2. **AI任务独立处理**

```
用户发起请求 → 同时调用多个AI

DeepSeek: 请求 → 处理 → 存储 → 完成 ✅
元宝:     请求 → 处理 → 失败 ❌  （不影响DeepSeek）
Kimi:     请求 → 处理 → 存储 → 完成 ✅
```

### 3. **状态独立跟踪**

```java
// 每个AI独立标记
sessionStateManager.markAiCompleted(sessionId, "deepseek");
sessionStateManager.markAiFailed(sessionId, "yuanbao", "网络错误");
sessionStateManager.markAiCompleted(sessionId, "kimi");

// 统计整体完成情况
// 成功: 2, 失败: 1, 总计: 3 → 整轮完成
```

---

## 📊 完整流程图

```
┌─────────┐
│  前端    │ 发送AI请求（不保存数据库）
└────┬────┘
     │ WebSocket
     ↓
┌─────────────────────────────────────┐
│  Admin (AigcController)             │
│  1. 生成requestId                   │
│  2. 设置用户信息                    │
│  3. 🔥 保存初始请求到数据库          │
│  4. 转发给Engine                    │
└────┬────────────────────────────────┘
     │
     ↓
┌─────────────────────────────────────┐
│  Engine                             │
│  - 处理AI请求                       │
│  - 返回进度消息（_PROGRESS）        │
│  - 返回截图消息（_SCREENSHOT）      │
│  - 返回最终结果（_RESULT）          │
└────┬────────────────────────────────┘
     │ 每条消息都经过Admin
     ↓
┌─────────────────────────────────────┐
│  Admin (EngineMessageRouter)        │
│  🎯 只处理AI_开头的消息              │
│                                     │
│  _PROGRESS → 记录日志               │
│  _SCREENSHOT → 记录截图              │
│  _RESULT → 🔥 保存到数据库 + 标记完成│
│  _ERROR → 保存错误 + 标记失败        │
│                                     │
│  检查：所有AI完成？→ 🎉 整轮完成     │
└────┬────────────────────────────────┘
     │ 转发消息
     ↓
┌─────────┐
│  前端    │ 接收消息，更新UI（不保存数据库）
└─────────┘
     │ 页面刷新
     ↓
┌─────────────────────────────────────┐
│  从数据库恢复数据                    │
│  - 读取聊天历史                      │
│  - 显示之前的对话                    │
└─────────────────────────────────────┘
```

---

## 🎁 核心优势

### 1. **数据安全性** 🔒
- ✅ 实时存储，不依赖前端
- ✅ 每条消息都立即保存
- ✅ WebSocket断开不影响数据完整性

### 2. **任务可靠性** 💪
- ✅ 某个AI失败不影响其他AI
- ✅ 错误信息完整记录
- ✅ 可追溯每个AI的执行状态

### 3. **业务清晰性** 📐
- ✅ 消息类型规范化，区分登录和AI业务
- ✅ 使用通用任务消息（TASK_*）统一处理
- ✅ 代码结构清晰易维护

### 4. **用户体验** 🎨
- ✅ 前端无需关心存储逻辑
- ✅ 刷新页面可恢复完整数据（包括日志和截图）
- ✅ 实时展示各AI进度，历史可回溯
- ✅ 草稿库支持查看历史对话详情

---

## 🔧 关键代码位置

### 前端
- **请求发送**：`/WxFbsir-ui/src/views/aigc/index.vue`
  - `sendPrompt()` - 发送AI请求
  - `handleWebSocketMessage()` - 处理响应消息
  - 已移除数据库保存调用

### 后端-Admin
- **初始请求存储**：`/WxFbsir-business/src/main/java/com/wx/fbsir/business/aigc/controller/AigcController.java`
  - `handleAiRequest()` - 处理请求入口
  - `saveInitialRequest()` - 保存初始请求

- **实时存储路由**：`/WxFbsir-business/src/main/java/com/wx/fbsir/business/websocket/server/EngineMessageRouter.java`
  - `processRealtimeStorage()` - 实时存储处理（已更新）
  - `appendProgressLog()` - 追加进度日志（新增）
  - `appendScreenshot()` - 追加截图（新增）
  - `saveAiResult()` - 保存AI结果

- **会话状态管理**：`/WxFbsir-business/src/main/java/com/wx/fbsir/business/aigc/manager/AiSessionStateManager.java`
  - `createSession()` - 创建会话
  - `markAiCompleted()` - 标记完成
  - `markAiFailed()` - 标记失败

### 数据库
- **迁移脚本**：`/sql/aigc_migration.sql`
  - 表结构定义
  - 菜单权限配置

---

## 📝 使用示例

### 场景1：单个AI请求

```javascript
// 前端发送
sendWebSocketMessage('AI_DEEPSEEK_QUERY', {
  query: '你好',
  sessionId: 'session-001',
  aiType: 'deepseek'
})

// 后端自动处理：
// 1. 保存初始请求 ✅
// 2. 转发给Engine
// 3. 收到进度 → 记录日志 ✅
// 4. 收到结果 → 保存数据库 ✅
// 5. 标记完成 ✅
// 6. 转发给前端显示
```

### 场景2：多个AI同时请求

```javascript
// 前端同时发送多个AI请求
['deepseek', 'yuanbao', 'kimi'].forEach(aiType => {
  sendWebSocketMessage(`AI_${aiType.toUpperCase()}_QUERY`, {
    query: '你好',
    sessionId: 'session-002',
    aiType: aiType
  })
})

// 后端处理：
// DeepSeek: 处理中... → 完成 ✅ (1/3)
// 元宝:     处理中... → 失败 ❌ (2/3)
// Kimi:     处理中... → 完成 ✅ (3/3)
// 
// 整轮对话完成！🎉
```

### 场景3：页面刷新恢复（已优化）

```javascript
// 页面加载时
onMounted(async () => {
  // 从数据库读取历史
  const history = await getChatHistory()
  
  // 恢复最近会话（包括完整日志和截图）
  if (history.length > 0) {
    const latest = history[0]
    const data = JSON.parse(latest.data)
    
    // 恢复结果
    results.value = data.results || []
    
    // 恢复截图数组
    screenshots.value = data.screenshots || []
    
    // 恢复进度日志
    progressLogs.value = data.progressLogs || []
    
    // 用户可查看完整执行过程
    console.log('历史日志:', progressLogs.value)
    console.log('历史截图:', screenshots.value)
  }
})
```

---

## ⚠️ 注意事项（已更新）

1. **消息类型规范**（重要变更）
   - **登录类消息**：不含`AI_`前缀（`DEEPSEEK_CHECK_LOGIN`、`DEEPSEEK_SCAN_LOGIN`）
   - **AI咨询请求**：保留`AI_`前缀（`AI_DEEPSEEK_QUERY`）
   - **响应消息**：使用通用类型（`TASK_LOG`、`TASK_SCREENSHOT`、`TASK_RESULT`）
   - Engine端使用`@OnceCapability`或`@StreamCapability`注解定义消息类型

2. **sessionId唯一性**
   - 每轮对话必须使用唯一sessionId
   - 用于关联所有AI任务
   - 作为数据库主键存储

3. **错误处理**
   - 某个AI失败不影响其他AI
   - 错误信息完整记录到数据库
   - 会话状态管理跟踪失败任务

4. **数据存储策略**
   - **进度日志**：实时追加到`data.progressLogs`数组
   - **执行截图**：实时追加到`data.screenshots`数组
   - **最终结果**：保存到`data`字段
   - 支持历史查询和完整回放

5. **性能考虑**
   - 追加操作使用UPDATE而非频繁INSERT
   - 截图上传到文件服务器，数据库只存URL
   - 大数据使用JSON格式压缩存储

---

## 🚀 未来扩展

1. **添加新AI**
   - 只需Engine端添加处理器，使用`@StreamCapability`注解
   - 使用通用消息类型`TASK_*`，无需定义新类型
   - Admin端自动处理存储，无需修改代码

2. **增强监控**
   - 记录每个AI的执行时间
   - 统计成功率和失败率
   - 生成性能报告
   - 分析进度日志，优化执行流程

3. **优化存储**
   - 大数据压缩存储
   - 历史数据归档（按月分表）
   - 进度日志和截图独立存储
   - 实现数据清理策略

4. **增强用户体验**
   - 草稿库支持筛选和搜索
   - 历史对话详情页展示完整日志
   - 截图支持放大预览和下载
   - 支持对话导出为Markdown

---

## 📞 联系与支持

如有问题，请参考：
- 代码注释
- 日志输出
- 本文档

祝您使用愉快！🎉
