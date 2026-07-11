/**
 * Playwright 企业级浏览器自动化框架
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 框架概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 本框架基于 Microsoft Playwright 构建，解决了 cube-engine 中的以下问题：
 * 
 * 1. 内存泄漏：静态 HashMap 未清理 → 使用池化管理 + 自动过期清理
 * 2. 线程泄漏：多个 ScheduledExecutorService → 统一线程池管理
 * 3. 资源耗尽：无并发控制 → Semaphore 限制最大会话数
 * 4. 复杂重试：15次重试逻辑 → 简化为3次，快速失败
 * 5. 代码耦合：工具类相互依赖 → 清晰的分层架构
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 包结构
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * playwright/
 * ├── config/
 * │   └── PlaywrightProperties.java    - 配置属性（池大小、超时、线程数等）
 * │
 * ├── core/
 * │   ├── PlaywrightManager.java       - Playwright 生命周期管理（单例）
 * │   └── PlaywrightTaskExecutor.java  - 任务线程池（防止线程泄漏）
 * │
 * ├── pool/
 * │   └── BrowserPoolManager.java      - 浏览器会话池（复用、限流、清理）
 * │
 * ├── session/
 * │   └── BrowserSession.java          - 会话抽象（生命周期、状态、资源）
 * │
 * └── util/
 *     ├── ClipboardManager.java        - 剪贴板操作
 *     └── ScreenshotUtil.java          - 截图工具
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 快速开始
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 配置 (application.yml):
 * 
 * ```yaml
 * engine:
 *   playwright:
 *     enabled: true
 *     data-dir: /path/to/browser/data
 *     headless: false
 *     pool:
 *       max-size: 10
 *       session-timeout: 3600000
 *     browser:
 *       launch-timeout: 60000
 *       viewport-width: 1280
 *       viewport-height: 720
 * ```
 * 
 * 2. 基本使用:
 * 
 * ```java
 * @Autowired
 * private BrowserPoolManager browserPool;
 * 
 * // 获取持久化会话（登录状态会保存）
 * try (BrowserSession session = browserPool.acquirePersistent("userId", "baidu")) {
 *     Page page = session.getOrCreatePage();
 *     page.navigate("https://baidu.com");
 *     // 执行操作...
 * } // 自动归还到池
 * ```
 * 
 * 3. 任务执行器使用:
 * 
 * ```java
 * @Autowired
 * private PlaywrightTaskExecutor taskExecutor;
 * 
 * // 同步执行
 * String title = taskExecutor.execute("userId", "task", session -> {
 *     Page page = session.getOrCreatePage();
 *     page.navigate("https://example.com");
 *     return page.title();
 * });
 * 
 * // 异步执行
 * CompletableFuture<String> future = taskExecutor.executeAsync("userId", "task", session -> {
 *     return "result";
 * });
 * ```
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 会话类型
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 持久化会话 (Persistent):
 *    - 数据保存到磁盘
 *    - 适合需要保持登录状态的场景（如 AI 登录）
 *    - 会话关闭后可复用
 *    - 使用: browserPool.acquirePersistent("userId", "baidu")
 * 
 * 2. 临时会话 (Temporary):
 *    - 无痕模式，关闭后数据不保留
 *    - 适合一次性任务
 *    - 会话关闭后立即销毁
 *    - 使用: browserPool.acquireTemporary("taskId")
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 无头/有头模式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 无头模式 (headless=true):  无 GUI，适合服务器环境
 * - 有头模式 (headless=false): 有 GUI，适合调试和需要看到界面的场景
 * 
 * ```java
 * // 指定无头模式
 * browserPool.acquirePersistent("userId", "task", true);  // headless
 * browserPool.acquirePersistent("userId", "task", false); // 有GUI
 * ```
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 并发控制
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 最大会话数由 pool.max-size 控制（默认: CPU核心数*2）
 * - 超过限制时，acquire 会等待直到有可用槽位
 * - 等待超时由 pool.acquire-timeout 控制（默认: 30秒）
 * - 建议根据服务器内存调整 max-size（每个浏览器约 200-500MB）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 资源清理
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 会话超时自动清理（session-timeout，默认1小时）
 * - 定时清理任务（cleanup-interval，默认5分钟）
 * - 应用关闭时自动清理所有会话
 * - try-with-resources 确保会话正确归还
 * 
 * @author FBSir
 * @date 2025-12-16
 * @see com.wx.fbsir.engine.playwright.config.PlaywrightProperties
 * @see com.wx.fbsir.engine.playwright.core.PlaywrightManager
 * @see com.wx.fbsir.engine.playwright.pool.BrowserPoolManager
 * @see com.wx.fbsir.engine.playwright.session.BrowserSession
 */
package com.wx.fbsir.engine.playwright;
