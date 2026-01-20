# 🎭 Playwright 框架完整指南

> **目标读者**: 需要使用Playwright实现浏览器自动化任务的Engine端开发者  
> **文档用途**: 从快速入门到深入精通，全面讲解Playwright框架

---

## 📖 如何使用本文档

### 🚀 快速入门（10分钟）

**如果你是新手开发者，想快速使用Playwright**，请阅读：
- [第0章：快速入门 - 10分钟学会Playwright](#0-快速入门---10分钟学会playwright) ⭐⭐⭐

### 🎓 框架精通（深入学习）

**如果你需要深入理解架构或优化性能**，请按顺序阅读：
1. [第1章：框架概述](#1-框架概述) - 理解整体架构
2. [第2章：核心组件详解](#2-核心组件详解) - 浏览器池和会话管理
3. [第5章：资源管理与监控](#5-资源管理与监控) - 防止内存泄漏
4. [第6章：最佳实践](#6-最佳实践) - 开发规范和技巧

### 📚 参考手册（按需查阅）

遇到问题时查阅：
- [第9章：常见问题](#9-常见问题) - 疑难解答
- [6.4 登录信息保存与复用](#64-登录信息保存与复用) - 会话持久化
- [附录A：截图上传完整示例](#附录a截图上传完整示例) - 截图功能

---

## 📑 完整目录

### 第0章：快速入门 ⭐⭐⭐ 新手必读
- [0.1 第一个Playwright任务](#01-第一个playwright任务)
- [0.2 常用操作示例](#02-常用操作示例)
- [0.3 会话管理要点](#03-会话管理要点)
- [0.4 常见错误处理](#04-常见错误处理)

### 第1-9章：框架精通
1. [框架概述](#1-框架概述)
2. [核心组件详解](#2-核心组件详解)
3. [使用指南](#3-使用指南)
4. [配置说明](#4-配置说明)
5. [资源管理与监控](#5-资源管理与监控)
6. [最佳实践](#6-最佳实践)
7. [代码文件结构](#7-代码文件结构)
8. [业务示例](#8-业务示例)
9. [常见问题](#9-常见问题)

### 附录：实用参考
- [附录A：截图上传完整示例](#附录a截图上传完整示例)
- [附录B：完整演示代码](#附录b完整演示代码)

---

## 0. 快速入门 - 10分钟学会Playwright

### 0.1 第一个Playwright任务

#### 最简单的示例（打开网页）

```java
package com.wx.fbsir.engine.controller.business;

import com.microsoft.playwright.Page;
import com.wx.fbsir.engine.capability.annotation.StreamCapability;
import com.wx.fbsir.engine.capability.base.StreamTaskHelper;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

/**
 * 我的第一个Playwright任务
 */
@Controller
public class MyFirstPlaywrightController extends StreamTaskHelper {
    
    @Autowired
    private BrowserPoolManager browserPool;
    
    @StreamCapability(type = "MY_FIRST_BROWSER_TASK", description = "我的第一个浏览器任务")
    public void handleTask(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        StreamTask task = startStreamTask(userId, requestId);
        BrowserSession session = null;
        
        try {
            task.sendLog("正在启动浏览器...");
            
            // 🔥 关键：获取浏览器会话
            session = browserPool.acquirePersistent(userId, "my_task", false);
            Page page = session.getOrCreatePage();
            
            task.sendLog("正在打开网页...");
            
            // 打开网页
            page.navigate("https://www.baidu.com");
            
            // 获取标题
            String title = page.title();
            task.sendLog("页面标题: " + title);
            
            // 返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("title", title);
            result.put("url", page.url());
            
            task.sendSuccess("任务完成", result);
            
        } catch (Exception e) {
            task.sendError("任务失败: " + e.getMessage());
        } finally {
            task.stop();
            
            // 🔥 关键：必须释放会话
            if (session != null) {
                session.destroy();
            }
        }
    }
}
```

**测试命令**:
```bash
{"type": "MY_FIRST_BROWSER_TASK", "engineId": "engine-001", "payload": {}}
```

---

### 0.2 常用操作示例

#### 操作1：点击元素

```java
// 点击按钮
page.click("#submit-button");

// 等待元素出现后点击
page.waitForSelector("#submit-button");
page.click("#submit-button");

// 点击文本内容
page.click("text=登录");
```

#### 操作2：输入文本

```java
// 输入文本
page.fill("#username", "myusername");
page.fill("#password", "mypassword");

// 清空后输入
page.locator("#search").clear();
page.locator("#search").fill("搜索内容");
```

#### 操作3：等待页面加载

```java
// 等待网络空闲
page.waitForLoadState(LoadState.NETWORKIDLE);

// 等待DOM加载完成
page.waitForLoadState(LoadState.DOMCONTENTLOADED);

// 等待特定元素出现
page.waitForSelector("#content");

// 等待指定时间
page.waitForTimeout(2000); // 等待2秒
```

#### 操作4：获取元素内容

```java
// 获取文本内容
String text = page.locator("#title").textContent();

// 获取属性值
String href = page.locator("a").getAttribute("href");

// 获取所有匹配元素
List<String> items = page.locator(".item").allTextContents();
```

#### 操作5：截图

```java
@Autowired
private ScreenshotUtil screenshotUtil;

@Autowired
private ScreenshotUploadClient uploadClient;

// 截图并上传
Path screenshotPath = screenshotUtil.capture(page, "my_screenshot");
byte[] imageBytes = Files.readAllBytes(screenshotPath);

ScreenshotUploadClient.UploadResult result = uploadClient.uploadScreenshot(
    userId, 
    "screenshot", 
    imageBytes
);

String screenshotUrl = result.getUrl();
task.sendScreenshot(screenshotUrl);
```

---

### 0.3 会话管理要点

#### 持久化会话 vs 临时会话

**持久化会话**（推荐，保存登录状态）:
```java
// 会话数据保存在磁盘，下次自动加载
session = browserPool.acquirePersistent(userId, "session_name", false);

// 数据保存位置：./data/playwright/session_name/{userId}/
// 包含：Cookies、LocalStorage、SessionStorage等
```

**临时会话**（无痕模式）:
```java
// 会话数据不保存，关闭后清空
session = browserPool.acquireTemporary("task_id");
```

#### 会话命名规范

**按业务场景命名**:
```java
// 好的命名
session = browserPool.acquirePersistent(userId, "baidu_search", false);
session = browserPool.acquirePersistent(userId, "taobao_shopping", false);
session = browserPool.acquirePersistent(userId, "wechat_article", false);

// 不好的命名
session = browserPool.acquirePersistent(userId, "session1", false);
session = browserPool.acquirePersistent(userId, "temp", false);
```

#### 必须释放会话

**✅ 正确做法**:
```java
BrowserSession session = null;
try {
    session = browserPool.acquirePersistent(userId, "task", false);
    // 使用会话...
} finally {
    if (session != null) {
        session.destroy(); // 🔥 必须调用
    }
}
```

**❌ 错误做法**:
```java
// 错误：忘记释放会话
session = browserPool.acquirePersistent(userId, "task", false);
// 使用会话...
// 忘记调用 session.destroy()
```

---

### 0.4 常见错误处理

#### 错误1：元素未找到

**问题**:
```java
page.click("#button"); // TimeoutError: Timeout 30000ms exceeded
```

**解决**:
```java
// 方案1：增加等待时间
page.waitForSelector("#button", new Page.WaitForSelectorOptions().setTimeout(60000));
page.click("#button");

// 方案2：检查元素是否存在
if (page.locator("#button").count() > 0) {
    page.click("#button");
} else {
    log.warn("按钮不存在");
}
```

#### 错误2：页面加载超时

**问题**:
```java
page.navigate("https://example.com"); // TimeoutError
```

**解决**:
```java
// 增加超时时间
page.navigate("https://example.com", 
    new Page.NavigateOptions().setTimeout(60000));

// 或者不等待加载完成
page.navigate("https://example.com", 
    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
```

#### 错误3：会话未释放导致文件锁

**问题**:
```
用户数据目录已被使用
```

**解决**:
```java
// 确保在finally中释放
try {
    session = browserPool.acquirePersistent(userId, "task", false);
    // ...
} finally {
    if (session != null) {
        session.destroy(); // 释放文件锁
    }
}
```

#### 错误4：并发访问导致元素状态异常

**问题**:
```
Element is not attached to the DOM
```

**解决**:
```java
// 重新获取元素
for (int i = 0; i < 3; i++) {
    try {
        page.click("#button");
        break;
    } catch (PlaywrightException e) {
        if (i == 2) throw e;
        page.waitForTimeout(1000);
    }
}
```

---

### 0.5 完整示例参考

**位置**: `WxFbsir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/`

**文件**:
- `BaiduHotSearchDemoController.java` - 完整的浏览器自动化示例
  - 打开网页
  - 抓取数据
  - 点击链接
  - 截图上传
  - 会话管理

**深入学习**:
- [第6章：最佳实践](#6-最佳实践) - 开发规范
- [附录A：截图上传完整示例](#附录a截图上传完整示例) - 截图功能
- [6.4 登录信息保存与复用](#64-登录信息保存与复用) - 会话持久化

---

## 1. 框架概述

### 1.1 架构图

```
┌─────────────────────────────────────────────────────────────────────┐
│                        WxFbsir-engine                                │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    PlaywrightTaskExecutor                     │   │
│  │    ┌──────────┐  ┌──────────┐  ┌──────────┐                  │   │
│  │    │ 同步执行  │  │ 异步执行  │  │ 超时执行  │                  │   │
│  │    └────┬─────┘  └────┬─────┘  └────┬─────┘                  │   │
│  └─────────┼─────────────┼─────────────┼───────────────────────┘   │
│            │             │             │                            │
│            ▼             ▼             ▼                            │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    BrowserPoolManager                         │   │
│  │    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    │   │
│  │    │ 持久化会话池  │    │  临时会话池   │    │  资源监控    │    │   │
│  │    │  (用户隔离)  │    │  (任务隔离)   │    │  (泄漏检测)  │    │   │
│  │    └──────┬──────┘    └──────┬──────┘    └─────────────┘    │   │
│  └───────────┼──────────────────┼──────────────────────────────┘   │
│              │                  │                                   │
│              ▼                  ▼                                   │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                      BrowserSession                           │   │
│  │    ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  │   │
│  │    │ Browser  │  │ Context  │  │  Page    │  │ 资源计数  │  │   │
│  │    └──────────┘  └──────────┘  └──────────┘  └──────────┘  │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                      │
│                              ▼                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    PlaywrightManager                          │   │
│  │              (单例 Playwright 实例管理)                        │   │
│  └─────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 核心特性

| 特性 | 说明 |
|------|------|
| ✅ **池化管理** | 浏览器会话复用，避免频繁创建销毁 |
| ✅ **用户隔离** | 每个用户独立的浏览器上下文和数据目录 |
| ✅ **实例隔离** | 支持单用户多浏览器实例（instanceId） |
| ✅ **剪贴板隔离** | 每页面独立锁，防止并发冲突 |
| ✅ **截图隔离** | 每页面独立锁，防止并发冲突 |
| ✅ **截图上传** | 自动上传到Admin服务器，获取访问URL |
| ✅ **资源监控** | 创建/销毁计数，泄漏检测告警 |
| ✅ **僵尸进程清理** | 定时清理残留的 Chrome 进程 |
| ✅ **动态配置** | 根据系统硬件自动计算最优参数 |

---

## 2. 核心组件详解

### 2.1 既往典型问题分析

| 问题 | cube-engine 表现 | 本框架解决方案 |
|------|------------------|---------------|
| **内存泄漏** | 静态 HashMap 未清理、Playwright 实例不关闭 | 池化管理 + 自动过期清理 + 显式资源释放 |
| **线程泄漏** | 多个独立 ScheduledExecutorService | 统一线程池管理 + @PreDestroy 关闭 |
| **资源耗尽** | 无并发控制，无限创建浏览器 | Semaphore 限制最大会话数 |
| **僵尸进程** | 异常退出后 Chrome 进程残留 | 定时清理 + 锁文件检测 |
| **剪贴板冲突** | 多线程同时操作剪贴板 | 每页面独立 ReentrantLock |
| **截图冲突** | 多线程同时截图 | 每页面独立 ReentrantLock |
| **静默异常** | catch 块空处理或 e.printStackTrace() | 所有异常记录 SLF4J 日志 |
| **重试复杂** | 15次重试逻辑 | 简化为3次，快速失败 |

### 2.2 隔离性设计

#### 用户隔离

```
数据目录结构：
${user.home}/.wxfbsir/playwright-data/
├── persistent/
│   ├── user-001/
│   │   ├── baidu/          # 用户001的百度会话
│   │   │   └── Default/
│   │   └── deepseek/       # 用户001的DeepSeek会话
│   │       └── Default/
│   └── user-002/
│       └── baidu/          # 用户002的百度会话（完全隔离）
└── temporary/
    └── task-xxx/           # 临时任务会话
```

#### 单用户多实例隔离

```java
// 同一用户可以同时操作多个独立浏览器实例
BrowserSession session1 = browserPool.acquirePersistent("user-001", "baidu", "instance-1");
BrowserSession session2 = browserPool.acquirePersistent("user-001", "baidu", "instance-2");
// session1 和 session2 完全独立，互不影响
```

#### 剪贴板/截图锁隔离

```
锁结构：
ClipboardManager.pageLocks: Map<PageId, ReentrantLock>
ScreenshotUtil.pageLocks: Map<PageId, ReentrantLock>

用户A.Page1 ─── 独立锁1
用户A.Page2 ─── 独立锁2
用户B.Page1 ─── 独立锁3（与用户A完全隔离）
```

### 2.3 资源管理设计

| 机制 | 说明 |
|------|------|
| **创建计数** | 记录 Browser/Context/Page 创建次数 |
| **销毁计数** | 记录 Browser/Context/Page 销毁次数 |
| **泄漏检测** | `创建数 - 销毁数 - 活跃数 > 阈值` 时告警 |
| **定时清理** | 每10分钟清理僵尸进程和锁文件 |
| **优雅关闭** | @PreDestroy 时按序关闭所有资源 |

---

## 注：代码文件结构详情

```
WxFbsir-engine/src/main/java/com/wx/fbsir/engine/playwright/
│
├── config/
│   └── PlaywrightProperties.java      # 配置属性类
│       - 读取 application.yml 中的 playwright 配置
│       - 提供默认值和动态计算支持
│
├── core/
│   ├── PlaywrightManager.java         # Playwright 实例管理（单例）
│   │   - 懒加载创建 Playwright 实例
│   │   - ReentrantLock 保证线程安全
│   │   - 僵尸进程清理
│   │   - 锁文件清理
│   │   - 故障重建机制
│   │
│   └── PlaywrightTaskExecutor.java    # 任务执行器
│       - 同步/异步/超时任务执行
│       - 统一线程池管理
│       - 任务状态统计
│       - 支持 instanceId 隔离
│
├── pool/
│   └── BrowserPoolManager.java        # 浏览器会话池
│       - 持久化会话管理（用户数据保存）
│       - 临时会话管理（无痕模式）
│       - Semaphore 并发控制
│       - 定时清理过期会话
│       - 资源泄漏检测
│
├── session/
│   └── BrowserSession.java            # 会话抽象
│       - 封装 Browser/Context/Page
│       - 生命周期管理
│       - 资源计数（创建/关闭）
│       - 支持 instanceId 隔离
│
├── monitor/
│   └── ResourceMonitor.java           # 资源监控器
│       - 锁泄漏检测
│       - 线程泄漏检测
│       - 内存使用监控
│       - 定期状态报告
│
├── scheduler/
│   └── PlaywrightScheduledTasks.java  # 定时任务
│       - 每小时清理过期截图
│       - 每10分钟清理僵尸进程
│
├── util/
│   ├── ClipboardManager.java          # 剪贴板操作
│   │   - 每页面独立锁
│   │   - 10秒锁超时
│   │   - 支持降级方案 (execCommand)
│   │
│   ├── ScreenshotUtil.java            # 截图工具
│   │   - 每页面独立锁
│   │   - 30秒锁超时
│   │   - 支持页面/元素/二维码截图
│   │
│   └── SystemCapabilityDetector.java  # 系统能力检测
│       - 检测 CPU 核心数
│       - 检测可用内存
│       - 动态计算最优配置
│
└── package-info.java                  # 包说明文档
```

### 文件功能速查表

| 文件 | 职责 | 关键方法 |
|------|------|---------|
| `PlaywrightManager` | 管理 Playwright 单例 | `getPlaywright()`, `rebuild()`, `cleanupZombieProcesses()` |
| `PlaywrightTaskExecutor` | 任务执行入口 | `execute()`, `executeAsync()`, `executeWithTimeout()` |
| `BrowserPoolManager` | 会话池管理 | `acquirePersistent()`, `acquireTemporary()`, `releaseSession()` |
| `BrowserSession` | 会话操作 | `getOrCreatePage()`, `closePage()`, `destroy()` |
| `ResourceMonitor` | 资源监控 | `checkResources()`, `getResourceStatus()` |
| `ClipboardManager` | 剪贴板操作 | `write()`, `read()`, `pasteToElement()` |
| `ScreenshotUtil` | 截图操作 | `capture()`, `captureAsBase64()`, `captureQrCode()` |

---

## 四、配置说明

### 4.1 完整配置示例

```yaml
wxfbsir:
  engine:
    playwright:
      # 基础配置
      enabled: true                                    # 是否启用
      data-dir: ${user.home}/.wxfbsir/playwright-data  # 数据目录
      headless: false                                  # 默认无头模式
      dynamic-performance: true                        # 启用动态性能计算
      
      # 浏览器池配置
      pool:
        max-size: 0                    # 最大会话数（0=动态计算）
        min-idle: 0                    # 最小空闲数
        session-timeout: 3600000       # 会话超时（毫秒，默认1小时）
        cleanup-interval: 300000       # 清理间隔（毫秒，默认5分钟）
        acquire-timeout: 30000         # 获取超时（毫秒）
        
      # 浏览器启动配置
      browser:
        launch-timeout: 60000          # 启动超时
        navigation-timeout: 30000      # 导航超时
        viewport-width: 1280           # 视口宽度
        viewport-height: 720           # 视口高度
        disable-images: false          # 禁用图片（节省资源）
        disable-gpu: true              # 禁用GPU（服务器环境）
        max-retries: 3                 # 最大重试次数
        retry-interval: 2000           # 重试间隔（毫秒）
        
      # 线程池配置
      thread-pool:
        core-size: 0                   # 核心线程数（0=动态计算）
        max-size: 0                    # 最大线程数（0=动态计算）
        keep-alive-seconds: 60         # 空闲时间
        queue-capacity: 0              # 队列大小（0=动态计算）
```

### 4.2 动态配置说明

当 `dynamic-performance: true` 且对应配置为 `0` 时，系统会根据硬件自动计算：

| 配置项 | 计算公式 | 4核8G示例 | 8核16G示例 |
|--------|---------|----------|-----------|
| `pool.max-size` | min(CPU核心数, 可用内存GB/2) | 4 | 8 |
| `thread-pool.core-size` | CPU核心数 | 4 | 8 |
| `thread-pool.max-size` | CPU核心数 * 2 | 8 | 16 |
| `thread-pool.queue-capacity` | max-size * 10 | 80 | 160 |

### 4.3 低配置系统优化

对于 2核4G 以下的系统，框架会自动：
- 将池大小限制为 1-2
- 建议启用 `disable-images: true`
- 减少线程池大小

---

## 五、核心组件详解

### 5.1 PlaywrightManager

**职责**：管理 Playwright 单例实例的生命周期

```java
@Component
public class PlaywrightManager {
    
    // 获取 Playwright 实例（懒加载）
    public Playwright getPlaywright();
    
    // 故障恢复重建
    public void rebuild();
    
    // 清理僵尸进程（公开方法）
    public void cleanupZombieProcesses();
    
    // 检查是否可用
    public boolean isAvailable();
}
```

**关键设计**：
- 使用 `AtomicReference<Playwright>` + `ReentrantLock` 保证线程安全
- 创建失败时自动清理僵尸进程后重试
- `@PreDestroy` 时优雅关闭所有资源

### 5.2 BrowserPoolManager

**职责**：管理浏览器会话池

```java
@Component
public class BrowserPoolManager {
    
    // 获取持久化会话
    public BrowserSession acquirePersistent(String userId, String name);
    public BrowserSession acquirePersistent(String userId, String name, boolean headless);
    public BrowserSession acquirePersistent(String userId, String name, String instanceId);
    
    // 获取临时会话
    public BrowserSession acquireTemporary(String taskId);
    
    // 释放会话
    public void releaseSession(BrowserSession session);
    
    // 关闭指定会话
    public void closeSession(String userId, String name);
    
    // 获取池状态
    public Map<String, Object> getStatus();
    
    // 获取资源泄漏信息
    public String getResourceLeakInfo();
}
```

**会话 Key 格式**：
- 持久化会话：`persistent:{userId}:{name}` 或 `persistent:{userId}:{name}:{instanceId}`
- 临时会话：`temporary:{taskId}`

### 5.3 BrowserSession

**职责**：封装单个浏览器会话

```java
public class BrowserSession implements AutoCloseable {
    
    // 获取或创建页面
    public Page getOrCreatePage();
    
    // 关闭指定页面
    public void closePage(Page page);
    
    // 保持会话活跃
    public void touch();
    
    // 检查是否过期
    public boolean isExpired();
    
    // 获取资源泄漏信息
    public String getResourceLeakInfo();
    
    // 销毁会话（释放所有资源）
    public void destroy();
}
```

**资源释放顺序**：
1. 关闭所有 Page
2. 关闭 BrowserContext
3. 关闭 Browser（仅临时会话）

### 5.4 PlaywrightTaskExecutor

**职责**：统一任务执行入口

```java
@Component
public class PlaywrightTaskExecutor {
    
    // 同步执行（持久化会话）
    public <T> T execute(String userId, String name, 
                         Function<BrowserSession, T> task);
    
    // 同步执行（带实例ID）
    public <T> T execute(String userId, String name, String instanceId,
                         Function<BrowserSession, T> task);
    
    // 异步执行
    public <T> CompletableFuture<T> executeAsync(String userId, String name,
                                                  Function<BrowserSession, T> task);
    
    // 带超时执行
    public <T> T executeWithTimeout(String userId, String name,
                                    long timeout, TimeUnit unit,
                                    Function<BrowserSession, T> task);
    
    // 临时会话执行
    public <T> T executeTemporary(String taskId, 
                                   Function<BrowserSession, T> task);
    
    // 获取执行器状态
    public Map<String, Object> getStatus();
}
```

---

## 六、使用指南

### 6.1 基本使用

```java
@Service
public class MyService {

    @Autowired
    private BrowserPoolManager browserPool;

    public void basicUsage() {
        // 方式1：try-with-resources（推荐）
        try (BrowserSession session = browserPool.acquirePersistent("user-001", "baidu")) {
            Page page = session.getOrCreatePage();
            page.navigate("https://baidu.com");
            String title = page.title();
            System.out.println("页面标题: " + title);
        } // 自动归还到池

        // 方式2：手动管理
        BrowserSession session = browserPool.acquirePersistent("user-001", "baidu");
        try {
            Page page = session.getOrCreatePage();
            page.navigate("https://baidu.com");
        } finally {
            browserPool.releaseSession(session);
        }
    }
}
```

### 6.2 使用任务执行器

```java
@Service
public class TaskService {

    @Autowired
    private PlaywrightTaskExecutor taskExecutor;

    // 同步执行
    public String syncTask() {
        return taskExecutor.execute("user-001", "baidu", session -> {
            Page page = session.getOrCreatePage();
            page.navigate("https://example.com");
            return page.title();
        });
    }

    // 异步执行
    public CompletableFuture<String> asyncTask() {
        return taskExecutor.executeAsync("user-001", "baidu", session -> {
            Page page = session.getOrCreatePage();
            page.navigate("https://example.com");
            return page.title();
        });
    }

    // 带超时执行
    public String timeoutTask() {
        return taskExecutor.executeWithTimeout("user-001", "baidu", 
            30, TimeUnit.SECONDS, session -> {
                Page page = session.getOrCreatePage();
                page.navigate("https://example.com");
                return page.title();
            });
    }

    // 临时会话执行
    public String temporaryTask() {
        return taskExecutor.executeTemporary("task-001", session -> {
            Page page = session.getOrCreatePage();
            page.navigate("https://example.com");
            return page.title();
        });
    }
}
```

### 6.3 多实例隔离

```java
@Service
public class MultiInstanceService {

    @Autowired
    private PlaywrightTaskExecutor taskExecutor;

    // 同一用户同时操作多个独立浏览器
    public void multiInstance() {
        // 实例1
        CompletableFuture<String> future1 = taskExecutor.executeAsync(
            "user-001", "baidu", "instance-1", session -> {
                Page page = session.getOrCreatePage();
                page.navigate("https://baidu.com/search?q=java");
                return page.title();
            });

        // 实例2（与实例1完全独立）
        CompletableFuture<String> future2 = taskExecutor.executeAsync(
            "user-001", "baidu", "instance-2", session -> {
                Page page = session.getOrCreatePage();
                page.navigate("https://baidu.com/search?q=python");
                return page.title();
            });

        // 等待两个任务完成
        CompletableFuture.allOf(future1, future2).join();
    }
}
```

### 6.4 登录信息保存与复用 ⭐⭐⭐

#### 6.4.1 持久化会话原理

框架使用Playwright的 `userDataDir` 功能实现登录信息的持久化保存：

**保存的数据**：
- ✅ Cookies（会话Cookie和持久Cookie）
- ✅ LocalStorage
- ✅ SessionStorage
- ✅ IndexedDB
- ✅ Service Workers
- ✅ Cache Storage

**存储位置**：
```
./data/playwright/{sessionName}/{userId}/
```

例如：
- 百度会话：`./data/playwright/baidu_demo/user-001/`
- 淘宝会话：`./data/playwright/taobao/user-001/`
- 微信会话：`./data/playwright/wechat/user-001/`

#### 6.4.2 基本使用示例

```java
@Controller
public class LoginDemoController extends StreamTaskHelper {
    
    @Autowired
    private BrowserPoolManager browserPool;
    
    @StreamCapability(type = "LOGIN_DEMO", description = "登录演示")
    public void handleLogin(EngineMessage message) {
        String userId = message.getUserId();
        StreamTask task = startStreamTask(userId, requestId);
        
        BrowserSession session = null;
        try {
            // 🔥 关键：使用持久化会话
            // 第一次调用：创建新会话，需要手动登录
            // 后续调用：自动加载登录状态，无需重新登录
            session = browserPool.acquirePersistent(userId, "my_website", false);
            Page page = session.getOrCreatePage();
            
            // 打开网站
            page.navigate("https://example.com");
            
            // 检查是否已登录
            if (isLoggedIn(page)) {
                task.sendLog("已登录，直接使用");
                // 执行业务逻辑...
            } else {
                task.sendLog("未登录，需要登录");
                // 执行登录逻辑...
                performLogin(page);
                task.sendLog("登录成功");
            }
            
            // 执行业务操作...
            
            task.sendSuccess("任务完成", resultData);
            
        } finally {
            task.stop();
            if (session != null) {
                // 🔥 重要：销毁会话，释放文件锁
                // 登录信息已自动保存到磁盘
                session.destroy();
            }
        }
    }
    
    private boolean isLoggedIn(Page page) {
        // 检查登录状态的逻辑
        // 例如：检查特定元素是否存在
        return page.locator(".user-avatar").count() > 0;
    }
    
    private void performLogin(Page page) {
        // 执行登录操作
        page.fill("#username", "myusername");
        page.fill("#password", "mypassword");
        page.click("#login-button");
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }
}
```

#### 6.4.3 完整示例：DeepSeek登录保存

参考 `DeepSeekController.java` 的实现：

```java
@Controller
public class DeepSeekController extends StreamTaskHelper {
    
    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    private DeepSeekUtil deepSeekUtil;
    
    // 检查登录状态
    @OnceCapability(type = "DEEPSEEK_CHECK_LOGIN", description = "检查DeepSeek登录状态")
    public void handleCheckLogin(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        BrowserSession session = null;
        try {
            // 获取持久化会话
            session = browserPool.acquirePersistent(userId, "deepseek", false);
            String loginStatus = deepSeekUtil.checkLoginStatus(session.getOrCreatePage(), true);
            boolean isLoggedIn = !"false".equals(loginStatus);
            
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("isLoggedIn", isLoggedIn);
            resultData.put("userName", isLoggedIn ? loginStatus : null);
            
            sendResult(userId, requestId, resultData);
            
        } finally {
            if (session != null) {
                // 完全销毁会话，释放文件锁
                // 登录状态已保存在：./data/playwright/deepseek/{userId}/
                session.destroy();
            }
        }
    }
    
    // 扫码登录
    @StreamCapability(type = "DEEPSEEK_SCAN_LOGIN", description = "DeepSeek扫码登录")
    public void handleScanLogin(EngineMessage message) {
        String userId = message.getUserId();
        String requestId = message.getPayloadValue("requestId");
        
        StreamTask task = startStreamTask(userId, requestId);
        BrowserSession session = null;
        
        try {
            task.sendLog("正在打开DeepSeek登录页面...");
            
            // 获取持久化会话
            session = browserPool.acquirePersistent(userId, "deepseek", false);
            Page page = session.getOrCreatePage();
            
            // 导航到登录页
            deepSeekUtil.navigateToLoginPage(page);
            
            // 截图二维码
            String qrCodeUrl = captureAndUpload(page, userId, "deepseek_qrcode");
            task.sendScreenshot(qrCodeUrl);
            
            // 等待登录成功
            task.sendLog("等待扫码登录...");
            boolean loginSuccess = waitForLogin(page, task);
            
            if (loginSuccess) {
                task.sendLog("登录成功！");
                // 🔥 登录信息已自动保存到：./data/playwright/deepseek/{userId}/
                // 下次调用时会自动加载，无需重新登录
                task.sendSuccess("登录成功", resultData);
            } else {
                task.sendError("登录超时");
            }
            
        } finally {
            task.stop();
            if (session != null) {
                // 销毁会话，释放文件锁
                session.destroy();
            }
        }
    }
}
```

#### 6.4.4 文件锁问题处理

**问题**：持久化会话使用 `userDataDir`，Chromium会对目录加锁，如果不正确释放会导致下次无法使用。

**解决方案**：

1. **必须调用 `session.destroy()`**
```java
try {
    session = browserPool.acquirePersistent(userId, "session_name", false);
    // 使用会话...
} finally {
    if (session != null) {
        // 🔥 重要：完全销毁会话，释放文件锁
        session.destroy();
    }
}
```

2. **`session.destroy()` 执行的操作**：
   - 关闭所有Page页面
   - 关闭BrowserContext上下文
   - 关闭Browser浏览器实例
   - **释放用户数据目录的文件锁**
   - 从池中移除会话记录

3. **登录数据已自动保存**：
   - Playwright会在关闭前自动将Cookies等数据写入磁盘
   - 下次调用 `acquirePersistent()` 时会自动加载
   - 无需手动保存

#### 6.4.5 会话隔离策略

**按用户隔离**：
```java
// 用户A的百度会话
session = browserPool.acquirePersistent("user-001", "baidu", false);
// 数据保存在：./data/playwright/baidu/user-001/

// 用户B的百度会话
session = browserPool.acquirePersistent("user-002", "baidu", false);
// 数据保存在：./data/playwright/baidu/user-002/
```

**按业务场景隔离**：
```java
// 同一用户的不同网站会话
session1 = browserPool.acquirePersistent("user-001", "baidu", false);
session2 = browserPool.acquirePersistent("user-001", "taobao", false);
session3 = browserPool.acquirePersistent("user-001", "wechat", false);

// 数据分别保存在：
// ./data/playwright/baidu/user-001/
// ./data/playwright/taobao/user-001/
// ./data/playwright/wechat/user-001/
```

**按实例隔离**（高级用法）：
```java
// 同一用户同一网站的多个账号
session1 = browserPool.acquirePersistent("user-001", "baidu", "account1", false);
session2 = browserPool.acquirePersistent("user-001", "baidu", "account2", false);

// 数据分别保存在：
// ./data/playwright/baidu/user-001/account1/
// ./data/playwright/baidu/user-001/account2/
```

#### 6.4.6 最佳实践

**✅ 推荐做法**：

1. **使用有意义的会话名称**
```java
// 好
session = browserPool.acquirePersistent(userId, "taobao_shopping", false);
session = browserPool.acquirePersistent(userId, "wechat_article", false);

// 不好
session = browserPool.acquirePersistent(userId, "session1", false);
session = browserPool.acquirePersistent(userId, "temp", false);
```

2. **检查登录状态后再操作**
```java
session = browserPool.acquirePersistent(userId, "website", false);
Page page = session.getOrCreatePage();

if (!isLoggedIn(page)) {
    // 需要登录
    performLogin(page);
}

// 执行业务操作
```

3. **长时间操作定期touch**
```java
session = browserPool.acquirePersistent(userId, "website", false);
try {
    for (int i = 0; i < 100; i++) {
        // 防止会话超时
        session.touch();
        
        // 执行操作
        doSomething();
    }
} finally {
    session.destroy();
}
```

**❌ 避免的做法**：

1. **不要忘记调用 destroy()**
```java
// 错误：会导致文件锁泄漏
session = browserPool.acquirePersistent(userId, "website", false);
// 使用会话...
// 忘记调用 session.destroy()
```

2. **不要在异常时不释放会话**
```java
// 错误：异常时会话未释放
session = browserPool.acquirePersistent(userId, "website", false);
doSomething();  // 可能抛异常
session.destroy();  // 异常时不会执行

// 正确：使用 finally
try {
    session = browserPool.acquirePersistent(userId, "website", false);
    doSomething();
} finally {
    if (session != null) session.destroy();
}
```

3. **不要混用临时会话和持久化会话**
```java
// 错误：登录信息会丢失
session = browserPool.acquireTemporary("task-001");  // 临时会话
performLogin(page);  // 登录
session.destroy();  // 登录信息丢失

// 正确：使用持久化会话
session = browserPool.acquirePersistent(userId, "website", false);
performLogin(page);  // 登录
session.destroy();  // 登录信息已保存
```

#### 6.4.7 故障排查

**问题1：提示"用户数据目录已被使用"**

原因：上次会话未正确释放文件锁

解决：
```bash
# 1. 停止所有相关进程
ps aux | grep chromium | grep -v grep | awk '{print $2}' | xargs kill -9

# 2. 删除锁文件
rm -rf ./data/playwright/*/*/SingletonLock

# 3. 重启应用
```

**问题2：登录信息丢失**

原因：使用了临时会话或未正确保存

解决：
- 确保使用 `acquirePersistent()` 而不是 `acquireTemporary()`
- 确保调用了 `session.destroy()` 让Playwright保存数据

**问题3：多个用户登录信息混乱**

原因：userId或sessionName使用不当

解决：
- 确保每个用户使用唯一的 userId
- 确保不同业务场景使用不同的 sessionName

---

## 七、工具类使用

### 7.1 剪贴板操作

```java
@Service
public class ClipboardService {

    @Autowired
    private ClipboardManager clipboardManager;
    
    @Autowired
    private BrowserPoolManager browserPool;

    public void clipboardOps() {
        try (BrowserSession session = browserPool.acquirePersistent("user-001", "demo")) {
            Page page = session.getOrCreatePage();
            page.navigate("https://example.com");

            // 写入剪贴板
            boolean success = clipboardManager.write(page, "Hello World");

            // 读取剪贴板
            String content = clipboardManager.read(page);

            // 粘贴到元素
            clipboardManager.pasteToElement(page, "#input-field", "粘贴内容");

            // 从元素复制
            String copied = clipboardManager.copyFromElement(page, "#text-content");

            // 清空剪贴板
            clipboardManager.clear(page);
        }
    }
}
```

### 7.2 截图操作

```java
@Service
public class ScreenshotService {

    @Autowired
    private ScreenshotUtil screenshotUtil;
    
    @Autowired
    private BrowserPoolManager browserPool;

    public void screenshotOps() {
        try (BrowserSession session = browserPool.acquirePersistent("user-001", "demo")) {
            Page page = session.getOrCreatePage();
            page.navigate("https://example.com");

            // 截图保存到文件
            Path path = screenshotUtil.capture(page, "screenshot-name");

            // 全页面截图保存到文件
            Path fullPath = screenshotUtil.capture(page, "full-page", true);

            // 截图返回 Base64
            String base64 = screenshotUtil.captureAsBase64(page);

            // 截图返回 Data URL（可直接用于 img src）
            String dataUrl = screenshotUtil.captureAsDataUrl(page);

            // 截取指定元素
            String elementBase64 = screenshotUtil.captureElementAsBase64(page, "#qrcode");

            // 截取二维码（自动等待元素出现，失败降级为全页面截图）
            String qrBase64 = screenshotUtil.captureQrCode(page, ".qrcode-wrapper");
        }
    }
}
```

---

## 八、资源管理与监控

### 8.1 获取状态信息

```java
@RestController
@RequestMapping("/playwright")
public class PlaywrightStatusController {

    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    private PlaywrightTaskExecutor taskExecutor;
    
    @Autowired
    private ResourceMonitor resourceMonitor;

    @GetMapping("/status")
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // 浏览器池状态
        status.put("pool", browserPool.getStatus());
        // {activeCount, persistentCount, temporaryCount, maxSize, availableSlots}
        
        // 任务执行器状态
        status.put("executor", taskExecutor.getStatus());
        // {activeTaskCount, completedTaskCount, failedTaskCount, poolSize, queueSize}
        
        // 资源监控状态
        status.put("resources", resourceMonitor.getResourceStatus());
        // {clipboardLocks, screenshotLocks, currentThreads, heapUsedMB, alertCount}
        
        // 资源泄漏信息
        status.put("leakInfo", browserPool.getResourceLeakInfo());
        
        return status;
    }
}
```

### 8.2 健康检查端点

```java
// 已内置于 HealthController
GET /health              # 基础健康检查
GET /health/ready        # 就绪检查
GET /health/detail       # 详细状态（包含资源监控）
```

### 8.3 资源监控告警

`ResourceMonitor` 每5分钟自动检查：
- **锁泄漏**：锁数量 > 50 时告警，> 100 时强制清理
- **线程泄漏**：线程增长 > 100 时告警
- **内存使用**：堆内存使用率 > 85% 时告警

---

## 九、业务示例

### 9.1 AI 登录二维码获取

```java
@Service
public class AiLoginService {

    @Autowired
    private BrowserPoolManager browserPool;
    
    @Autowired
    private ScreenshotUtil screenshotUtil;

    public LoginResult getBaiduQrCode(String userId) {
        try (BrowserSession session = browserPool.acquirePersistent(userId, "baidu", false)) {
            Page page = session.getOrCreatePage();
            
            // 导航到登录页
            page.navigate("https://ai.baidu.com/");
            page.waitForLoadState();
            
            // 检查是否已登录
            if (page.locator(".user-avatar").count() > 0) {
                return LoginResult.alreadyLoggedIn();
            }
            
            // 点击登录按钮
            page.locator("text=登录").click();
            page.waitForTimeout(2000);
            
            // 截取二维码
            String qrBase64 = screenshotUtil.captureQrCode(page, ".qrcode-wrapper");
            
            return LoginResult.needScan(qrBase64);
        } catch (Exception e) {
            return LoginResult.error(e.getMessage());
        }
    }

    public LoginResult checkLoginStatus(String userId) {
        try (BrowserSession session = browserPool.acquirePersistent(userId, "baidu")) {
            Page page = session.getOrCreatePage();
            
            // 检查是否已登录
            if (page.locator(".user-avatar").count() > 0) {
                return LoginResult.success();
            }
            
            return LoginResult.pending();
        }
    }
}
```

### 9.2 批量任务执行

```java
@Service
public class BatchTaskService {

    @Autowired
    private PlaywrightTaskExecutor taskExecutor;

    public List<TaskResult> batchFetch(List<String> urls) {
        List<CompletableFuture<TaskResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < urls.size(); i++) {
            String url = urls.get(i);
            String taskId = "batch-" + i;
            
            CompletableFuture<TaskResult> future = taskExecutor.executeTemporaryAsync(
                taskId, session -> {
                    try {
                        Page page = session.getOrCreatePage();
                        page.navigate(url);
                        return TaskResult.success(url, page.title());
                    } catch (Exception e) {
                        return TaskResult.error(url, e.getMessage());
                    }
                });
            
            futures.add(future);
        }
        
        // 等待所有任务完成
        return futures.stream()
            .map(f -> {
                try {
                    return f.get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return TaskResult.error("unknown", e.getMessage());
                }
            })
            .collect(Collectors.toList());
    }
}
```

---

## 十、最佳实践

### 10.1 资源管理

```java
// ✅ 正确：使用 try-with-resources
try (BrowserSession session = browserPool.acquirePersistent("userId", "task")) {
    // 操作...
}

// ❌ 错误：可能导致资源泄漏
BrowserSession session = browserPool.acquirePersistent("userId", "task");
// 操作...
// 忘记关闭或发生异常
```

### 10.2 长时间任务

```java
// ✅ 正确：定期调用 touch() 保持会话活跃
try (BrowserSession session = browserPool.acquirePersistent("userId", "task")) {
    for (int i = 0; i < 30; i++) {
        session.touch();  // 防止会话超时
        page.waitForTimeout(2000);
        // 检查状态...
    }
}

// ✅ 正确：使用带超时的执行
taskExecutor.executeWithTimeout("userId", "task", 120, TimeUnit.SECONDS, session -> {
    // 长时间操作...
    return result;
});
```

### 10.3 会话类型选择

```java
// 需要保持登录状态 → 持久化会话
browserPool.acquirePersistent("userId", "baidu");

// 一次性任务、不需要保存状态 → 临时会话
browserPool.acquireTemporary("taskId");
```

### 10.4 异常处理

```java
// ✅ 正确：捕获并处理特定异常
try (BrowserSession session = browserPool.acquirePersistent("userId", "task")) {
    Page page = session.getOrCreatePage();
    page.navigate("https://example.com");
} catch (TimeoutError e) {
    log.warn("页面加载超时: {}", e.getMessage());
    // 重试或降级处理
} catch (PlaywrightException e) {
    log.error("Playwright 异常: {}", e.getMessage());
    // 可能需要重建会话
}
```

---

## 十一、常见问题

### Q1: 浏览器启动失败

**排查步骤**：
1. 检查是否安装了 Playwright 浏览器：
   ```bash
   mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
   ```
2. 检查 `data-dir` 目录写入权限
3. 查看日志中的详细错误信息
4. 检查是否存在僵尸进程占用

### Q2: 会话数达到上限

**解决方案**：
1. 增加 `pool.max-size` 配置
2. 减少 `session-timeout` 让空闲会话更快释放
3. 确保代码中正确关闭会话（使用 try-with-resources）
4. 检查是否存在资源泄漏（调用 `getResourceLeakInfo()`）

### Q3: 剪贴板操作失败

**解决方案**：
1. 无头模式下某些剪贴板操作可能受限，框架会自动降级使用 `execCommand`
2. 如果仍然失败，考虑使用有头模式 `headless: false`
3. 检查是否存在锁超时（默认10秒）

### Q4: 截图返回空或异常

**排查步骤**：
1. 检查元素选择器是否正确
2. 检查页面是否加载完成（使用 `waitForLoadState()`）
3. 检查是否存在锁超时（默认30秒）
4. 查看日志中的详细错误信息

### Q5: 僵尸进程问题

**框架已内置解决方案**：
- 启动时清理僵尸进程
- 定时清理（每10分钟）
- 创建失败时清理后重试
- 关闭时清理所有残留进程

**手动清理命令**：
```bash
# macOS/Linux
ps aux | grep chromium | grep -v grep | awk '{print $2}' | xargs kill -9

# Windows
taskkill /F /IM chrome.exe
```

---

## 附录：配置参数速查

| 参数路径 | 默认值 | 说明 |
|----------|--------|------|
| `playwright.enabled` | true | 是否启用 |
| `playwright.data-dir` | `${user.home}/.wxfbsir/playwright-data` | 数据目录 |
| `playwright.headless` | false | 默认无头模式 |
| `playwright.dynamic-performance` | true | 动态性能计算 |
| `playwright.pool.max-size` | 0 (动态) | 最大会话数 |
| `playwright.pool.session-timeout` | 3600000 | 会话超时(ms) |
| `playwright.pool.cleanup-interval` | 300000 | 清理间隔(ms) |
| `playwright.pool.acquire-timeout` | 30000 | 获取超时(ms) |
| `playwright.browser.launch-timeout` | 60000 | 启动超时(ms) |
| `playwright.browser.max-retries` | 3 | 最大重试次数 |
| `playwright.thread-pool.core-size` | 0 (动态) | 核心线程数 |
| `playwright.thread-pool.max-size` | 0 (动态) | 最大线程数 |

---

## 附录A：截图上传完整示例

### A.1 截图上传流程

框架提供了完整的截图上传功能，可以将Playwright截图自动上传到Admin服务器并获取访问URL。

### A.2 核心组件

**ScreenshotUtil** - 截图工具类
- 提供页面截图功能
- 支持全页面截图和元素截图
- 线程安全，每页面独立锁

**ScreenshotUploadClient** - 截图上传客户端
- 自动上传到Admin服务器
- 返回可访问的URL
- 支持HTTP multipart上传

### A.3 使用示例

#### 基础截图并上传

```java
@Controller
public class MyController extends StreamTaskHelper {
    
    @Autowired
    private ScreenshotUtil screenshotUtil;
    
    @Autowired
    private ScreenshotUploadClient uploadClient;
    
    @StreamCapability(type = "MY_TASK", description = "我的任务")
    public void handleTask(EngineMessage message) {
        String userId = message.getUserId();
        StreamTask task = startStreamTask(userId, requestId);
        
        try {
            // 1. 截图到临时文件
            Path screenshotPath = screenshotUtil.capture(page, "screenshot_name");
            
            // 2. 读取为字节数组
            byte[] imageBytes = Files.readAllBytes(screenshotPath);
            
            // 3. 上传到Admin服务器
            ScreenshotUploadClient.UploadResult result = uploadClient.uploadScreenshot(
                userId,           // 用户ID
                "my_screenshot",  // 文件名（可选）
                imageBytes        // 图片字节数组
            );
            
            // 4. 获取URL
            String screenshotUrl = result.getUrl();
            
            // 5. 推送到前端
            task.sendScreenshot(screenshotUrl);
            
        } finally {
            task.stop();
        }
    }
}
```

#### 完整示例（参考BaiduHotSearchDemoController）

```java
// 截图并上传
if (needScreenshot) {
    task.sendLog("正在截图...");
    
    // 截图到临时文件
    Path screenshotPath = screenshotUtil.capture(
        page, 
        String.format("baidu_hot_%d_%s", clickIndex, requestId)
    );
    
    task.sendLog("截图完成，正在上传...");
    
    // 读取截图文件为字节数组
    byte[] imageBytes = java.nio.file.Files.readAllBytes(screenshotPath);
    
    // 上传到图片服务器
    ScreenshotUploadClient.UploadResult uploadResult = uploadClient.uploadScreenshot(
        userId, 
        String.format("baidu_hot_%d", clickIndex), 
        imageBytes
    );
    
    String screenshotUrl = uploadResult.getUrl();
    task.sendLog("图片上传成功: " + screenshotUrl);
    
    // 推送截图URL到前端
    task.sendScreenshot(screenshotUrl);
    
    // 保存到业务数据
    targetItem.put("screenshotUrl", screenshotUrl);
}
```

### A.4 ScreenshotUtil API

#### capture() - 截图到文件

```java
// 基础截图
Path path = screenshotUtil.capture(page, "screenshot_name");

// 全页面截图（包括滚动区域）
Path path = screenshotUtil.capture(page, "screenshot_name", true);
```

#### captureAsBase64() - 截图返回Base64

```java
String base64 = screenshotUtil.captureAsBase64(page);
```

#### captureElementAsBase64() - 截取元素

```java
String base64 = screenshotUtil.captureElementAsBase64(page, "#qrcode");
```

### A.5 ScreenshotUploadClient API

#### uploadScreenshot() - 上传截图

```java
ScreenshotUploadClient.UploadResult result = uploadClient.uploadScreenshot(
    String userId,      // 用户ID
    String fileName,    // 文件名（可选，不含扩展名）
    byte[] imageBytes   // 图片字节数组
);

// 获取结果
String url = result.getUrl();           // 图片URL
String fileName = result.getFileName(); // 文件名
boolean success = result.isSuccess();   // 是否成功
String message = result.getMessage();   // 错误信息（失败时）
```

### A.6 注意事项

1. **线程安全**: ScreenshotUtil 使用每页面独立锁，支持并发截图
2. **超时时间**: 截图锁等待超时30秒
3. **文件清理**: 临时截图文件在上传后可以删除
4. **URL格式**: 返回的URL格式为 `http://localhost:8080/profile/engine/{userId}/{date}/filename.png`
5. **错误处理**: 上传失败时 `result.isSuccess()` 返回 false，通过 `result.getMessage()` 获取错误信息

---

## 附录B：完整演示代码

框架提供了两个完整的演示Controller，展示了所有核心功能的使用方法：

### B.1 BaiduHotSearchDemoController - 流式输出完整示例

**位置**: `WxFbsir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/BaiduHotSearchDemoController.java`

**演示内容**:
- ✅ Playwright自动化（打开百度、抓取热搜、点击链接）
- ✅ 流式输出（多次TASK_LOG推送进度）
- ✅ 截图上传（TASK_SCREENSHOT推送图片）
- ✅ 数据提取（返回热搜榜单和点击结果）
- ✅ 会话管理（持久化会话、资源清理）
- ✅ 异常处理（完整的错误处理）

**测试命令**:
```bash
{"type": "BAIDU_HOT_SEARCH_DEMO", "engineId": "engine-001", "payload": {"clickIndex": 0, "needScreenshot": true}}
```

### B.2 SimpleHealthCheckDemoController - 单次输出完整示例

**位置**: `WxFbsir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/SimpleHealthCheckDemoController.java`

**演示内容**:
- ✅ 单次返回（不继承StreamTaskHelper）
- ✅ 参数提取（从payload中提取参数）
- ✅ 数据封装（构建结构化返回数据）
- ✅ 消息发送（使用EngineMessage.builder()）
- ✅ 异常处理（完整的错误处理）

**测试命令**:
```bash
{"type": "SIMPLE_HEALTH_CHECK_DEMO", "engineId": "engine-001", "payload": {"includeDetails": true}}
```

### B.3 演示能力使用指南

**位置**: `WxFbsir-engine/src/main/java/com/wx/fbsir/engine/controller/demo/README.md`

该文档包含：
- 两个Controller的详细说明
- 客户端调用示例
- 返回数据格式
- 单次返回 vs 流式返回对比
- 开发新能力的步骤指南
- 最佳实践建议

---

---

## 附录C：企业级并发架构设计

### C.1 并发模型概述

**核心原则**：有N个Playwright实例，就支持N个任务并发

```
8个Playwright实例 = 最多8个任务同时执行
不区分能力类型，只要有空闲实例就执行
资源利用率最大化
```

### C.2 三级控制机制

#### 层级1：全局并发限制（实例级）

```java
// 基于Playwright实例池大小
全局最大并发 = 8个实例（自动检测CPU核心数）
超过限制 → 返回 GLOBAL_LIMIT_EXCEEDED
```

**配置位置**：
```yaml
# application.yml
engine:
  playwright:
    instance-pool:
      size: 0  # 0=自动检测，手动设置如 size: 16
```

#### 层级2：防重复提交

```java
// 同一用户+能力组合只能有1个任务
key = userId:capabilityType
重复提交 → 返回 DUPLICATE_REQUEST
```

**场景示例**：
```
用户A点击"登录检测" → 任务1开始执行
用户A再次点击"登录检测" → 被拒绝（重复提交）❌
用户A点击"节点编辑" → 正常执行（不同能力）✅
```

### C.3 架构组件

```
EngineCapabilityManager（能力管理器）
         ↓
TaskExecutionTracker（任务追踪器）
    • 全局并发信号量（8个permit）
    • 防重复映射（userId:type → taskId）
    • 任务详情（taskId → TaskInfo）
    • 自动超时清理（5分钟）
         ↓
PlaywrightInstancePool（实例池）
    • 8个Playwright实例（可配置）
    • 轮询分配策略
    • 实例隔离
         ↓
BrowserPoolManager（会话池）
    • 持久化会话（登录状态保存）
    • 临时会话（无痕模式）
    • 会话复用
```

### C.4 任务执行流程

```
1. 消息到达 → EngineCapabilityManager.handleMessage()
2. 检查重复 → duplicateCheck.containsKey()
3. 获取信号量 → globalSemaphore.tryAcquire()
4. 执行任务 → ThreadPoolTaskExecutor.execute()
5. 分配实例 → PlaywrightInstancePool.acquire()
6. 创建会话 → BrowserPoolManager.acquire()
7. 业务逻辑 → Controller.handleXXX()
8. 任务完成 → globalSemaphore.release()
```

### C.5 拒绝策略

| 场景 | 错误码 | 说明 |
|------|--------|------|
| 重复提交 | `DUPLICATE_REQUEST` | 任务正在执行中 |
| 全局并发已满 | `GLOBAL_LIMIT_EXCEEDED` | 系统并发已满，当前执行N个任务 |
| 线程池繁忙 | `SYSTEM_BUSY` | 系统繁忙，1-2分钟后重试 |

### C.6 监控接口

#### 健康检查

```bash
GET /api/monitor/health

Response:
{
  "status": "UP",
  "taskStatus": {
    "executing": 5,
    "capacity": 8,
    "utilizationRate": "62.5%",
    "stats": "全局并发: 5/8, 总任务: 120, 完成: 115, 拒绝: 5, 成功率: 95.83%"
  },
  "browserStatus": {
    "activeSessions": 5
  }
}
```

#### 任务统计

```bash
GET /api/monitor/tasks

Response:
{
  "executing": 5,
  "capacity": 8,
  "summary": "全局并发: 5/8, 总任务: 120, 完成: 115"
}
```

### C.7 性能指标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| **并发能力** | 8个任务同时执行 | 基于CPU核心数 |
| **资源利用率** | > 80% | 实例使用效率 |
| **任务成功率** | > 95% | 完成/总任务 |
| **平均响应时间** | < 10秒 | 单任务耗时 |

### C.8 最佳实践

#### 合理设置实例数

```yaml
# 推荐：CPU核心数（自动检测）
engine.playwright.instance-pool.size: 0

# 手动设置
engine.playwright.instance-pool.size: 16  # 16核CPU
```

#### 监控实例利用率

```bash
# 定期检查
curl http://localhost:8080/api/monitor/health

# 利用率 > 80% → 性能良好
# 利用率 < 50% → 资源浪费，可降低实例数
# 利用率 = 100% → 考虑增加实例数
```

#### 关键日志分析

```bash
[任务追踪] 任务已开始 - 全局并发: 5/8
[任务追踪] 任务已完成 - 耗时: 6500ms, 全局并发: 4/8
[任务追踪] 拒绝执行，全局并发已满 - 当前执行: 8/8
[任务追踪] 清理超时任务 - 超时: 300000ms
```

---

**维护者**: WxFbsir Team  
**最后更新**: 2026-01-20

## 📚 相关文档

- [快速上手指南](./快速上手指南.md) - 新手入门必读
- [代码规范](../代码规范.md) - 代码编写规范
