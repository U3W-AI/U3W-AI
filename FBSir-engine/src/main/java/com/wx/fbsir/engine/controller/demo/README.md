# 演示Controller完整指南

> **更新日期**：2026-07-11

本目录包含两个完整的演示Controller，展示框架的所有核心能力。

---

## 📚 目录

1. [BaiduHotSearchDemoController](#1-baiduhotsearchdemocontroller-流式输出)
2. [SimpleHealthCheckDemoController](#2-simplehealthcheckdemocontroller-单次输出)
3. [对比总结](#3-对比总结)
4. [开发建议](#4-开发建议)

---

## 1. BaiduHotSearchDemoController（流式输出）

### 演示内容

这是一个**流式输出的完整示例**，展示了框架的几乎所有能力：

- ✅ **流式输出** - 继承 `StreamTaskHelper`，实现进度推送
- ✅ **Playwright自动化** - 浏览器控制、页面操作、元素定位
- ✅ **会话管理** - 持久化会话、状态保存、资源管理
- ✅ **截图上传** - 自动截图、图片上传、URL返回
- ✅ **数据提取** - 页面元素抓取、结构化数据返回
- ✅ **异常处理** - 完整的错误处理和资源清理
- ✅ **进度推送** - 实时推送任务进度到前端
- ✅ **中间态返回** - 通过 `sendLog`、`sendScreenshot` 推送中间结果

### 业务流程

1. 打开百度首页
2. 抓取热搜榜前10条数据（标题、链接、热度标签）
3. 点击指定索引的热搜
4. 等待页面加载完成
5. 截图并上传，获取图片URL
6. 返回热搜数据和截图URL

### 客户端调用示例

```json
{
  "type": "BAIDU_HOT_SEARCH_DEMO",
  "engineId": "engine-001",
  "payload": {
    "clickIndex": 0,
    "needScreenshot": true,
    "config": {
      "timeout": 30000,
      "retry": true
    },
    "filters": [
      {"field": "status", "value": "active"}
    ],
    "metadata": {
      "tags": ["tag1", "tag2"]
    }
  }
}
```

**⚠️ 注意**：
- JSON必须是**单行**或**正确格式化**的，不能有多余的换行符
- 如果使用websocat等工具，确保JSON在一行内
- 如果出现JSON解析错误，检查是否有特殊字符或格式问题

### 返回消息流程

#### 1. 中间态（TASK_LOG）- 多次推送

```json
{
  "type": "TASK_LOG",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "message": "正在启动浏览器...",
    "timestamp": 1234567890
  }
}
```

```json
{
  "type": "TASK_LOG",
  "payload": {
    "requestId": "xxx",
    "message": "正在打开百度首页...",
    "timestamp": 1234567891
  }
}
```

```json
{
  "type": "TASK_LOG",
  "payload": {
    "requestId": "xxx",
    "message": "成功抓取 10 条热搜数据",
    "timestamp": 1234567892
  }
}
```

#### 2. 中间态（TASK_SCREENSHOT）- 可选推送

```json
{
  "type": "TASK_SCREENSHOT",
  "payload": {
    "requestId": "xxx",
    "screenshotUrl": "http://example.com/screenshot.png",
    "description": "点击热搜后的页面截图",
    "timestamp": 1234567893
  }
}
```

#### 3. 最终结果（TASK_RESULT）- 一次推送

```json
{
  "type": "TASK_RESULT",
  "payload": {
    "requestId": "xxx",
    "success": true,
    "data": {
      "hotSearchList": [
        {
          "rank": 1,
          "title": "总书记始终不变的牵挂",
          "url": "https://www.baidu.com/s?wd=...",
          "tag": "置顶",
          "tagType": "top"
        },
        {
          "rank": 2,
          "title": "解放军台岛周边演习现场画面",
          "url": "https://www.baidu.com/s?wd=...",
          "tag": "新",
          "tagType": "new"
        }
      ],
      "clickedIndex": 0,
      "clickedItem": {
        "rank": 1,
        "title": "总书记始终不变的牵挂",
        "url": "https://www.baidu.com/s?wd=...",
        "actualUrl": "https://baijiahao.baidu.com/...",
        "screenshotUrl": "http://example.com/screenshot.png"
      },
      "totalCount": 10,
      "timestamp": 1234567894
    },
    "timestamp": 1234567894
  }
}
```

### 核心代码片段

```java
@Controller
public class BaiduHotSearchDemoController extends StreamTaskHelper {
    
    @StreamCapability(
        type = "BAIDU_HOT_SEARCH_DEMO",
        description = "百度热搜抓取演示",
        progressInterval = 3000  // 可选：自动心跳间隔
    )
    public void handleBaiduHotSearch(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        // 创建流式任务
        StreamTask task = startStreamTask(userId, requestId);
        
        BrowserSession session = null;
        try {
            // 推送进度日志
            task.sendLog("正在启动浏览器...");
            
            // 获取浏览器会话
            session = browserPool.acquirePersistent(userId, "baidu_demo", false);
            Page page = session.getOrCreatePage();
            
            // 执行业务逻辑...
            task.sendLog("正在打开百度首页...");
            page.navigate("https://www.baidu.com");
            
            // 推送截图
            String screenshotUrl = uploadClient.uploadScreenshot(screenshotPath, userId);
            task.sendScreenshot(screenshotUrl, "描述信息");
            
            // 发送最终结果
            task.sendSuccess("任务完成", resultData);
            
        } catch (Exception e) {
            task.sendError("任务失败: " + e.getMessage());
        } finally {
            task.stop();  // 停止心跳
            if (session != null) session.destroy();  // 释放资源
        }
    }
}
```

---

## 2. SimpleHealthCheckDemoController（单次输出）

### 演示内容

这是一个**单次输出的完整示例**，展示了简单任务的标准实现：

- ✅ **单次返回** - 不继承 `StreamTaskHelper`，直接使用 `WebSocketClientManager`
- ✅ **参数提取** - 从 `EngineMessage` 中提取 payload 参数
- ✅ **业务处理** - 收集系统性能数据
- ✅ **数据封装** - 构建结构化的返回数据
- ✅ **消息发送** - 使用 `EngineMessage.builder()` 构建响应
- ✅ **异常处理** - 完整的错误处理和错误响应

### 客户端调用示例

```json
{"type": "SIMPLE_HEALTH_CHECK_DEMO", "engineId": "engine-001", "payload": {"includeDetails": true}}
```

### 返回消息

#### 成功响应（TASK_RESULT）- 一次推送

```json
{
  "type": "TASK_RESULT",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "success": true,
    "data": {
      "status": "healthy",
      "hardware": {
        "cpuModel": "Apple M1",
        "cpuCores": 8,
        "totalMemoryGB": 16
      },
      "performance": {
        "cpuUsage": 0.35,
        "memoryUsage": 0.68,
        "jvmMemoryUsageMB": 512,
        "jvmMaxMemoryMB": 2048
      },
      "components": {
        "websocket": {
          "connected": true,
          "status": "CONNECTED"
        }
      },
      "timestamp": 1234567890
    },
    "timestamp": 1234567890
  }
}
```

#### 错误响应（TASK_RESULT）

```json
{
  "type": "TASK_RESULT",
  "payload": {
    "requestId": "xxx",
    "success": false,
    "errorCode": "TASK_ERROR",
    "errorMessage": "系统异常: xxx",
    "timestamp": 1234567890
  }
}
```

### 核心代码片段

```java
@Controller
public class SimpleHealthCheckDemoController {
    
    @Autowired
    private WebSocketClientManager webSocketClientManager;
    
    @OnceCapability(
        type = "SIMPLE_HEALTH_CHECK_DEMO",
        description = "简单健康检查演示",
        timeout = 30000L
    )
    public void handleHealthCheck(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        // 提取业务参数（带默认值）
        Boolean includeDetails = message.getPayloadValue("includeDetails");
        if (includeDetails == null) includeDetails = false;
        
        try {
            // 执行业务逻辑
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("status", "healthy");
            resultData.put("hardware", getHardwareInfo());
            
            // 发送成功结果
            sendSuccessResult(userId, requestId, resultData);
            
        } catch (Exception e) {
            // 发送错误结果
            sendErrorResult(userId, requestId, "系统异常: " + e.getMessage());
        }
    }
    
    private void sendSuccessResult(String userId, String requestId, Map<String, Object> data) {
        EngineMessage result = EngineMessage.builder()
            .type("TASK_RESULT")
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", true)
            .payload("data", data)
            .build();
        
        webSocketClientManager.sendMessage(result);
    }
}
```

---

## 3. 对比总结

### 单次返回 vs 流式返回

| 特性 | 单次返回 | 流式返回 |
|------|---------|---------|
| **基类** | 不继承 | 继承 `StreamTaskHelper` |
| **注解** | `@OnceCapability` | `@StreamCapability` |
| **注入** | `WebSocketClientManager` | 自动注入（继承获得） |
| **消息数量** | 1次 TASK_RESULT | 多次 TASK_LOG/TASK_SCREENSHOT + 1次 TASK_RESULT |
| **适用场景** | 快速返回（数据查询、状态检查） | 长时间运行（爬虫、AI对话、文件处理） |
| **进度推送** | ❌ 不支持 | ✅ 支持 |
| **截图推送** | ❌ 不支持 | ✅ 支持 |
| **心跳机制** | ❌ 不支持 | ✅ 可选支持 |
| **资源管理** | 简单 | 需要在 finally 中调用 `task.stop()` |

### 消息类型说明

| 消息类型 | 发送时机 | 发送次数 | 用途 |
|---------|---------|---------|------|
| **TASK_LOG** | 任务执行中 | 多次 | 推送进度日志（前端显示在日志区） |
| **TASK_SCREENSHOT** | 任务执行中 | 多次 | 推送截图URL（前端显示在轮播区） |
| **TASK_PROGRESS** | 任务执行中 | 多次 | ⚠️ 已废弃，请使用 TASK_LOG |
| **TASK_RESULT** | 任务结束时 | 1次 | 推送最终结果（成功或失败） |

---

## 4. 开发建议

### 如何选择实现方式？

#### 使用单次返回（SimpleHealthCheckDemoController）当：

- ✅ 任务执行时间 < 5秒
- ✅ 不需要向前端推送进度
- ✅ 只需要返回最终结果
- ✅ 例如：数据查询、状态检查、参数验证

#### 使用流式返回（BaiduHotSearchDemoController）当：

- ✅ 任务执行时间 > 5秒
- ✅ 需要向前端推送进度日志
- ✅ 需要推送截图或其他中间结果
- ✅ 例如：爬虫任务、AI对话、文件处理、浏览器自动化

### 开发新能力的步骤

#### 步骤1：创建Controller类

```java
@Controller
public class MyNewController extends StreamTaskHelper {  // 或不继承，看需求
    // ...
}
```

#### 步骤2：添加能力注解

```java
@StreamCapability(type = "MY_NEW_TASK", description = "我的新任务")
public void handleMyTask(EngineMessage message) {
    // ...
}
```

#### 步骤3：提取参数

```java
String userId = message.getUserId();
String requestId = message.getPayloadValue("requestId");

// 业务参数（带默认值）
String query = message.getPayloadValue("query");
if (query == null) query = "默认值";
```

#### 步骤4：执行业务逻辑

```java
// 流式任务
StreamTask task = startStreamTask(userId, requestId);
task.sendLog("正在处理...");
// 业务代码...
task.sendSuccess("完成", resultData);

// 或单次任务
EngineMessage result = EngineMessage.builder()
    .type("TASK_RESULT")
    .userId(userId)
    .payload("requestId", requestId)
    .payload("success", true)
    .payload("data", resultData)
    .build();
webSocketClientManager.sendMessage(result);
```

#### 步骤5：更新MessageType（可选）

在 `MessageType.java` 中添加枚举常量（用于代码提示和文档）：

```java
MY_NEW_TASK("MY_NEW_TASK", "我的新任务")
```

> ⚠️ 注意：即使不更新 MessageType，能力也能正常工作，因为注册完全依赖注解。

### 参数提取最佳实践

```java
// ✅ 好的做法：带默认值
Integer count = message.getPayloadValue("count");
if (count == null) count = 10;

// ✅ 好的做法：类型转换异常处理
try {
    List<String> items = message.getPayloadValue("items");
} catch (ClassCastException e) {
    log.error("参数类型错误", e);
    task.sendError("参数items必须是数组");
    return;
}

// ❌ 不好的做法：直接使用可能为null的值
Integer count = message.getPayloadValue("count");
int result = count * 2;  // 可能 NullPointerException
```

### 资源管理最佳实践

```java
BrowserSession session = null;
try {
    session = browserPool.acquirePersistent(userId, "session_key", false);
    // 使用 session...
} finally {
    // 🔥 重要：必须释放资源
    if (session != null) {
        session.destroy();
    }
}
```

### 错误处理最佳实践

```java
try {
    // 业务代码
    task.sendSuccess("完成", resultData);
} catch (Exception e) {
    log.error("任务失败", e);
    // 🔥 重要：发送错误结果，不要让前端一直等待
    task.sendError("任务失败: " + e.getMessage());
} finally {
    // 🔥 重要：清理资源
    task.stop();
    if (session != null) session.destroy();
}
```

---

## 🎯 快速开始

### 测试百度热搜演示

```bash
# 通过WebSocket发送测试消息
{
  "type": "BAIDU_HOT_SEARCH_DEMO",
  "engineId": "engine-001",
  "payload": {
    "clickIndex": 0,
    "needScreenshot": true
  }
}
```

### 测试健康检查演示

```bash
{
  "type": "SIMPLE_HEALTH_CHECK_DEMO",
  "engineId": "engine-001",
  "payload": {
    "includeDetails": true
  }
}
```

