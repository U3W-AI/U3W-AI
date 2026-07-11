# 🔌 WebSocket 通信完整指南
> 更新日期：2026-07-11

> **更新日期**：2026-07-11
>
> **目标读者**: 需要开发Engine能力或深入理解WebSocket通信机制的后端开发者  
> **文档用途**: 从快速入门到深入精通，全面讲解WebSocket通信架构

---

## 📖 如何使用本文档

### 🚀 快速入门（5分钟）

**如果你是新手开发者，想快速实现一个能力**，请阅读：
- [第0章：快速入门 - 5分钟实现你的第一个能力](#0-快速入门---5分钟实现你的第一个能力) ⭐⭐⭐

### 🎓 框架精通（深入学习）

**如果你需要深入理解架构或解决复杂问题**，请按顺序阅读：
1. [第1章：架构概述](#1-架构概述) - 理解整体架构
2. [第2章：消息协议](#2-消息协议) - 消息格式定义
3. [第3章：通信流程详解](#3-通信流程详解) - 完整交互流程
4. [第12章：流式输出 vs 单次输出](#12-流式输出-vs-单次输出完整指南) - 选择合适的实现方式

### 📚 参考手册（按需查阅）

遇到问题时查阅：
- [第9章：故障排除](#9-故障排除) - 常见问题解决
- [第10章：API快速参考](#10-api快速参考) - API速查表
- [第11章：Payload处理指南](#11-payload处理指南) - 参数处理

---

## 📑 完整目录

### 第0章：快速入门 ⭐⭐⭐ 新手必读
- [0.1 开发第一个能力的完整步骤](#01-开发第一个能力的完整步骤)
- [0.2 单次输出能力示例](#02-单次输出能力示例)
- [0.3 流式输出能力示例](#03-流式输出能力示例)
- [0.4 常见问题](#04-常见问题)

### 第1-11章：框架精通
1. [架构概述](#1-架构概述)
2. [消息协议](#2-消息协议)
3. [通信流程详解](#3-通信流程详解)
4. [Admin端实现](#4-admin端实现)
5. [Engine端实现](#5-engine端实现)
6. [配置说明](#6-配置说明)
7. [代码文件结构](#7-代码文件结构)
8. [业务示例](#8-业务示例)
9. [故障排除](#9-故障排除)
10. [API快速参考](#10-api快速参考)
11. [Payload处理指南](#11-payload处理指南)

### 第12章：开发指南 ⭐⭐⭐ 开发必读
- [12. 流式输出 vs 单次输出完整指南](#12-流式输出-vs-单次输出完整指南)

---

## 源码校准说明（2026-07-10）

以下事实以当前源码和已通过的自动化测试为准，优先级高于本文后续旧示例：

1. Engine WebSocket 连接地址默认是 `ws://localhost:8080/ws/engine`，当前源码不要求通过查询参数传递 `clientId`。
2. Engine 的身份信息在注册消息 `ENGINE_REGISTER` 中通过 `engineId` 提供。
3. `/ws/engine` 与 `/ws/client` 仅对 WebSocket 握手路径放行；除握手路径外，其余 `/ws/**` HTTP 接口均要求 JWT，匿名访问返回 HTTP 401。
4. Engine 注册时的白名单校验不仅包含 `hostId`，还会校验状态、过期时间、IP 规则以及 `hostType=engine`。
5. Admin 发送给 Engine 的消息会自动补充顶层字段 `requestId`、`userId`、`sourceType`、`sourceClientId`，并在 `payload` 中补充 `requestId` 与 `sourceType`。
6. `TASK_RESULT` 示例中的 `payload.requestId` 不应为 `null`；当前链路下会返回真实请求 ID，并按来源返回 `sourceType=WEBSOCKET` 或 `HTTP`。
7. `/ws/client` 使用 `clientType` 识别客户端；服务端优先读取 `Authorization`，并兼容 `token` / `key` 查询参数。当前 Vue 页面因浏览器原生 WebSocket 不能设置自定义 Header，使用 `clientType + token` 查询参数建立连接。

---

## 0. 快速入门 - 5分钟实现你的第一个能力

### 0.1 开发第一个能力的完整步骤

#### 步骤1：确定能力类型

**单次输出**（推荐新手）：
- 任务执行时间 < 5秒
- 不需要推送中间进度
- 示例：健康检查、数据查询、简单计算

**流式输出**（适合复杂任务）：
- 任务执行时间 > 5秒
- 需要推送中间进度、日志、截图
- 示例：网页抓取、AI对话、批量处理

#### 步骤2：在Engine创建Controller

**位置**: `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/`

**目录结构**:
```
controller/
├── demo/           # 演示示例（可参考）
│   ├── BaiduHotSearchDemoController.java      # 流式输出示例
│   ├── SimpleHealthCheckDemoController.java   # 单次输出示例
│   └── README.md                              # 演示说明
├── ai/             # AI相关能力
│   └── DeepSeekController.java
└── business/       # 业务能力
    └── YourController.java                    # 你的新能力
```

#### 步骤3：编写Controller代码

**单次输出示例**（完整可运行）：

```java
package com.wx.fbsir.engine.controller.business;

import com.wx.fbsir.engine.capability.annotation.OnceCapability;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.util.WebSocketSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.Map;

/**
 * 我的第一个能力
 */
@Controller
public class MyFirstController {
    
    private static final Logger log = LoggerFactory.getLogger(MyFirstController.class);
    
    @Autowired
    private WebSocketSender webSocketSender;
    
    /**
     * 处理请求（单次返回）
     * 
     * @param message 请求消息
     */
    @OnceCapability(
        type = "MY_FIRST_CAPABILITY",
        description = "我的第一个能力"
    )
    public void handleRequest(EngineMessage message) {
        // 1. 提取参数
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        String name = message.getPayloadValue("name");
        
        log.info("[我的能力] 收到请求 - 用户: {}, 参数: {}", userId, name);
        
        try {
            // 2. 执行业务逻辑
            String result = "Hello, " + (name != null ? name : "World") + "!";
            
            // 3. 构建返回数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("message", result);
            resultData.put("timestamp", System.currentTimeMillis());
            
            // 4. 发送成功响应
            EngineMessage response = EngineMessage.builder()
                .type("TASK_RESULT")
                .userId(userId)
                .payload("requestId", requestId)
                .payload("success", true)
                .payload("data", resultData)
                .build();
            
            webSocketSender.sendToAdmin(response);
            
            log.info("[我的能力] 处理完成 - 用户: {}", userId);
            
        } catch (Exception e) {
            log.error("[我的能力] 处理失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
            
            // 发送错误响应
            EngineMessage errorResponse = EngineMessage.builder()
                .type("TASK_RESULT")
                .userId(userId)
                .payload("requestId", requestId)
                .payload("success", false)
                .payload("error", e.getMessage())
                .build();
            
            webSocketSender.sendToAdmin(errorResponse);
        }
    }
}
```

#### 步骤4：测试你的能力

**使用websocat测试**:
```bash
# 1. 连接到Admin
websocat ws://localhost:8080/ws/client

# 2. 发送测试消息（单行JSON）
{"type": "MY_FIRST_CAPABILITY", "engineId": "engine-001", "payload": {"name": "张三"}}

# 3. 查看返回结果
{
  "type": "TASK_RESULT",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "success": true,
    "data": {
      "message": "Hello, 张三!",
      "timestamp": 1735459200000
    }
  }
}
```

#### 步骤5：注意事项

**✅ 必须做的**:
1. Controller类必须添加 `@Controller` 注解
2. 方法必须添加 `@OnceCapability` 或 `@StreamCapability` 注解
3. 方法必须是 `public void`
4. 方法参数必须是 `EngineMessage message`
5. 必须发送响应消息（成功或失败）
6. 为业务封装的工具类放在 `utils`下

**❌ 不要做的**:
1. 不要修改Admin端代码（只在Engine端开发）
2. 不要忘记添加异常处理
3. 不要忘记添加日志
4. 不要在FBSir-engine中直接操作数据库（数据传回admin进行存储）

**📁 文件放置位置**:
- Controller: `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/business/`
- Service: `FBSir-engine/src/main/java/com/wx/fbsir/engine/service/`
- Utils: `FBSir-engine/src/main/java/com/wx/fbsir/engine/utils/`

---

### 0.2 单次输出能力示例

完整示例请参考：`controller/demo/SimpleHealthCheckDemoController.java`

**特点**:
- ✅ 不继承任何基类
- ✅ 使用 `@OnceCapability` 注解
- ✅ 直接构建 `EngineMessage` 发送
- ✅ 适合快速返回的任务

**调用示例**:
```bash
{"type": "SIMPLE_HEALTH_CHECK_DEMO", "engineId": "engine-001", "payload": {"includeDetails": true}}
```

---

### 0.3 流式输出能力示例

完整示例请参考：`controller/demo/BaiduHotSearchDemoController.java`

**特点**:
- ✅ 继承 `StreamTaskHelper`
- ✅ 使用 `@StreamCapability` 注解
- ✅ 使用 `StreamTask` 推送进度
- ✅ 适合长时间运行的任务

**核心代码**:
```java
@Controller
public class MyStreamController extends StreamTaskHelper {
    
    @StreamCapability(type = "MY_STREAM_TASK", description = "我的流式任务")
    public void handleTask(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        // 创建流式任务
        StreamTask task = startStreamTask(userId, requestId);
        
        try {
            // 推送进度
            task.sendLog("开始处理...");
            
            // 执行业务逻辑
            doSomething();
            
            task.sendLog("处理中...");
            
            // 推送截图（如果需要）
            task.sendScreenshot("https://example.com/screenshot.png");
            
            // 发送最终结果
            Map<String, Object> result = new HashMap<>();
            result.put("status", "success");
            task.sendSuccess("任务完成", result);
            
        } catch (Exception e) {
            task.sendError("任务失败: " + e.getMessage());
        } finally {
            task.stop();
        }
    }
}
```

**调用示例**:
```bash
{"type": "MY_STREAM_TASK", "engineId": "engine-001", "payload": {}}
```

---

### 0.4 常见问题

#### Q1: 如何调试参数传递？

在方法开头添加调试代码：
```java
@OnceCapability(type = "MY_CAPABILITY", description = "我的能力")
public void handleRequest(EngineMessage message) {
    // 调试代码：查看完整消息和Payload
    System.out.println("完整消息: " + message);
    System.out.println("Payload内容: " + message.getPayload());
    
    // 正式代码...
}
```

#### Q2: 如何封装业务工具类？

**位置**: `FBSir-engine/src/main/java/com/wx/fbsir/engine/utils/`

**示例**:
```java
package com.wx.fbsir.engine.utils;

import org.springframework.stereotype.Component;

@Component
public class MyBusinessUtils {
    
    public String processData(String input) {
        // 业务逻辑
        return "processed: " + input;
    }
}
```

**使用**:
```java
@Controller
public class MyController {
    
    @Autowired
    private MyBusinessUtil myBusinessUtil;
    
    @OnceCapability(type = "MY_CAPABILITY", description = "我的能力")
    public void handleRequest(EngineMessage message) {
        String result = myBusinessUtil.processData("test");
        // ...
    }
}
```

#### Q3: 如何使用Playwright自动化？

参考 `BaiduHotSearchDemoController.java` 的完整示例：

```java
@Autowired
private BrowserPoolManager browserPool;

@StreamCapability(type = "MY_BROWSER_TASK", description = "浏览器任务")
public void handleTask(EngineMessage message) {
    String userId = message.getUserId();
    BrowserSession session = null;
    
    try {
        // 获取浏览器会话
        session = browserPool.acquirePersistent(userId, "my_task", false);
        Page page = session.getOrCreatePage();
        
        // 打开网页
        page.navigate("https://example.com");
        
        // 执行操作
        page.click("#button");
        
        // ...
        
    } finally {
        if (session != null) {
            session.destroy(); // 必须释放会话
        }
    }
}
```

#### Q4: 能力类型（type）命名规范

**推荐命名**:
- 业务能力：`BUSINESS_XXX`（如 `BUSINESS_ORDER_QUERY`）
- AI能力：`AI_XXX`（如 `AI_CHAT`）
- 爬虫能力：`CRAWLER_XXX`（如 `CRAWLER_NEWS`）
- 工具能力：`TOOL_XXX`（如 `TOOL_IMAGE_PROCESS`）

**避免命名**:
- ❌ 使用中文
- ❌ 使用特殊字符
- ❌ 过长的名称（建议 < 30字符）

#### Q5: 如何查看完整示例？

**演示代码位置**:
- 单次输出：`controller/demo/SimpleHealthCheckDemoController.java`
- 流式输出：`controller/demo/BaiduHotSearchDemoController.java`
- 使用说明：`controller/demo/README.md`

**深入学习**:
- [第12章：流式输出 vs 单次输出完整指南](#12-流式输出-vs-单次输出完整指南)
- [Playwright框架完整指南](Playwright框架完整指南.md)

---

## 1. 架构概述

### 1.1 系统架构图（⭐⭐⭐ 必读）

```
┌─────────────────────────────────────────────────────────────────────┐
│                         用户前端 (Vue/React)                          │
└─────────────────────────────┬───────────────────────────────────────┘
                              │ HTTP / WebSocket
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      主节点 (FBSir-admin)                           │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐           │
│  │  WebSocket    │  │   Message     │  │   Session     │           │
│  │  Server       │  │   Router      │  │   Manager     │           │
│  │  (Handler)    │  │   (Handler    │  │   (Pool/      │           │
│  │               │  │    Pattern)   │  │    Heartbeat) │           │
│  └───────────────┘  └───────────────┘  └───────────────┘           │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────┐    │
│  │          白名单验证 / 连接限制 / 心跳检测 / 会话清理           │    │
│  └────────────────────────────────────────────────────────────┘    │
└─────────────────────────────┬───────────────────────────────────────┘
                              │ WebSocket (反向连接)
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌───────────┐       ┌───────────┐       ┌───────────┐
    │ Engine-1  │       │ Engine-2  │       │ Engine-N  │
    │ (副节点)   │       │ (副节点)   │       │ (副节点)   │
    │           │       │           │       │           │
    │ WebSocket │       │ WebSocket │       │ WebSocket │
    │ Client    │       │ Client    │       │ Client    │
    │           │       │           │       │           │
    │ Playwright│       │ Playwright│       │ Playwright│
    │ (浏览器)   │       │ (浏览器)   │       │ (浏览器)   │
    └───────────┘       └───────────┘       └───────────┘
```

### 1.2 核心特性（⭐⭐⭐ 必读）

| 特性 | 说明 | 优势 |
|------|------|------|
| ✅ **反向连接** | Engine主动连接Admin | 无需固定IP，穿透防火墙 |
| ✅ **白名单机制** | 基于engine-id验证 | 防止未授权连接 |
| ✅ **单连接限制** | 一个engine-id一个连接 | 防止重复连接和资源浪费 |
| ✅ **心跳检测** | 30秒一次心跳 | 快速发现断线，90秒超时清理 |
| ✅ **指数退避重连** | 1s至30s递增 | 避免重连风暴，保护服务器 |
| ✅ **大消息支持** | 支持5MB消息 | 支持AI长文本、图片传输 |
| ✅ **消息路由** | Handler模式 | 高效、可维护、易扩展 |
| ✅ **异步发送** | 每个连接独立队列 | 防止阻塞，提升吞吐量 |
| ✅ **优雅停机** | 支持任务完成后关闭 | 保证数据完整性 |

### 1.3 通信流程概览

```
时间线：
T0: Engine 启动
    └─ 连接主节点 WebSocket
    
T1: Engine → Admin：发送 ENGINE_REGISTER
    └─ 携带 hostId, version, capabilities

T2: Admin 验证白名单
    ├─ 通过 → 发送 ENGINE_REGISTER_ACK
    └─ 拒绝 → 关闭连接 (code: 4007)

T3: 定时心跳循环
    ├─ Engine → Admin：HEARTBEAT_PING
    └─ Admin → Engine：HEARTBEAT_PONG

T4: 业务消息交互
    ├─ Admin → Engine：TASK_ASSIGN / AI_CHAT_REQUEST
    └─ Engine → Admin：TASK_RESULT / AI_CHAT_RESPONSE
```

---

## 1.4 设计考量与问题解决

### 2.1 既往典型问题分析

| 问题 | cube-engine 表现 | 本框架解决方案 |
|------|------------------|---------------|
| **消息处理臃肿** | 单一 `onMessage` 500+ 行 if-else | Handler 模式消息路由器 |
| **静默异常** | `e.printStackTrace()` | SLF4J 日志，区分级别 |
| **内存泄漏** | Future 对象未清理 | 自动超时 + 定时清理 |
| **线程泄漏** | 多个独立 Scheduler | 统一线程池管理 |
| **重连竞态** | `boolean reconnecting` | `AtomicBoolean` + CAS |
| **连接不稳定** | 心跳机制不完善 | 双向心跳 + 超时检测 |
| **无限重试** | 无法感知致命错误 | 区分错误类型，致命错误终止 |

### 2.2 连接稳定性设计

#### 心跳机制

```
Engine                                Admin
  │                                     │
  │────── HEARTBEAT_PING ──────────────►│
  │                                     │ 更新 lastHeartbeat
  │◄───── HEARTBEAT_PONG ──────────────│
  │ 更新 lastMessageTime                │
  │                                     │
  │  [超过 heartbeat-timeout 未响应]     │
  │                                     │
  │  主动关闭连接，触发重连              │
```

#### 重连策略

```java
// 指数退避重连
初始延迟: 5秒
最大延迟: 30秒
退避因子: 1.5

重连时间线示例:
第1次: 5秒后重试
第2次: 7.5秒后重试
第3次: 11.25秒后重试
...
最大: 30秒后重试
```

#### 致命错误处理

| 错误码 | 含义 | 处理方式 |
|--------|------|---------|
| 4007 | 主机ID不在白名单 | 终止程序 |
| 4008 | 重复连接 | 终止程序 |
| 403 | IP被封禁 | 终止程序 |
| 429 | 请求频繁被限流 | 终止程序 |
| 1011 | 服务器内部错误 | 重试 |
| 1013 | 服务器过载 | 重试 |

### 2.3 资源管理设计

#### 连接管理

```java
// 主节点连接管理
EngineSessionManager {
    ConcurrentHashMap<String, EngineSession> sessions;  // sessionId -> session
    ConcurrentHashMap<String, String> engineIdToSessionId;  // engineId -> sessionId
    
    // 定时清理：每60秒检查一次
    // 清理条件：心跳超时 || 连接关闭
}
```

#### Future 管理

```java
// 老项目问题：Future 持续累积
// 解决方案：自动超时 + 定时清理
CompletableFuture<?> future = new CompletableFuture<>();
future.orTimeout(30, TimeUnit.SECONDS)
    .whenComplete((result, ex) -> {
        futureMap.remove(requestId);  // 完成后立即移除
    });

// 定时清理：每60秒清理超期 Future
```

---

## 7. 代码文件结构

### 3.1 副节点 (FBSir-engine)

```
FBSir-engine/src/main/java/com/wx/fbsir/engine/websocket/
│
├── client/
│   ├── EngineWebSocketClient.java     # WebSocket 客户端
│   │   - 连接管理（连接、重连、关闭）
│   │   - 心跳发送和超时检测
│   │   - 消息发送和接收
│   │   - 错误处理和致命错误检测
│   │   - 指数退避重连策略
│   │
│   └── WebSocketClientManager.java    # 客户端管理器
│       - 生命周期管理
│       - 应用启动时自动连接
│       - 消息发送入口
│       - @PreDestroy 优雅关闭
│
├── handler/
│   └── MessageRouter.java             # 消息路由器
│       - 注册消息处理器
│       - 根据消息类型分发
│       - 替代 if-else 字符串匹配
│
└── message/
    ├── EngineMessage.java             # 消息实体
    │   - messageId: 消息唯一标识
    │   - type: 消息类型
    │   - engineId: 主机ID
    │   - userId: 用户ID
    │   - timestamp: 时间戳
    │   - payload: 消息体（任意JSON）
    │
    └── MessageType.java               # 消息类型枚举
        - 系统消息类型
        - 业务消息类型
```

### 3.2 主节点 (FBSir-admin)

```
FBSir-business/.../websocket/
│
├── server/
│   ├── EngineWebSocketHandler.java    # WebSocket 处理器
│   │   - 连接建立/关闭处理
│   │   - 消息接收和分发
│   │   - 心跳响应
│   │
│   ├── EngineSessionManager.java      # 会话管理器
│   │   - 会话注册/注销
│   │   - 白名单验证
│   │   - 重复连接检测
│   │   - 消息发送
│   │   - 广播
│   │
│   └── EngineMessageRouter.java       # 消息路由器
│       - Handler 注册
│       - 消息分发
│
├── controller/
│   └── EngineAdminController.java     # 管理接口
│       - GET /ws/admin/stats
│       - GET /ws/admin/engines
│       - POST /ws/admin/engines/{id}/task
│       - POST /ws/admin/broadcast
│
└── config/
    └── WebSocketConfig.java           # WebSocket 配置
        - 端点注册
        - 握手拦截器
        - 消息大小限制
```

### 3.3 文件功能速查表

#### Engine 端

| 文件 | 职责 | 关键方法 |
|------|------|---------|
| `EngineWebSocketClient` | WebSocket 连接 | `connect()`, `sendMessage()`, `closeGracefully()` |
| `WebSocketClientManager` | 生命周期管理 | `connect()`, `sendMessage()`, `isConnected()` |
| `MessageRouter` | 消息分发 | `route()`, `registerHandler()` |
| `EngineMessage` | 消息实体 | `builder()`, `getPayloadValue()` |

#### Admin 端

| 文件 | 职责 | 关键方法 |
|------|------|---------|
| `EngineWebSocketHandler` | 消息处理 | `handleTextMessage()`, `afterConnectionClosed()` |
| `EngineSessionManager` | 会话管理 | `registerSession()`, `sendMessage()`, `broadcast()` |
| `EngineMessageRouter` | 消息分发 | `route()`, `registerHandler()` |
| `EngineAdminController` | REST API | `/stats`, `/engines`, `/broadcast` |

---

## 2. 消息协议

### 4.1 统一消息格式（EngineMessage）

#### 基础结构

```json
{
  "messageId": "550e8400e29b41d4a716446655440000",
  "type": "AI_CHAT_REQUEST",
  "engineId": "engine-001",
  "version": "1.0.0",
  "userId": "user-001",
  "traceId": "trace-12345",
  "timestamp": 1702700000000,
  "payload": {
    "requestId": "req-001",
    "prompt": "你好",
    "config": {
      "model": "gpt-4",
      "temperature": 0.7
    }
  },
  "metadata": {
    "source": "admin",
    "priority": "normal"
  }
}
```

### 4.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `messageId` | String | 自动生成 | 消息唯一标识，UUID格式（无横线） |
| `type` | String | ✅ **必填** | 消息类型代码，见 [4.3 消息类型](#43-消息类型) |
| `engineId` | String | 可选 | Engine主机ID（Engine→Admin时必填） |
| `version` | String | 可选 | Engine版本号（注册时必填） |
| `userId` | String | 可选 | 关联的用户ID（业务场景） |
| `traceId` | String | 可选 | 链路追踪ID（用于分布式追踪） |
| `timestamp` | Long | 自动生成 | 消息时间戳（毫秒） |
| `payload` | Object | 可选 | 消息体，存储业务数据（Map结构） |
| `metadata` | Object | 可选 | 元数据，存储附加信息（Map结构） |

### 4.3 消息类型完整列表

#### 系统消息（System Messages）

| 类型代码 | 方向 | 说明 | payload 必填字段 |
|---------|------|------|----------------|
| `ENGINE_REGISTER` | Engine → Admin | Engine注册 | `deviceId`, `capabilities`, 设备信息 |
| `ENGINE_REGISTER_ACK` | Admin → Engine | 注册确认 | `message`, `serverTime` |
| `ENGINE_UNREGISTER` | Engine → Admin | Engine注销 | - |
| `HEARTBEAT_PING` | Engine → Admin | 心跳请求 | - |
| `HEARTBEAT_PONG` | Admin → Engine | 心跳响应 | `serverTime` |
| `ERROR` | 双向 | 错误消息 | `code`, `message` |
| `ADMIN_DISCONNECT` | Admin → Engine | 管理员主动断开 | `reason` |

#### 业务消息（Business Messages）

| 类型代码 | 方向 | 说明 | payload 必填字段 |
|---------|------|------|----------------|
| `TASK_REQUEST` | Admin → Engine | 任务请求 | `taskId`, `taskType` |
| `TASK_PROGRESS` | Engine → Admin | 任务进度 | `taskId`, `progress` |
| `TASK_RESULT` | Engine → Admin | 任务结果 | `taskId`, `result` |
| `TASK_CANCELLED` | Admin → Engine | 任务取消 | `taskId` |
| `AI_CHAT_REQUEST` | Admin → Engine | AI对话请求 | `requestId`, `prompt` |
| `AI_CHAT_RESPONSE` | Engine → Admin | AI对话响应 | `requestId`, `response` |
| `AI_CHAT_STREAM` | Engine → Admin | AI流式响应 | `requestId`, `chunk` |
| `PLAYWRIGHT_TEST` | Admin → Engine | Playwright测试 | `action` |
| `TASK_QUEUE_NOTIFICATION` | Admin → Engine | 任务排队通知 | `queuePosition` |
| `TASK_EXECUTION_START` | Admin → Engine | 任务开始执行 | `taskId` |
| `CANCEL_QUEUE` | Admin → Engine | 取消排队 | `queueId` |

### 4.4 核心消息格式详解

#### 4.4.1 ENGINE_REGISTER（Engine注册消息）

**发送方向**：Engine → Admin  
**触发时机**：WebSocket连接建立后立即发送  
**用途**：上报Engine的完整设备信息、能力列表，用于Admin端识别和管理

**完整格式示例**：

```json
{
  "messageId": "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6",
  "type": "ENGINE_REGISTER",
  "engineId": "engine-001",
  "version": "1.0.0",
  "timestamp": 1703232000000,
  "payload": {
    "deviceId": "f3b8c9d1e2a5f6b7c8d9e0f1a2b3c4d5",
    "capabilities": [
      {
        "name": "playwright_browser",
        "version": "1.40.0",
        "description": "Playwright浏览器自动化",
        "config": {
          "browsers": ["chromium"],
          "headless": true
        }
      }
    ],
    "hostname": "MacBook-Pro.local",
    "osName": "Mac OS X",
    "osVersion": "14.2.1",
    "osArch": "aarch64",
    "javaVersion": "17.0.9",
    "javaVendor": "Oracle Corporation",
    "macAddress": "a4:83:e7:12:34:56",
    "localIp": "192.168.1.100",
    "publicIp": "203.0.113.45"
  }
}
```

**payload字段说明**：

| 字段 | 类型 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `deviceId` | String | ✅ | 设备唯一标识（基于MAC+硬件序列号生成） | `f3b8c9d1...` |
| `capabilities` | Array | ✅ | Engine能力列表 | 见下方能力格式 |
| `hostname` | String | ✅ | 主机名 | `MacBook-Pro.local` |
| `osName` | String | ✅ | 操作系统名称 | `Mac OS X` / `Windows 10` |
| `osVersion` | String | ✅ | 操作系统版本 | `14.2.1` |
| `osArch` | String | ✅ | 系统架构 | `aarch64` / `x86_64` |
| `javaVersion` | String | ✅ | Java版本 | `17.0.9` |
| `javaVendor` | String | ✅ | Java供应商 | `Oracle Corporation` |
| `macAddress` | String | ✅ | 物理网卡MAC地址 | `a4:83:e7:12:34:56` |
| `localIp` | String | ✅ | 本地局域网IP | `192.168.1.100` |
| `publicIp` | String | 可选 | 公网IP（可能获取失败） | `203.0.113.45` |

**能力（Capability）格式**：

```json
{
  "name": "playwright_browser",
  "version": "1.40.0",
  "description": "Playwright浏览器自动化",
  "config": {
    "browsers": ["chromium", "firefox"],
    "headless": true,
    "maxConcurrent": 5
  }
}
```

#### 4.4.2 ENGINE_REGISTER_ACK（注册确认）

**发送方向**：Admin → Engine  
**触发时机**：Engine注册成功后  
**用途**：确认注册成功，返回服务器时间

```json
{
  "messageId": "b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7",
  "type": "ENGINE_REGISTER_ACK",
  "engineId": "engine-001",
  "timestamp": 1703232001000,
  "payload": {
    "message": "注册成功",
    "serverTime": 1703232001000
  }
}
```

#### 4.4.3 HEARTBEAT_PING / HEARTBEAT_PONG（心跳）

**HEARTBEAT_PING**（Engine → Admin）：

```json
{
  "messageId": "c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8",
  "type": "HEARTBEAT_PING",
  "engineId": "engine-001",
  "timestamp": 1703232030000
}
```

**HEARTBEAT_PONG**（Admin → Engine）：

```json
{
  "messageId": "d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9",
  "type": "HEARTBEAT_PONG",
  "timestamp": 1703232030100,
  "payload": {
    "serverTime": 1703232030100
  }
}
```

#### 4.4.4 TASK_REQUEST（任务请求）

**发送方向**：Admin → Engine  
**用途**：分配任务给Engine执行

```json
{
  "messageId": "e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0",
  "type": "TASK_REQUEST",
  "engineId": "engine-001",
  "userId": "user-123",
  "traceId": "trace-abc-123",
  "timestamp": 1703232100000,
  "payload": {
    "taskId": "task-20231222-001",
    "taskType": "browser_automation",
    "priority": "high",
    "timeout": 300000,
    "params": {
      "url": "https://example.com",
      "actions": [
        {"type": "click", "selector": "#button1"},
        {"type": "input", "selector": "#input1", "value": "test"}
      ]
    }
  }
}
```

#### 4.4.5 TASK_RESULT（任务结果）

**发送方向**：Engine → Admin  
**用途**：返回任务执行结果

```json
{
  "messageId": "f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1",
  "type": "TASK_RESULT",
  "engineId": "engine-001",
  "userId": "user-123",
  "traceId": "trace-abc-123",
  "timestamp": 1703232150000,
  "payload": {
    "taskId": "task-20231222-001",
    "status": "success",
    "duration": 45230,
    "result": {
      "screenshot": "base64_encoded_image_data",
      "extractedData": {
        "title": "Example Domain"
      }
    },
    "error": null
  }
}
```

### 4.5 添加自定义消息类型

```java
// 1. 在 MessageType.java 中添加
public enum MessageType {
    // ... 已有类型
    MY_CUSTOM_TYPE("MY_CUSTOM_TYPE", "我的自定义消息"),
    ;
}

// 2. 在 MessageRouter 中注册处理器
@Component
public class MessageRouter {
    
    @PostConstruct
    public void init() {
        registerHandler(MessageType.MY_CUSTOM_TYPE, this::handleMyCustomMessage);
    }
    
    private void handleMyCustomMessage(EngineMessage message) {
        String data = message.getPayloadValue("data");
        log.info("收到自定义消息: {}", data);
        // 处理逻辑...
    }
}
```

---

## 4.6 前端连接参数

### 前端 WebSocket 连接

```
ws://localhost:8080/ws/client?clientType={clientType}
Header: Authorization: Bearer {token}
```

| 参数 | 位置 | 必填 | 说明 | 示例 |
|------|------|------|------|------|
| `clientType` | URL 参数 | ✅ | 客户端类型 | `web` / `mini` |
| `Authorization` | Header | 非浏览器客户端推荐 | JWT 认证令牌 | `Bearer eyJhbGciOiJIUzUxMiJ9...` |
| `token` / `key` | URL 参数 | 浏览器客户端可用 | JWT 认证令牌的兼容传递方式 | `token=eyJhbGciOi...` |

> ⚠️ **userId 无需传递**，后端自动从 Token 解析，自动生成 `clientId = {clientType}-{userId}`

### Engine 副节点连接

```
ws://localhost:8080/ws/engine
```

> 说明：当前源码中 Engine 连接地址不要求额外查询参数，Engine 的身份信息在注册消息 `ENGINE_REGISTER` 中通过 `engineId` 传递。

| 注册字段 | 必填 | 说明 | 示例 |
|----------|------|------|------|
| `ENGINE_REGISTER.engineId` | ✅ | Engine 节点ID（即白名单 `hostId`） | `engine-001` |
| 白名单 `hostType` | ✅ | Engine 注册专用主机类型 | `engine` |

> Engine 连接通过**白名单验证**，不需要 Token

### 认证规则

| 端点 | 认证方式 | 说明 |
|------|----------|------|
| `/ws/client` | Token 认证 | 前端必须携带有效 JWT Token |
| `/ws/engine` | 白名单认证 | Engine 通过 hostId 白名单验证 |
| `/ws/admin/**`、`/ws/engine/request`、`/ws/engine/list` | JWT 认证 | 仅 WebSocket 握手路径匿名放行，HTTP 管理/执行接口需要 JWT |

### 错误码

| 错误码 | 说明 |
|--------|------|
| `ENGINE_NOT_SPECIFIED` | 未指定 engineId（请求时必须指定） |
| `ENGINE_OFFLINE` | 指定的 Engine 不在线 |
| `HANDLER_NOT_FOUND` | 未找到消息处理器 |
| `PARSE_ERROR` | 消息解析失败 |
| `AUTH_FAILED` | Token 认证失败 |

---

## 4. Admin端实现

### 5.1 发送消息给指定 Engine

```java
@Service
public class TaskDispatchService {

    @Autowired
    private EngineSessionManager sessionManager;

    // 通过 engineId 发送
    public void sendToEngine(String engineId, Object data) {
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.TASK_ASSIGN)
            .engineId(engineId)
            .payload("taskId", "task-001")
            .payload("taskType", "browser_automation")
            .payload("data", data)
            .build();
        
        boolean success = sessionManager.sendMessage(engineId, message);
        if (!success) {
            log.warn("发送失败，Engine可能不在线: {}", engineId);
        }
    }

    // 通过 session 发送
    public void sendToSession(EngineSession session, Object data) {
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.AI_CHAT_REQUEST)
            .payload("requestId", UUID.randomUUID().toString())
            .payload("prompt", "你好")
            .payload("config", Map.of("model", "gpt-4"))
            .build();
        
        sessionManager.sendMessage(session, message);
    }
}
```

### 5.2 广播消息

```java
public void broadcastToAllEngines(String announcement) {
    EngineMessage message = EngineMessage.builder()
        .type(MessageType.SYSTEM_BROADCAST)
        .payload("message", announcement)
        .payload("level", "info")
        .build();
    
    int successCount = sessionManager.broadcast(message);
    log.info("广播成功: {}/{}", successCount, sessionManager.getRegisteredSessions().size());
}
```

### 5.3 获取在线 Engine 列表

```java
// 获取所有已注册的会话
List<EngineSession> sessions = sessionManager.getRegisteredSessions();

for (EngineSession session : sessions) {
    System.out.println("EngineID: " + session.getEngineId());
    System.out.println("版本: " + session.getVersion());
    System.out.println("在线时长: " + session.getDurationSeconds() + "秒");
}

// 获取指定 Engine 的会话
EngineSession session = sessionManager.getSessionByEngineId("engine-001");
```

### 5.4 添加自定义消息处理器

```java
@Component
public class EngineMessageRouter {

    @Autowired
    private MyBusinessHandler myBusinessHandler;

    @PostConstruct
    public void init() {
        // 注册自定义处理器
        registerHandler(MessageType.TASK_RESULT, this::handleTaskResult);
        registerHandler(MessageType.AI_CHAT_RESPONSE, myBusinessHandler::handleAiResponse);
    }
    
    private void handleTaskResult(EngineSession session, EngineMessage message) {
        String taskId = message.getPayloadValue("taskId");
        Boolean success = message.getPayloadValue("success");
        Object result = message.getPayloadValue("result");
        
        log.info("收到任务结果 - EngineID: {}, TaskID: {}, Success: {}", 
            session.getEngineId(), taskId, success);
        
        // 处理业务逻辑...
    }
}
```

### 5.5 REST API 接口

```bash
# 获取连接统计（需要 JWT）
GET /ws/admin/stats
# 响应: {"success":true,"data":{"totalConnections":5},"timestamp":1730000000000}

# 获取 Engine 列表（需要 JWT）
GET /ws/admin/engines
# 响应: {"success":true,"total":1,"engines":[{"engineId":"engine-001","version":"1.3.1"}]}

# 发送任务到指定 Engine（需要 JWT）
POST /ws/admin/engines/{engineId}/task
Content-Type: application/json
{"type": "PLAYWRIGHT_TEST", "prompt": "你好"}

# 广播消息（需要 JWT）
POST /ws/admin/broadcast
Content-Type: application/json
{"type": "SYSTEM_NOTICE", "message": "系统维护通知"}
```

---

## 5. Engine端实现

### 6.1 发送消息给主节点

```java
@Service
public class TaskResultService {

    @Autowired
    private WebSocketClientManager clientManager;

    // 发送任务结果
    public void sendTaskResult(String taskId, Object result) {
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.TASK_RESULT)
            .payload("taskId", taskId)
            .payload("success", true)
            .payload("result", result)
            .payload("executionTime", 1500)
            .build();
        
        boolean success = clientManager.sendMessage(message);
        if (!success) {
            log.warn("发送失败，可能未连接主节点");
        }
    }

    // 发送任务进度
    public void sendProgress(String taskId, int progress, String status) {
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.TASK_PROGRESS)
            .payload("taskId", taskId)
            .payload("progress", progress)
            .payload("status", status)
            .build();
        
        clientManager.sendMessage(message);
    }
}
```

### 6.2 添加消息处理器

```java
@Component
public class MessageRouter {

    @Autowired
    private AiChatHandler aiChatHandler;

    @PostConstruct
    public void init() {
        // 注册任务分配处理器
        registerHandler(MessageType.TASK_ASSIGN, this::handleTaskAssign);
        
        // 注册 AI 对话处理器
        registerHandler(MessageType.AI_CHAT_REQUEST, aiChatHandler::handle);
    }
    
    private void handleTaskAssign(EngineMessage message) {
        String taskId = message.getPayloadValue("taskId");
        String taskType = message.getPayloadValue("taskType");
        Map<String, Object> data = message.getPayloadValue("data");
        
        log.info("收到任务: {} - {}", taskId, taskType);
        
        // 执行任务...
    }
}
```

### 6.3 检查连接状态

```java
// 检查是否已连接
boolean connected = clientManager.isConnected();

// 获取详细状态
EngineWebSocketClient.ConnectionStatus status = clientManager.getConnectionStatus();
System.out.println("已连接: " + status.connected());
System.out.println("重连中: " + status.reconnecting());
System.out.println("重连次数: " + status.reconnectCount());
System.out.println("最后消息时间: " + status.lastMessageTime());
System.out.println("最后心跳时间: " + status.lastHeartbeatTime());
```

### 6.4 健康检查端点

```bash
# 基础健康检查
GET /health
# 响应: {"status": "UP", "timestamp": 1702700000000}

# 就绪检查
GET /health/ready
# 响应: {"status": "UP", "websocket": "CONNECTED", "taskExecutor": {...}}

# 详细状态
GET /health/detail
# 响应: 包含 WebSocket状态、任务执行器状态、资源监控状态
```

---

## 6. 配置说明

### 7.1 主节点配置 (application.yml)

```yaml
fbsir:
  websocket:
    enabled: true                      # 是否启用
    path: /ws/engine                   # WebSocket 端点路径
    max-connections: 10000             # 最大连接数
    max-message-size: 5242880          # 最大消息大小（5MB）
    heartbeat-interval: 30             # 心跳间隔（秒）
    heartbeat-timeout: 10              # 心跳超时（秒）
    session-cleanup-interval: 60       # 会话清理间隔（秒）
```

### 7.2 副节点配置 (application.yml)

```yaml
fbsir:
  engine:
    # 主机ID（必须申请白名单）
    host-id: engine-001
    version: 1.0.0
    
    # 主节点连接配置
    admin:
      ws-url: ws://localhost:8080/ws/engine
    
    # 连接配置
    connection:
      heartbeat-interval: 30           # 心跳间隔（秒）
      heartbeat-timeout: 10            # 心跳超时（秒）
      max-message-size: 5242880        # 最大消息大小（5MB）
    
    # 重连配置
    reconnect:
      enabled: true                    # 是否启用重连
      max-retries: 12                  # 最大重试次数（-1=无限）
      initial-delay: 5                 # 初始延迟（秒）
      max-delay: 30                    # 最大延迟（秒）
      backoff-multiplier: 1.5          # 退避因子
```

### 7.3 配置参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `host-id` | - | **必填**，需向管理员申请白名单 |
| `ws-url` | - | 主节点 WebSocket 地址 |
| `max-message-size` | 5MB | 单条消息最大大小 |
| `heartbeat-interval` | 30s | 心跳发送间隔 |
| `heartbeat-timeout` | 10s | 心跳超时时间 |
| `initial-delay` | 5s | 重连初始延迟 |
| `max-delay` | 30s | 重连最大延迟 |
| `backoff-multiplier` | 1.5 | 重连退避因子 |

---

## 3. 通信流程详解

### 8.1 完整通信时序图

```
┌─────────┐                           ┌─────────┐                           ┌─────────┐
│  用户    │                           │  Admin  │                           │ Engine  │
└────┬────┘                           └────┬────┘                           └────┬────┘
     │                                     │                                     │
     │                                     │    [Engine 启动]                    │
     │                                     │◄────────────────────────────────────│ WebSocket 连接
     │                                     │                                     │
     │                                     │◄──── ENGINE_REGISTER ──────────────│
     │                                     │      {hostId, version, caps}        │
     │                                     │                                     │
     │                                     │──── ENGINE_REGISTER_ACK ──────────►│
     │                                     │      {status: "accepted"}           │
     │                                     │                                     │
     │                                     │◄──── HEARTBEAT_PING ───────────────│ [每30秒]
     │                                     │──── HEARTBEAT_PONG ────────────────►│
     │                                     │                                     │
     │──── HTTP: POST /ai/chat ───────────►│                                     │
     │      {userId, prompt}               │                                     │
     │                                     │                                     │
     │                                     │──── AI_CHAT_REQUEST ──────────────►│
     │                                     │      {requestId, userId, prompt}    │
     │                                     │                                     │
     │                                     │                   [Playwright 执行] │
     │                                     │                                     │
     │                                     │◄──── TASK_PROGRESS ────────────────│
     │                                     │      {progress: 50%}                │
     │                                     │                                     │
     │◄──── WebSocket 推送 ────────────────│                                     │
     │      {progress: 50%}                │                                     │
     │                                     │                                     │
     │                                     │◄──── AI_CHAT_RESPONSE ─────────────│
     │                                     │      {requestId, response}          │
     │                                     │                                     │
     │◄──── WebSocket 推送 ────────────────│                                     │
     │      {response}                     │                                     │
     │                                     │                                     │
```

### 8.2 错误处理流程

```
Engine 连接主节点
     │
     ▼
┌─────────────────┐
│  WebSocket 握手  │
└────────┬────────┘
         │
         ▼
    ┌────────────┐
    │ 握手成功？  │
    └────┬───────┘
         │
    ┌────┴────┐
    │         │
   Yes       No
    │         │
    ▼         ▼
发送注册    检查错误码
    │         │
    ▼    ┌────┴────┐
收到ACK? │  403/429 │ → 致命错误，终止程序
    │    │   其他   │ → 等待重连
    │    └─────────┘
┌───┴───┐
│       │
Yes    No
│       │
▼       ▼
正常运行 检查错误码
        │
   ┌────┴────┐
   │  4007   │ → 不在白名单，终止程序
   │  4008   │ → 重复连接，终止程序
   │  其他   │ → 等待重连
   └─────────┘
```

### 8.3 重连流程

```
连接断开
     │
     ▼
┌─────────────────┐
│ 是否致命错误？   │ ─── Yes ──► 终止程序
└────────┬────────┘
         │ No
         ▼
┌─────────────────┐
│ 重连次数 < 最大？│ ─── No ──► 终止程序
└────────┬────────┘
         │ Yes
         ▼
┌─────────────────┐
│ 计算重连延迟    │
│ delay = min(    │
│   initial * 1.5^n,│
│   maxDelay      │
│ )               │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   等待 delay    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   尝试重连      │
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
  成功       失败
    │         │
    ▼         ▼
正常运行   回到检查
```

---

## 8. 业务示例

### 9.1 AI 对话完整流程

#### Admin 端 - 发起请求

```java
@RestController
@RequestMapping("/api/ai")
public class AIChatController {

    @Autowired
    private EngineSessionManager sessionManager;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody ChatRequest request) {
        // 1. 选择一个可用的 Engine
        List<EngineSession> engines = sessionManager.getRegisteredSessions();
        if (engines.isEmpty()) {
            return Result.error("没有可用的 Engine 节点");
        }
        EngineSession engine = engines.get(0);
        
        // 2. 生成请求ID（用于关联响应）
        String requestId = UUID.randomUUID().toString();
        
        // 3. 构建消息
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.AI_CHAT_REQUEST)
            .userId(request.getUserId())
            .payload("requestId", requestId)
            .payload("prompt", request.getPrompt())
            .payload("conversationId", request.getConversationId())
            .payload("config", Map.of(
                "model", "gpt-4",
                "temperature", 0.7,
                "stream", true
            ))
            .build();
        
        // 4. 发送给 Engine
        boolean success = sessionManager.sendMessage(engine, message);
        
        if (success) {
            return Result.success(requestId);
        } else {
            return Result.error("发送失败");
        }
    }
}
```

#### Engine 端 - 处理请求

```java
@Component
public class AIChatHandler {

    @Autowired
    private WebSocketClientManager clientManager;
    
    @Autowired
    private PlaywrightTaskExecutor taskExecutor;

    public void handle(EngineMessage message) {
        String requestId = message.getPayloadValue("requestId");
        String userId = message.getUserId();
        String prompt = message.getPayloadValue("prompt");
        Map<String, Object> config = message.getPayloadValue("config");
        
        // 异步执行 Playwright 任务
        taskExecutor.executeAsync(userId, "ai-chat", session -> {
            try {
                Page page = session.getOrCreatePage();
                
                // 导航到 AI 平台
                page.navigate("https://ai.example.com/");
                
                // 发送进度
                sendProgress(requestId, 20, "正在加载页面...");
                
                // 输入提示词
                page.locator("#prompt-input").fill(prompt);
                page.locator("#submit-btn").click();
                
                sendProgress(requestId, 50, "等待 AI 响应...");
                
                // 等待响应
                page.locator(".response-content").waitFor();
                String response = page.locator(".response-content").textContent();
                
                sendProgress(requestId, 100, "完成");
                
                // 发送结果
                sendResponse(requestId, userId, response, true);
                
                return response;
            } catch (Exception e) {
                sendResponse(requestId, userId, e.getMessage(), false);
                throw e;
            }
        });
    }
    
    private void sendProgress(String requestId, int progress, String status) {
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.TASK_PROGRESS)
            .payload("requestId", requestId)
            .payload("progress", progress)
            .payload("status", status)
            .build();
        clientManager.sendMessage(message);
    }
    
    private void sendResponse(String requestId, String userId, String content, boolean success) {
        EngineMessage message = EngineMessage.builder()
            .type(MessageType.AI_CHAT_RESPONSE)
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", success)
            .payload("response", content)
            .build();
        clientManager.sendMessage(message);
    }
}
```

### 9.2 批量任务分发

```java
@Service
public class BatchTaskService {

    @Autowired
    private EngineSessionManager sessionManager;

    public void distributeTasksToEngines(List<TaskRequest> tasks) {
        List<EngineSession> engines = sessionManager.getRegisteredSessions();
        if (engines.isEmpty()) {
            throw new RuntimeException("没有可用的 Engine");
        }
        
        // 轮询分发任务
        for (int i = 0; i < tasks.size(); i++) {
            TaskRequest task = tasks.get(i);
            EngineSession engine = engines.get(i % engines.size());
            
            EngineMessage message = EngineMessage.builder()
                .type(MessageType.TASK_ASSIGN)
                .engineId(engine.getEngineId())
                .payload("taskId", task.getId())
                .payload("taskType", task.getType())
                .payload("data", task.getData())
                .build();
            
            sessionManager.sendMessage(engine, message);
        }
    }
}
```

---

## 9. 故障排除

### 10.1 连接问题

#### Engine 无法连接主节点

**排查步骤**：
1. 检查主节点是否启动：`curl http://localhost:8080/actuator/health`
2. 检查 WebSocket 端点路径：确认 `/ws/engine` 配置正确
3. 检查防火墙/网络设置
4. 检查日志中的详细错误信息

#### 连接被拒绝 (4007)

**原因**：主机ID不在白名单  
**解决**：联系管理员将主机ID添加到 `ws_host_whitelist` 表

#### 重复连接被拒绝 (4008)

**原因**：同一主机ID已有连接  
**解决**：
1. 检查是否有重复启动的进程
2. 等待旧连接超时后重试
3. 使用不同的主机ID

#### 频繁断连重连

**排查步骤**：
1. 检查网络稳定性
2. 增大 `heartbeat-interval` 和 `heartbeat-timeout`
3. 检查主节点服务器负载
4. 查看日志中的断连原因

### 10.2 消息问题

#### 消息发送失败

**排查步骤**：
1. 检查 Engine 是否已注册：调用 `GET /ws/admin/engines`
2. 检查连接状态：`clientManager.isConnected()`
3. 检查消息格式是否正确
4. 查看日志中的错误信息

#### 消息处理异常

**排查步骤**：
1. 检查消息类型是否已注册处理器
2. 查看处理器日志
3. 检查 payload 数据格式

### 10.3 性能问题

#### 连接数过多

**解决方案**：
1. 增加 `max-connections` 配置
2. 考虑增加主节点实例
3. 实施负载均衡

#### 消息积压

**解决方案**：
1. 增加消息处理线程
2. 优化处理器逻辑
3. 实施背压控制

---

## 10. API快速参考

### 主节点 (EngineSessionManager)

| 方法 | 说明 |
|------|------|
| `sendMessage(engineId, message)` | 发送消息给指定 Engine |
| `sendMessage(session, message)` | 发送消息给指定会话 |
| `broadcast(message)` | 广播消息给所有 Engine |
| `getSession(sessionId)` | 获取会话 |
| `getSessionByEngineId(engineId)` | 通过 engineId 获取会话 |
| `getRegisteredSessions()` | 获取所有已注册会话 |
| `getStats()` | 获取连接统计 |

### 副节点 (WebSocketClientManager)

| 方法 | 说明 |
|------|------|
| `sendMessage(message)` | 发送消息给主节点 |
| `isConnected()` | 检查连接状态 |
| `getConnectionStatus()` | 获取详细连接状态 |
| `reconnect()` | 手动重连 |
| `disconnect()` | 断开连接 |

### 消息构建 (EngineMessage.builder())

| 方法 | 说明 |
|------|------|
| `.type(MessageType)` | 设置消息类型 |
| `.engineId(String)` | 设置主机ID |
| `.userId(String)` | 设置用户ID |
| `.payload(key, value)` | 添加 payload 字段 |
| `.payload(Map)` | 批量添加 payload |
| `.build()` | 构建消息对象 |

### 消息读取 (EngineMessage)

| 方法 | 说明 |
|------|------|
| `.getType()` | 获取消息类型 |
| `.getEngineId()` | 获取主机ID |
| `.getUserId()` | 获取用户ID |
| `.getPayloadValue(key)` | 获取 payload 字段值 |
| `.getPayload()` | 获取完整 payload |

---

## 十二、流式消息与中间状态回传

### 12.1 概述

流式消息（Stream）用于需要在处理过程中多次回传状态的场景，如：
- 获取二维码 → 检测登录 → 返回结果
- AI 对话 → 思考中 → 回答中 → 完成
- 内容投递 → 上传中 → 发布中 → 完成

### 12.2 CapabilityRegistry 配置

```java
// CapabilityRegistry.java

// stream() - 流式消息，处理过程中会多次回传状态
stream("PLAY_GET_DB_QRCODE", "获取豆包二维码", services.ai.doubao::getQrCode);

// once() - 单次消息，只返回最终结果
once("CHECK_DB_LOGIN", "检查豆包登录", services.ai.doubao::checkLogin);
```

### 12.3 Handler 中使用 WebSocketSender

```java
@Component
public class DoubaoHandler extends BaseAiHandler {

    @Autowired
    private WebSocketSender wsSender;

    public void getQrCode(EngineMessage message) {
        String userId = message.getUserId();
        
        try {
            // 1. 创建浏览器会话
            BrowserContext context = browserPool.getOrCreate(userId, "doubao");
            Page page = context.newPage();
            page.navigate("https://www.doubao.com/");
            
            // 2. 截取二维码并发送（第1次响应）
            String qrUrl = screenshotUtil.captureQrCode(page);
            wsSender.sendQrCode(userId, "RETURN_PC_DB_QRURL", qrUrl);
            
            // 3. 循环检测登录状态（最多60秒）
            for (int i = 0; i < 30; i++) {
                Thread.sleep(2000);
                
                // 二维码过期，重新发送（第N次响应）
                if (isQrCodeExpired(page)) {
                    String newQrUrl = screenshotUtil.captureQrCode(page);
                    wsSender.sendQrCode(userId, "RETURN_PC_DB_QRURL", newQrUrl);
                }
                
                // 登录成功（最终响应）
                if (isLoggedIn(page)) {
                    String username = extractUsername(page);
                    wsSender.sendStatus(userId, "RETURN_DB_STATUS", username);
                    return;
                }
            }
            
            // 4. 超时
            wsSender.sendTimeout(userId, "RETURN_DB_LOGIN_TIMEOUT");
            
        } catch (Exception e) {
            wsSender.sendError(userId, "RETURN_DB_STATUS", e.getMessage());
        }
    }
}
```

### 12.4 WebSocketSender API

| 方法 | 说明 | 示例 |
|------|------|------|
| `sendQrCode(userId, type, url)` | 发送二维码 | `sendQrCode(userId, "RETURN_PC_DB_QRURL", qrUrl)` |
| `sendStatus(userId, type, status)` | 发送状态 | `sendStatus(userId, "RETURN_DB_STATUS", "true")` |
| `sendProgress(userId, type, progress, msg)` | 发送进度 | `sendProgress(userId, "TASK_PROGRESS", 50, "处理中")` |
| `sendSuccess(userId, type, data)` | 发送成功结果 | `sendSuccess(userId, "TASK_RESULT", result)` |
| `sendError(userId, type, error)` | 发送错误 | `sendError(userId, "TASK_ERROR", "失败原因")` |
| `sendTimeout(userId, type)` | 发送超时 | `sendTimeout(userId, "RETURN_DB_LOGIN_TIMEOUT")` |
| `sendImage(userId, type, url)` | 发送截图 | `sendImage(userId, "TASK_SCREENSHOT", imageUrl)` |

### 12.5 前端处理流式消息

```javascript
// 浏览器原生 WebSocket 不能设置 Authorization Header，当前前端使用兼容的查询参数方式
const ws = new WebSocket(
  'wss://admin.example.com/ws/client?clientType=web&token=' + encodeURIComponent(token)
);

// 监听消息
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  
  switch (data.type) {
    // 二维码（可能收到多次）
    case 'RETURN_PC_DB_QRURL':
      showQrCode(data.payload.url);
      break;
      
    // 登录成功
    case 'RETURN_DB_STATUS':
      if (data.payload.status !== 'false') {
        showLoginSuccess(data.payload.status);
        closeQrDialog();
      }
      break;
      
    // 登录超时
    case 'RETURN_DB_LOGIN_TIMEOUT':
      showTimeout();
      break;
      
    // 进度更新
    case 'TASK_PROGRESS':
      updateProgress(data.payload.progress, data.payload.message);
      break;
  }
};

// 发送请求
function getDoubaoQrCode() {
  ws.send(JSON.stringify({
    type: 'PLAY_GET_DB_QRCODE',
    userId: userId,
    timestamp: Date.now()
  }));
}
```

### 12.6 消息类型约定

| 请求类型 | 中间响应 | 最终响应 |
|---------|---------|---------|
| `PLAY_GET_DB_QRCODE` | `RETURN_PC_DB_QRURL` (多次) | `RETURN_DB_STATUS` / `RETURN_DB_LOGIN_TIMEOUT` |
| `PLAY_GET_DS_QRCODE` | `RETURN_PC_DS_QRURL` (多次) | `RETURN_DEEPSEEK_STATUS` |
| `AI智能对话` | `AI_THINKING` / `AI_PROGRESS` | `AI_RESPONSE` |
| `媒体投递` | `MEDIA_PROGRESS` (多次) | `MEDIA_RESULT` |

---

## 附录：大消息传输说明

### 消息大小限制

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `max-message-size` | **5MB** | 单条消息最大大小 |
| 建议最大值 | 10MB | 超过10MB建议使用HTTP或分片 |

### 5MB 可以传输的数据量

- 约 **250万个中文字符**
- 约 **500万个英文字符**
- 足够传输：AI对话结果、长文章、大型JSON数据等

### 调整消息大小限制

如需调整，需同时修改主节点和副节点配置：

```yaml
# 主节点
fbsir:
  websocket:
    max-message-size: 10485760  # 10MB

# 副节点
fbsir:
  engine:
    connection:
      max-message-size: 10485760  # 10MB
```

> ⚠️ 增大消息限制会增加内存消耗，建议不超过10MB。超大数据建议使用HTTP接口传输。

---

## 11. Payload处理指南

### 11.1 消息体结构

所有业务消息使用统一的 `EngineMessage` 格式，其中 `payload` 字段用于承载业务数据：

```json
{
  "type": "YOUR_MESSAGE_TYPE",
  "engineId": "engine-001",
  "payload": {
    "requestId": "自动生成",
    "userId": "自动添加",
    "sourceType": "自动添加",
    "sourceClientId": "自动添加",
    "你的业务字段1": "值1",
    "你的业务字段2": {...},
    "你的业务字段3": [...]
  }
}
```

### 11.2 Admin透明转发机制

**核心原则**：Admin主节点**不处理、不解析、不修改** payload中的业务数据，完全透传给Engine。

**Admin自动添加的字段**（仅这4个）：
- `requestId`: 后端生成的唯一请求ID
- `userId`: 从Token解析的用户ID
- `sourceType`: 请求来源类型（WEBSOCKET/HTTP）
- `sourceClientId`: 客户端ID

**你可以在payload中放置**：
- ✅ 任意复杂的嵌套对象（Map）
- ✅ 任意数组/列表（List）
- ✅ 基础类型（String, Number, Boolean）
- ✅ 任意深度的嵌套结构

### 11.3 Engine端提取Payload数据

#### 示例1：提取基础类型

```java
@OnceCapability(type = "SIMPLE_TASK", description = "简单任务")
public void handleSimpleTask(EngineMessage request) {
    // 提取String
    String taskName = request.getPayloadValue("taskName");
    
    // 提取Integer
    Integer priority = request.getPayloadValue("priority");
    
    // 提取Boolean
    Boolean enabled = request.getPayloadValue("enabled");
    
    log.info("任务: {}, 优先级: {}, 启用: {}", taskName, priority, enabled);
}
```

**客户端发送**：
```json
{"type":"SIMPLE_TASK","engineId":"engine-001","payload":{"taskName":"测试任务","priority":1,"enabled":true}}
```

#### 示例2：提取嵌套对象

```java
@OnceCapability(type = "NESTED_TASK", description = "嵌套对象")
public void handleNestedTask(EngineMessage request) {
    // 提取嵌套Map
    @SuppressWarnings("unchecked")
    Map<String, Object> user = (Map<String, Object>) request.getPayloadValue("user");
    
    if (user != null) {
        String userId = (String) user.get("id");
        String userName = (String) user.get("name");
        
        // 提取更深层的嵌套
        @SuppressWarnings("unchecked")
        Map<String, Object> settings = (Map<String, Object>) user.get("settings");
        if (settings != null) {
            String theme = (String) settings.get("theme");
            log.info("用户: {}, 主题: {}", userName, theme);
        }
    }
}
```

**客户端发送**：
```json
{"type":"NESTED_TASK","engineId":"engine-001","payload":{"user":{"id":"user123","name":"张三","settings":{"theme":"dark","language":"zh-CN"}}}}
```

#### 示例3：提取数组/列表

```java
@OnceCapability(type = "BATCH_TASK", description = "批量任务")
public void handleBatchTask(EngineMessage request) {
    // 提取List
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> items = (List<Map<String, Object>>) 
        request.getPayloadValue("items");
    
    if (items != null) {
        for (Map<String, Object> item : items) {
            Integer id = (Integer) item.get("id");
            String name = (String) item.get("name");
            log.info("处理项目: {} - {}", id, name);
        }
    }
}
```

**客户端发送**：
```json
{"type":"BATCH_TASK","engineId":"engine-001","payload":{"items":[{"id":1,"name":"Item 1"},{"id":2,"name":"Item 2"}]}}
```

#### 示例4：复杂混合结构

```java
@OnceCapability(type = "COMPLEX_TASK", description = "复杂结构")
public void handleComplexTask(EngineMessage request) {
    // 提取配置对象
    @SuppressWarnings("unchecked")
    Map<String, Object> config = (Map<String, Object>) 
        request.getPayloadValue("config");
    
    // 提取过滤器列表
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> filters = (List<Map<String, Object>>) 
        request.getPayloadValue("filters");
    
    // 提取元数据中的标签数组
    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) 
        request.getPayloadValue("metadata");
    if (metadata != null) {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) metadata.get("tags");
        log.info("标签: {}", String.join(", ", tags));
    }
}
```

**客户端发送**：
```json
{"type":"COMPLEX_TASK","engineId":"engine-001","payload":{"config":{"timeout":30000,"retry":true},"filters":[{"field":"status","value":"active"}],"metadata":{"tags":["tag1","tag2"]}}}
```

### 11.4 完整的能力实现模板

```java
@Component
@Controller
public class MyCapability {

    private static final Logger log = LoggerFactory.getLogger(MyCapability.class);
    private final WebSocketSender webSocketSender;

    public MyCapability(WebSocketSender webSocketSender) {
        this.webSocketSender = webSocketSender;
    }

    @OnceCapability(type = "MY_TASK", description = "我的任务")
    public void handleMyTask(EngineMessage request) {
        try {
            // 1. 提取payload数据
            String param1 = request.getPayloadValue("param1");
            
            @SuppressWarnings("unchecked")
            Map<String, Object> param2 = (Map<String, Object>) 
                request.getPayloadValue("param2");
            
            // 2. 处理业务逻辑
            Object result = processBusinessLogic(param1, param2);
            
            // 3. 构建响应
            EngineMessage response = EngineMessage.builder()
                .type("MY_TASK_RESULT")
                .userId(request.getUserId())
                .engineId(request.getEngineId())
                .payload("success", true)
                .payload("result", result)
                .build();
            
            // 4. 发送响应
            webSocketSender.send(response);
            
        } catch (Exception e) {
            log.error("[MyCapability] 处理失败", e);
            sendError(request, "处理失败: " + e.getMessage());
        }
    }
    
    private void sendError(EngineMessage request, String errorMessage) {
        EngineMessage response = EngineMessage.builder()
            .type(request.getType() + "_RESULT")
            .userId(request.getUserId())
            .engineId(request.getEngineId())
            .payload("success", false)
            .payload("error", errorMessage)
            .build();
        
        webSocketSender.send(response);
    }
    
    private Object processBusinessLogic(String param1, Map<String, Object> param2) {
        // 你的业务逻辑
        return "result";
    }
}
```

### 11.5 WebSocket测试命令

使用 `websocat` 工具测试（一行JSON格式）：

```bash
# 简单测试（token 查询参数是服务端兼容模式）
echo '{"type":"YOUR_TYPE","engineId":"engine-001","payload":{"param1":"value1"}}' | websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}"

# 复杂嵌套测试
echo '{"type":"YOUR_TYPE","engineId":"engine-001","payload":{"user":{"id":"123","name":"张三"},"items":[{"id":1}]}}' | websocat "ws://localhost:8080/ws/client?clientType=web&token=${TOKEN}"
```

### 11.6 最佳实践

#### ✅ 推荐做法

1. **使用类型安全的提取**
```java
@SuppressWarnings("unchecked")
Map<String, Object> data = (Map<String, Object>) request.getPayloadValue("data");
```

2. **空值检查**
```java
if (data != null && !data.isEmpty()) {
    // 处理数据
}
```

3. **异常处理**
```java
try {
    // 业务逻辑
} catch (Exception e) {
    log.error("处理失败", e);
    sendError(request, e.getMessage());
}
```

4. **日志记录关键信息**
```java
String requestId = request.getPayloadValue("requestId");
log.info("[{}] 开始处理", requestId);
```

#### ❌ 避免的做法

1. **不要在Admin修改payload**
   - Admin只负责转发，不要添加业务字段

2. **不要硬编码字段名**
```java
// 不好
String value = (String) payload.get("field1");

// 好
String value = request.getPayloadValue("field1");
```

3. **不要忽略类型转换异常**
```java
// 不好
Integer value = (Integer) request.getPayloadValue("count");

// 好
try {
    Integer value = (Integer) request.getPayloadValue("count");
} catch (ClassCastException e) {
    log.error("类型转换失败", e);
}
```

### 11.7 完整示例代码

参考文件：`FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/PayloadExampleController.java`

该文件包含了所有常见payload处理场景的完整示例代码。

### 11.8 代码组织结构

Engine端代码组织遵循以下原则：

**`capability/` 包** - 能力框架核心
- `capability/annotation/` - 能力注解定义（`@OnceCapability`, `@StreamCapability`）
- `capability/base/` - 基础工具类（`StreamTaskHelper` - 流式任务辅助工具）
- `CapabilityRegistry.java` - 能力注册器
- `EngineCapabilityManager.java` - 能力管理器

**`controller/` 包** - 业务能力实现
- `controller/demo/` - 演示能力（完整示例）
  - `BaiduHotSearchDemoController.java` - 百度热搜抓取演示（流式输出完整示例）
  - `SimpleHealthCheckDemoController.java` - 简单健康检查演示（单次输出完整示例）
  - `README.md` - 演示能力使用指南
- `PayloadExampleController.java` - Payload处理示例
- `HealthCheckController.java` - 健康检查能力（单次返回）

**设计原则**：
- ✅ `capability/` 存放框架代码（注解、工具类、管理器）
- ✅ `controller/` 存放业务能力实现（所有业务Controller）
- ✅ 所有业务能力类使用 `@Controller` + `@OnceCapability` 或 `@StreamCapability` 注解
- ✅ 流式任务可选择继承 `StreamTaskHelper` 获得流式能力（非必须）
- ✅ 简单任务（如 `HealthCheckController`）直接注入 `WebSocketClientManager` 即可

---

## 12. 流式输出 vs 单次输出完整指南

### 12.1 概念对比

| 特性 | 单次输出 | 流式输出 |
|------|---------|---------|
| **基类** | 不继承 | 继承 `StreamTaskHelper` |
| **注解** | `@OnceCapability` | `@StreamCapability` |
| **注入** | `WebSocketClientManager` | 自动注入（继承获得） |
| **消息数量** | 1次 TASK_RESULT | 多次 TASK_LOG/TASK_SCREENSHOT + 1次 TASK_RESULT |
| **适用场景** | 快速返回（<5秒） | 长时间运行（>5秒） |
| **进度推送** | ❌ 不支持 | ✅ 支持 |
| **截图推送** | ❌ 不支持 | ✅ 支持 |
| **心跳机制** | ❌ 不支持 | ✅ 可选支持 |
| **资源管理** | 简单 | 需要在 finally 中调用 `task.stop()` |

### 12.2 消息类型说明

#### 12.2.1 TASK_LOG - 任务日志

**用途**: 推送任务执行进度的文本日志

**发送时机**: 任务执行中（多次）

**前端显示**: 显示在 progressLogs 数组中

**消息格式**:
```json
{
  "type": "TASK_LOG",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "message": "正在打开百度首页...",
    "timestamp": 1766989918872
  }
}
```

**使用示例**:
```java
task.sendLog("正在启动浏览器...");
task.sendLog("浏览器启动成功");
task.sendLog("正在打开百度首页...");
```

#### 12.2.2 TASK_SCREENSHOT - 任务截图

**用途**: 推送截图URL

**发送时机**: 任务执行中（多次）

**前端显示**: 显示在 screenshots 轮播区

**消息格式**:
```json
{
  "type": "TASK_SCREENSHOT",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "screenshotUrl": "http://localhost:8080/profile/engine/1/2025/12/29/screenshot.png",
    "timestamp": 1766989926651
  }
}
```

**使用示例**:
```java
// 截图并上传
Path screenshotPath = screenshotUtil.capture(page, "screenshot_name");
byte[] imageBytes = Files.readAllBytes(screenshotPath);
ScreenshotUploadClient.UploadResult result = uploadClient.uploadScreenshot(userId, "file_name", imageBytes);

// 推送截图URL
task.sendScreenshot(result.getUrl());
```

#### 12.2.3 TASK_PROGRESS - 任务进度（已废弃）

**状态**: ⚠️ 已废弃，请使用 TASK_LOG

**原因**: TASK_LOG 更灵活，可以携带任意文本信息

#### 12.2.4 TASK_RESULT - 任务结果

**用途**: 推送最终结果（成功或失败）

**发送时机**: 任务结束时（1次）

**前端显示**: 根据 success 字段显示成功或失败

**成功消息格式**:
```json
{
  "type": "TASK_RESULT",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "success": true,
    "message": "任务完成",
    "data": {
      // 业务数据
    },
    "timestamp": 1766989926652
  }
}
```

**失败消息格式**:
```json
{
  "type": "TASK_RESULT",
  "userId": "1",
  "payload": {
    "requestId": "xxx",
    "success": false,
    "errorCode": "TASK_ERROR",
    "errorMessage": "任务失败: xxx",
    "timestamp": 1766989926652
  }
}
```

### 12.3 单次输出完整示例

**适用场景**: 数据查询、状态检查、参数验证等快速返回的任务

**示例**: SimpleHealthCheckDemoController

```java
@Controller
public class SimpleHealthCheckDemoController {
    
    @Autowired
    @Lazy
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
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
    }
    
    private void sendErrorResult(String userId, String requestId, String errorMessage) {
        EngineMessage result = EngineMessage.builder()
            .type("TASK_RESULT")
            .userId(userId)
            .payload("requestId", requestId)
            .payload("success", false)
            .payload("errorCode", "TASK_ERROR")
            .payload("errorMessage", errorMessage)
            .payload("timestamp", System.currentTimeMillis())
            .build();
        
        webSocketClientManager.sendMessage(result);
    }
}
```

**客户端调用**:
```json
{"type": "SIMPLE_HEALTH_CHECK_DEMO", "engineId": "engine-001", "payload": {"includeDetails": true}}
```

**返回消息**:
```json
{
  "type": "TASK_RESULT",
  "payload": {
    "requestId": "1_20260710162054_SIMPLE_HEALTH_CHECK_DEMO_001",
    "sourceType": "WEBSOCKET",
    "success": true,
    "data": {
      "status": "healthy",
      "hardware": {...},
      "performance": {...}
    }
  }
}
```

### 12.4 流式输出完整示例

**适用场景**: 爬虫任务、AI对话、文件处理、浏览器自动化等长时间运行的任务

**示例**: BaiduHotSearchDemoController

```java
@Controller
public class BaiduHotSearchDemoController extends StreamTaskHelper {
    
    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    private ScreenshotUtil screenshotUtil;
    
    @Autowired
    private ScreenshotUploadClient uploadClient;
    
    @StreamCapability(
        type = "BAIDU_HOT_SEARCH_DEMO",
        description = "百度热搜抓取演示",
        progressInterval = 3000  // 每3秒自动推送心跳（可选）
    )
    public void handleBaiduHotSearch(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        // 提取业务参数
        Integer clickIndex = message.getPayloadValue("clickIndex");
        if (clickIndex == null) clickIndex = 0;
        
        Boolean needScreenshot = message.getPayloadValue("needScreenshot");
        if (needScreenshot == null) needScreenshot = true;
        
        // 创建流式任务
        StreamTask task = startStreamTask(userId, requestId);
        
        BrowserSession session = null;
        
        try {
            // 推送进度日志
            task.sendLog("正在启动浏览器...");
            
            // 获取浏览器会话
            session = browserPool.acquirePersistent(userId, "baidu_demo", false);
            Page page = session.getOrCreatePage();
            
            task.sendLog("浏览器启动成功");
            task.sendLog("正在打开百度首页...");
            
            // 打开百度首页
            page.navigate("https://www.baidu.com");
            page.waitForLoadState(LoadState.NETWORKIDLE);
            
            task.sendLog("百度首页加载完成");
            task.sendLog("正在抓取热搜榜数据...");
            
            // 抓取热搜数据
            List<Map<String, Object>> hotSearchList = extractHotSearch(page);
            
            task.sendLog(String.format("成功抓取 %d 条热搜数据", hotSearchList.size()));
            
            // 点击指定热搜
            if (clickIndex >= 0 && clickIndex < hotSearchList.size()) {
                Map<String, Object> targetItem = hotSearchList.get(clickIndex);
                String targetTitle = (String) targetItem.get("title");
                
                task.sendLog(String.format("正在点击第 %d 条热搜: %s", clickIndex + 1, targetTitle));
                
                // 点击并等待加载
                clickHotSearch(page, clickIndex);
                
                task.sendLog("页面加载完成");
                
                // 截图并上传
                if (needScreenshot) {
                    task.sendLog("正在截图...");
                    
                    Path screenshotPath = screenshotUtil.capture(page, "baidu_hot_" + clickIndex);
                    
                    task.sendLog("截图完成，正在上传...");
                    
                    byte[] imageBytes = Files.readAllBytes(screenshotPath);
                    ScreenshotUploadClient.UploadResult uploadResult = uploadClient.uploadScreenshot(
                        userId, 
                        "baidu_hot_" + clickIndex, 
                        imageBytes
                    );
                    
                    String screenshotUrl = uploadResult.getUrl();
                    task.sendLog("图片上传成功: " + screenshotUrl);
                    
                    // 推送截图
                    task.sendScreenshot(screenshotUrl);
                    
                    targetItem.put("screenshotUrl", screenshotUrl);
                }
            }
            
            // 构建返回数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("hotSearchList", hotSearchList);
            resultData.put("totalCount", hotSearchList.size());
            resultData.put("timestamp", System.currentTimeMillis());
            
            // 发送最终结果
            task.sendSuccess("热搜抓取完成", resultData);
            
        } catch (Exception e) {
            log.error("[百度热搜演示] 任务失败", e);
            task.sendError("任务执行失败: " + e.getMessage());
            
        } finally {
            // 停止StreamTask
            task.stop();
            
            // 释放浏览器会话
            if (session != null) {
                session.destroy();
            }
        }
    }
}
```

**客户端调用**:
```json
{"type": "BAIDU_HOT_SEARCH_DEMO", "engineId": "engine-001", "payload": {"clickIndex": 0, "needScreenshot": true}}
```

**返回消息流程**:

1. **TASK_LOG** (多次):
```json
{"type": "TASK_LOG", "payload": {"message": "正在启动浏览器..."}}
{"type": "TASK_LOG", "payload": {"message": "浏览器启动成功"}}
{"type": "TASK_LOG", "payload": {"message": "正在打开百度首页..."}}
{"type": "TASK_LOG", "payload": {"message": "百度首页加载完成"}}
{"type": "TASK_LOG", "payload": {"message": "成功抓取 10 条热搜数据"}}
{"type": "TASK_LOG", "payload": {"message": "正在截图..."}}
{"type": "TASK_LOG", "payload": {"message": "图片上传成功: http://..."}}
```

2. **TASK_SCREENSHOT** (可选):
```json
{
  "type": "TASK_SCREENSHOT",
  "payload": {
    "screenshotUrl": "http://localhost:8080/profile/engine/1/2025/12/29/screenshot.png"
  }
}
```

3. **TASK_RESULT** (1次):
```json
{
  "type": "TASK_RESULT",
  "payload": {
    "success": true,
    "message": "热搜抓取完成",
    "data": {
      "hotSearchList": [...],
      "totalCount": 10
    }
  }
}
```

### 12.5 如何选择实现方式

#### 使用单次输出当：

- ✅ 任务执行时间 < 5秒
- ✅ 不需要向前端推送进度
- ✅ 只需要返回最终结果
- ✅ 例如：数据查询、状态检查、参数验证

#### 使用流式输出当：

- ✅ 任务执行时间 > 5秒
- ✅ 需要向前端推送进度日志
- ✅ 需要推送截图或其他中间结果
- ✅ 例如：爬虫任务、AI对话、文件处理、浏览器自动化

### 12.6 完整演示代码

详细的演示代码和使用指南请参考：

- `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/BaiduHotSearchDemoController.java` - 流式输出完整示例
- `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/SimpleHealthCheckDemoController.java` - 单次输出完整示例
- `FBSir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/README.md` - 演示能力使用指南

