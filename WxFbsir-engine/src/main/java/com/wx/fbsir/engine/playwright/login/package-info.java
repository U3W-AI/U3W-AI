/**
 * Playwright 通用登录状态持久化框架
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 框架概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 【什么是登录状态持久化框架？】
 * 本框架提供统一的登录状态持久化能力，解决不同AI平台（Gitee、DeepSeek、元器等）的登录状态保存和恢复问题。
 * 
 * 【为什么需要这个框架？】
 * - 问题：每次重启服务或关闭浏览器后，用户需要重新扫码登录，体验差
 * - 解决：登录一次后，登录状态永久保存，下次自动恢复，无需重复登录
 * - 优势：统一管理所有平台的登录状态，代码简洁，易于维护和扩展
 * 
 * 【核心特性】
 * 1. 🌐 平台无关：统一的API，支持Gitee、DeepSeek、元器等所有平台
 * 2. 📦 完整性：保存Cookies、LocalStorage、SessionStorage等所有登录信息
 * 3. 💾 持久化：登录状态保存到本地文件，有效期由平台自身管理
 * 4. 🔄 自动恢复：下次使用时自动恢复登录状态，失效时自动重新登录
 * 5. 🚀 易扩展：新增平台只需调用统一API，无需重复编写持久化逻辑
 * 6. ⚙️ 配置化：直接使用application.yml的data-dir，与浏览器数据目录平级
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 包结构
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * playwright/login/
 * ├── model/
 * │   └── LoginState.java              - 登录状态数据模型（Cookie、Storage、元数据）
 * │
 * ├── persistence/
 * │   └── LoginStatePersistence.java   - 登录状态持久化（保存/加载/删除文件）
 * │
 * ├── restorer/
 * │   └── LoginStateRestorer.java      - 登录状态恢复（注入到浏览器会话）
 * │
 * └── manager/
 *     └── LoginStateManager.java       - 统一管理类（门面模式，推荐使用）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 文件存储结构
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 【存储路径】直接使用application.yml配置的data-dir
 * 
 * {data-dir}/                          ← 配置的浏览器数据根目录
 * ├── gitee/                           ← Gitee AI平台登录状态
 * │   ├── user-1/
 * │   │   └── login-state.json         ← 用户1的Gitee登录状态
 * │   └── user-2/
 * │       └── login-state.json         ← 用户2的Gitee登录状态
 * ├── deepseek/                        ← DeepSeek AI平台登录状态
 * │   └── user-1/
 * │       └── login-state.json         ← 用户1的DeepSeek登录状态
 * └── yuanqi/                          ← 元器AI平台登录状态
 *     └── user-1/
 *         └── login-state.json         ← 用户1的元器登录状态
 * 
 * 【设计原则】
 * ✅ 直接使用data-dir作为根目录，与浏览器数据目录平级
 * ✅ 按平台和用户分目录存储，避免冲突
 * ✅ 使用JSON格式，便于调试和手动修改
 * ✅ 不保存锁文件，确保并发安全
 * ✅ 有效期由平台自身管理，框架不检查过期时间
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 快速开始
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录成功后保存状态：
 * 
 * ```java
 * // 在登录成功的Controller中
 * BrowserSession session = browserPool.acquirePersistent(userId, "gitee", false);
 * 
 * // 执行登录操作...
 * String userName = giteeUtil.login(session.getOrCreatePage());
 * 
 * // 保存登录状态
 * LoginStateManager.saveLoginState(session, "gitee", userId, userName);
 * 
 * // 销毁会话（登录状态已持久化）
 * browserPool.destroy(session);
 * ```
 * 
 * 2. 下次使用时恢复状态：
 * 
 * ```java
 * // 在需要使用登录状态的Controller中
 * BrowserSession session = browserPool.acquirePersistent(userId, "gitee", false);
 * 
 * // 恢复登录状态
 * if (LoginStateManager.hasLoginState("gitee", userId)) {
 *     LoginStateManager.restoreLoginState(session, "gitee", userId);
 * }
 * 
 * // 使用已登录的会话...
 * Page page = session.getOrCreatePage();
 * page.navigate("https://chat.gitee.com/");
 * ```
 * 
 * 3. 检查登录状态：
 * 
 * ```java
 * // 检查是否存在登录状态
 * boolean hasLogin = LoginStateManager.hasLoginState("gitee", userId);
 * 
 * // 检查登录状态是否有效（存在且未过期）
 * boolean isValid = LoginStateManager.isLoginStateValid("gitee", userId);
 * 
 * // 获取登录状态详情
 * LoginState state = LoginStateManager.getLoginState("gitee", userId);
 * if (state != null) {
 *     System.out.println("用户名: " + state.getUserName());
 *     System.out.println("Cookie数量: " + state.getCookieCount());
 *     System.out.println("保存时间: " + new Date(state.getTimestamp()));
 * }
 * ```
 * 
 * 4. 清除登录状态：
 * 
 * ```java
 * // 删除登录状态文件
 * LoginStateManager.clearLoginState("gitee", userId);
 * 
 * // 或者只标记为无效（不删除文件）
 * LoginStateManager.invalidateLoginState("gitee", userId);
 * ```
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心类说明
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. LoginState（数据模型）
 *    - 封装登录状态的所有信息
 *    - 包含：platform、userId、userName、cookies、origins、metadata等
 *    - 支持JSON序列化/反序列化
 *    - 提供过期检测、Cookie统计等工具方法
 * 
 * 2. LoginStatePersistence（持久化层）
 *    - save(LoginState)：保存登录状态到文件
 *    - load(platform, userId)：从文件加载登录状态
 *    - exists(platform, userId)：检查文件是否存在
 *    - delete(platform, userId)：删除登录状态文件
 * 
 * 3. LoginStateRestorer（恢复层）
 *    - restore(BrowserSession, LoginState)：恢复Cookies到浏览器
 *    - restoreStorageToPage(Page, LoginState)：恢复LocalStorage/SessionStorage
 * 
 * 4. LoginStateManager（门面层，推荐使用）
 *    - saveLoginState()：保存登录状态
 *    - restoreLoginState()：恢复登录状态
 *    - hasLoginState()：检查是否存在
 *    - isLoginStateValid()：检查是否有效
 *    - clearLoginState()：清除登录状态
 *    - invalidateLoginState()：标记为无效
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 技术细节
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. Cookie恢复机制：
 *    - 读取JSON文件中的所有Cookie
 *    - 构建Playwright Cookie对象
 *    - 设置domain、path、expires、httpOnly、secure、sameSite等属性
 *    - 通过BrowserContext.addCookies()批量注入
 *    - 支持跨域Cookie（如：.gitee.com → chat.gitee.com）
 * 
 * 2. Storage恢复机制：
 *    - LocalStorage和SessionStorage需要在页面加载后通过JavaScript注入
 *    - 使用Page.evaluate()执行window.localStorage.setItem()
 *    - 自动转义JavaScript字符串，防止注入攻击
 * 
 * 3. 登录状态有效性判断：
 *    - ❌ 不能通过检查文件是否存在来判断有效性
 *    - ❌ 文件存在 ≠ 平台服务器上的登录状态有效
 *    - ✅ 唯一有效的判断方式：打开页面后检查登录标志
 *    - ✅ 如果页面上检测到登录标志（如头像、用户名） → 登录状态有效
 *    - ✅ 如果未检测到登录标志 → 登录状态已失效，需要重新登录
 *    - 框架只负责保存和恢复，由平台自身决定是否有效
 * 
 * 4. 并发安全：
 *    - 不使用锁文件，避免死锁
 *    - 文件操作使用原子写入（TRUNCATE_EXISTING）
 *    - 支持多进程并发访问
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 最佳实践
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 登录流程：
 *    - 扫码/密码登录成功后，立即保存登录状态
 *    - 等待2-3秒让页面稳定（确保Cookie完全加载）
 *    - 保存后立即销毁浏览器会话，释放资源
 * 
 * 2. 恢复流程：
 *    - 创建浏览器会话后，立即恢复登录状态
 *    - 在页面导航前恢复Cookies
 *    - 在页面导航后恢复LocalStorage/SessionStorage（如需要）
 * 
 * 3. 有效性判断（⭐ 重要）：
 *    - ❌ 不要依赖文件存在来判断有效性
 *    - ✅ 必须通过页面打开后的登录检测来判断
 *    - ✅ 检查页面上的登录标志（头像、用户名、登录按钮等）
 *    - ✅ 如果检测到登录标志 → 登录状态有效
 *    - ✅ 如果未检测到登录标志 → 调用invalidateLoginState()标记为无效，提示用户重新登录
 * 
 * 4. 错误处理：
 *    - 登录状态恢复失败不应阻断业务流程
 *    - 记录详细日志，便于排查问题
 *    - 提供降级方案（如：重新登录）
 * 
 * 5. 扩展新平台：
 *    - 只需调用LoginStateManager的统一API
 *    - 平台标识使用小写英文（如：gitee、deepseek、kimi）
 *    - 无需修改框架代码
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用示例（完整流程）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * Gitee扫码登录示例：
 * 
 * ```java
 * public void handleGiteeScanLogin(String userId) {
 *     BrowserSession session = null;
 *     try {
 *         // 1. 创建浏览器会话
 *         session = browserPool.acquirePersistent(userId, "gitee", false);
 *         Page page = session.getOrCreatePage();
 *         
 *         // 2. 执行扫码登录
 *         page.navigate("https://gitee.com/login");
 *         String userName = waitForQrCodeLogin(page);
 *         
 *         // 3. 登录成功后导航到聊天页（确保跨域Cookie同步）
 *         page.navigate("https://chat.gitee.com/");
 *         Thread.sleep(2000);
 *         
 *         // 4. 保存登录状态
 *         LoginStateManager.saveLoginState(session, "gitee", userId, userName);
 *         
 *     } finally {
 *         // 5. 销毁会话（登录状态已持久化）
 *         if (session != null) {
 *             browserPool.destroy(session);
 *         }
 *     }
 * }
 * ```
 * 
 * Gitee AI咨询示例：
 * 
 * ```java
 * public void handleGiteeQuery(String userId, String question) {
 *     BrowserSession session = null;
 *     try {
 *         // 1. 创建浏览器会话
 *         session = browserPool.acquirePersistent(userId, "gitee", false);
 *         
 *         // 2. 恢复登录状态
 *         if (LoginStateManager.hasLoginState("gitee", userId)) {
 *             LoginStateManager.restoreLoginState(session, "gitee", userId);
 *         }
 *         
 *         // 3. 使用已登录的会话
 *         Page page = session.getOrCreatePage();
 *         page.navigate("https://chat.gitee.com/");
 *         
 *         // 4. 发送问题并获取回复
 *         String answer = sendQuestion(page, question);
 *         
 *     } finally {
 *         if (session != null) {
 *             browserPool.destroy(session);
 *         }
 *     }
 * }
 * ```
 * 
 * @author 15年高级Java开发工程师
 * @since 2026-02-03
 */
package com.wx.fbsir.engine.playwright.login;
