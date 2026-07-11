package com.wx.fbsir.engine.playwright.pool;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.wx.fbsir.engine.playwright.config.BrowserLaunchOptionsFactory;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.core.PlaywrightManager;
import com.wx.fbsir.engine.playwright.core.PlaywrightInstancePool;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import com.wx.fbsir.engine.playwright.util.ClipboardManager;
import com.wx.fbsir.engine.playwright.util.SafePathResolver;
import com.wx.fbsir.engine.playwright.util.ScreenshotUtil;
import com.wx.fbsir.engine.util.BrowserSessionLockUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 浏览器池管理器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 管理浏览器会话池（创建、复用、回收）
 * 2. 支持持久化会话和临时会话
 * 3. 支持有头模式和无头模式
 * 4. 自动清理过期会话
 * 5. 并发控制，防止资源耗尽
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 设计亮点（解决 cube-engine 问题）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 并发安全：使用 ConcurrentHashMap 和 Semaphore
 * 2. 资源限制：最大会话数限制，防止 OOM
 * 3. 自动清理：定时任务清理过期会话
 * 4. 优雅关闭：PreDestroy 确保资源释放
 * 5. 简化重试：移除复杂的重试逻辑，失败快速返回
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用方式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```java
 * @Autowired
 * private BrowserPoolManager browserPool;
 * 
 * // 获取持久化会话（数据保存到磁盘）
 * try (BrowserSession session = browserPool.acquirePersistent("userId", "baidu")) {
 *     Page page = session.getOrCreatePage();
 *     page.navigate("https://baidu.com");
 * }
 * 
 * // 获取临时会话（无痕模式）
 * try (BrowserSession session = browserPool.acquireTemporary("taskId")) {
 *     Page page = session.getOrCreatePage();
 *     page.navigate("https://example.com");
 * }
 * 
 * // 指定无头/有头模式
 * try (BrowserSession session = browserPool.acquire("userId", "task", true, true)) {
 *     // persistent=true, headless=true
 * }
 * ```
 * 
 * @author FBSir
 * @date 2025-12-16
 */
@Component
public class BrowserPoolManager {

    private static final Logger log = LoggerFactory.getLogger(BrowserPoolManager.class);

    private final PlaywrightManager playwrightManager;
    private final PlaywrightInstancePool playwrightInstancePool;  // ✅ 新增：实例池
    private final PlaywrightProperties properties;
    private final ClipboardManager clipboardManager;
    private final ScreenshotUtil screenshotUtil;
    private final GlobalBrowserPool globalBrowserPool;

    /**
     * 持久化会话池（按 userId+name 索引）
     * <p>⚠️ 并发场景：
     * <ul>
     *   <li>场景1：用户A同时打开2个标签页访问同一能力 → 需要复用同一Session</li>
     *   <li>场景2：用户A访问能力1，用户B同时访问能力2 → 完全隔离</li>
     *   <li>场景3：用户A长时间未操作，Session被清理，此时重新访问 → 创建新Session</li>
     * </ul>
     * <p>🔒 线程安全：ConcurrentHashMap + computeIfAbsent确保原子性
     */
    private final ConcurrentHashMap<String, BrowserSession> persistentSessions = new ConcurrentHashMap<>();

    /**
     * 临时会话池（按 sessionId 索引）
     * <p>⚠️ 并发场景：
     * <ul>
     *   <li>场景1：批量任务同时创建100个临时Session → Semaphore限流</li>
     *   <li>场景2：Session用完立即销毁 → 快速回收资源</li>
     * </ul>
     * <p>🔒 线程安全：ConcurrentHashMap保证put/remove原子性
     */
    private final ConcurrentHashMap<String, BrowserSession> temporarySessions = new ConcurrentHashMap<>();

    /**
     * 并发控制信号量（防止资源耗尽）
     * <p>⚠️ 边界场景：
     * <ul>
     *   <li>场景1：100个并发请求同时到达 → Semaphore排队，超时拒绝</li>
     *   <li>场景2：Session创建失败 → 必须release()，否则永久泄漏</li>
     * </ul>
     * <p>🔒 关键：acquire和release必须成对出现，使用try-finally确保
     */
    private Semaphore semaphore;

    /**
     * 当前活跃会话数（监控用）
     * <p>📊 用途：实时监控资源使用情况，与Semaphore配合判断负载
     */
    private final AtomicInteger activeCount = new AtomicInteger(0);
    
    /**
     * 总创建会话数（用于资源监控）
     */
    private final AtomicInteger totalCreatedCount = new AtomicInteger(0);
    
    /**
     * 总销毁会话数（用于资源监控）
     */
    private final AtomicInteger totalDestroyedCount = new AtomicInteger(0);

    /**
     * 是否已关闭
     */
    private volatile boolean shutdown = false;

    public BrowserPoolManager(PlaywrightManager playwrightManager, 
                               PlaywrightInstancePool playwrightInstancePool,
                               PlaywrightProperties properties,
                               ClipboardManager clipboardManager, 
                               ScreenshotUtil screenshotUtil,
                               GlobalBrowserPool globalBrowserPool) {
        this.playwrightManager = playwrightManager;
        this.playwrightInstancePool = playwrightInstancePool;
        this.properties = properties;
        this.clipboardManager = clipboardManager;
        this.screenshotUtil = screenshotUtil;
        this.globalBrowserPool = globalBrowserPool;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("[浏览器池] 已禁用");
            return;
        }
        
        // 初始化信号量
        this.semaphore = new Semaphore(properties.getPool().getMaxSize());
        
        // 确保数据目录存在
        try {
            Path dataDir = Paths.get(properties.getDataDir());
            if (!Files.exists(dataDir)) {
                Files.createDirectories(dataDir);
            }
        } catch (Exception e) {
            log.warn("[浏览器池] 创建数据目录失败: {}", e.getMessage());
        }
        
        log.info("[浏览器池] 初始化完成 - 最大会话数: {}, 数据目录: {}", 
            properties.getPool().getMaxSize(), properties.getDataDir());
    }

    // ==================== 会话获取方法 ====================

    /**
     * 获取持久化会话
     * 会话数据保存到磁盘，下次启动时可复用（如登录状态）
     * 
     * @param userId 用户ID
     * @param name 会话名称（如 "baidu", "deepseek"）
     * @return BrowserSession
     */
    public BrowserSession acquirePersistent(String userId, String name) {
        return acquire(userId, name, true, properties.isHeadless());
    }

    /**
     * 获取持久化会话（指定无头模式）
     */
    public BrowserSession acquirePersistent(String userId, String name, boolean headless) {
        return acquire(userId, name, true, headless);
    }

    /**
     * 获取临时会话（无痕模式）
     * 会话关闭后数据不保留
     * 
     * @param taskId 任务ID
     * @return BrowserSession
     */
    public BrowserSession acquireTemporary(String taskId) {
        return acquire(taskId, "temp", false, properties.isHeadless());
    }

    /**
     * 获取临时会话（指定无头模式）
     */
    public BrowserSession acquireTemporary(String taskId, boolean headless) {
        return acquire(taskId, "temp", false, headless);
    }

    /**
     * 获取浏览器会话（完整参数）
     * 
     * @param userId 用户ID
     * @param name 会话名称
     * @param persistent 是否持久化
     * @param headless 是否无头模式
     * @return BrowserSession
     */
    public BrowserSession acquire(String userId, String name, boolean persistent, boolean headless) {
        return acquire(userId, name, null, persistent, headless);
    }
    
    /**
     * 获取浏览器会话（支持实例ID，用于同一用户多浏览器实例隔离）
     * 
     * <p>⚠️ 关键并发场景分析：
     * <pre>
     * 场景1：用户A同时发起2个请求访问baidu能力
     *   线程1: acquire("userA", "baidu", null, true, true)
     *   线程2: acquire("userA", "baidu", null, true, true)
     *   期望：线程1创建Session，线程2复用
     *   实现：computeIfAbsent原子性保证只创建一次
     * 
     * 场景2：用户A访问baidu，用户B访问google
     *   线程1: acquire("userA", "baidu", ...)
     *   线程2: acquire("userB", "google", ...)
     *   期望：完全隔离，互不影响
     *   实现：不同key，ConcurrentHashMap保证隔离
     * 
     * 场景3：同一用户多实例隔离（如多开微信）
     *   线程1: acquire("userA", "wechat", "instance1", ...)
     *   线程2: acquire("userA", "wechat", "instance2", ...)
     *   期望：两个独立Session，互不干扰
     *   实现：instanceId区分不同实例
     * 
     * 场景4：100个并发请求同时到达
     *   期望：Semaphore限流，超过最大值的请求等待或拒绝
     *   实现：tryAcquire(timeout) + 清晰的错误提示
     * </pre>
     * 
     * @param userId 用户ID（用于用户隔离）
     * @param name 会话名称（能力名称，如"baidu", "wechat"）
     * @param instanceId 实例ID（同一用户多实例隔离，可为null）
     * @param persistent 是否持久化（true=数据保存到磁盘，false=无痕模式）
     * @param headless 是否无头模式（true=后台运行，false=显示窗口）
     * @return BrowserSession
     */
    public BrowserSession acquire(String userId, String name, String instanceId, boolean persistent, boolean headless) {
        if (shutdown) {
            throw new IllegalStateException("BrowserPoolManager 已关闭");
        }

        SafePathResolver.requireSafeSegment(userId, "userId");
        SafePathResolver.requireSafeSegment(name, "name");
        if (instanceId != null) {
            SafePathResolver.requireSafeSegment(instanceId, "instanceId");
        }
        
        // 构建会话键：支持实例ID隔离
        // 格式：userId:name 或 userId:name:instanceId
        String key = instanceId != null ? buildKey(userId, name, instanceId) : buildKey(userId, name);
        
        if (persistent) {
            BrowserSession existing = persistentSessions.get(key);
            // A lease can exceed its nominal TTL while a browser action is still running.
            // Never evict an in-use session solely because its validity window elapsed.
            if (existing != null && !existing.isValid() && !existing.isInUse()) {
                // 只有确认失效且仍是同一个映射时才移除，避免误删正在使用的会话。
                if (persistentSessions.remove(key, existing)) {
                    destroySessionQuietly(existing);
                    activeCount.decrementAndGet();
                    semaphore.release();
                }
                existing = null;
            }

            if (existing != null) {
                long deadline = System.currentTimeMillis() + properties.getPool().getAcquireTimeout();
                do {
                    if (existing.acquire(name)) {
                        log.debug("[浏览器池] 复用持久化会话: {}", key);
                        return wrapSession(existing);
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("等待持久化会话被中断: " + key, e);
                    }
                } while (System.currentTimeMillis() < deadline && existing.isValid());

                throw new RuntimeException("持久化会话正在被其他任务占用: " + key);
            }
        }
        
        // 获取信号量（等待可用槽位）
        boolean semaphoreAcquired = false;
        BrowserSession session = null;
        boolean sessionCreated = false;
        
        try {
            semaphoreAcquired = semaphore.tryAcquire(properties.getPool().getAcquireTimeout(), TimeUnit.MILLISECONDS);
            if (!semaphoreAcquired) {
                int available = semaphore.availablePermits();
                log.error("[浏览器池] 资源耗尽 - 活跃会话: {}, 最大: {}, 可用槽位: {}, 等待超时: {}ms | 建议: 1) 稍后重试 2) 增加浏览器池大小",
                    activeCount.get(), properties.getPool().getMaxSize(), available, properties.getPool().getAcquireTimeout());
                throw new RuntimeException("浏览器池繁忙，当前" + activeCount.get() + "个活跃会话，请稍后重试");
            }
            
            // 创建新会话（使用全局Browser池）
            session = createSession(userId, name, instanceId, persistent, headless);
            sessionCreated = true;
            activeCount.incrementAndGet();
            totalCreatedCount.incrementAndGet();
            
            // 🔴 P0修复：加入池时存在竞态条件
            // 旧问题：线程1和线程2同时创建Session，put()时后者覆盖前者 → 资源泄漏
            // 新方案：使用putIfAbsent原子性操作
            if (persistent) {
                BrowserSession finalSession = session;
                BrowserSession existing = persistentSessions.putIfAbsent(key, session);
                if (existing != null) {
                    // 另一个线程已经放入了Session，销毁当前创建的
                    log.warn("[浏览器池] 并发创建冲突，销毁多余Session: {}", key);
                    destroySessionQuietly(session);
                    activeCount.decrementAndGet();
                    semaphore.release();
                    semaphoreAcquired = false;
                    // 使用已存在的Session
                    session = existing;
                    sessionCreated = false; // 标记未成功创建
                    if (!session.acquire(name)) {
                        // 极端情况：existing也无法获取，抛异常让外层重试
                        throw new RuntimeException("持久化会话被其他线程占用: " + key);
                    }
                }
            } else {
                // 临时Session使用唯一sessionId，无并发冲突
                temporarySessions.put(session.getSessionId(), session);
            }
            
            // The concurrent persistent-session path has already acquired the
            // winning session above. Acquiring it a second time is misleading
            // today and would become incorrect if BrowserSession tracks lease depth.
            if (sessionCreated) {
                session.acquire(name);
            }
            log.debug("[浏览器池] 创建新会话: {} (持久化={}, 无头={}, 总创建={})", 
                key, persistent, headless, totalCreatedCount.get());
            
            return wrapSession(session);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            cleanupFailedAcquisition(semaphoreAcquired, sessionCreated, session);
            log.error("[浏览器池] 获取会话被中断 - 用户: {}, 会话: {}", userId, name);
            throw new RuntimeException("获取浏览器会话被中断", e);
        } catch (Exception e) {
            cleanupFailedAcquisition(semaphoreAcquired, sessionCreated, session);
            log.error("[浏览器池] 创建会话失败 - 用户: {}, 错误: {}", userId, e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * 清理失败的会话获取（确保资源不泄漏）
     */
    private void cleanupFailedAcquisition(boolean semaphoreAcquired, boolean sessionCreated, BrowserSession session) {
        if (sessionCreated && session != null) {
            try {
                session.destroy();
                activeCount.decrementAndGet();
            } catch (Exception e) {
                log.warn("[浏览器池] 清理失败会话异常: {}", e.getMessage());
            }
        }
        if (semaphoreAcquired) {
            semaphore.release();
        }
    }

    /**
     * 释放会话（归还到池）
     */
    public void release(BrowserSession session) {
        if (session == null) return;

        if (!session.releaseIfAcquired()) {
            log.debug("[浏览器池] 忽略重复释放 - 会话: {}", session.getSessionId());
            return;
        }
        
        // 临时会话：立即销毁
        if (!session.isPersistent()) {
            temporarySessions.remove(session.getSessionId());
            destroySessionQuietly(session);
            activeCount.decrementAndGet();
            semaphore.release();
            log.debug("[浏览器池] 释放临时会话 - 会话: {}, 活跃: {}", session.getSessionId(), activeCount.get());
        }
        // 持久化会话：保留在池中，但不释放 Semaphore（会话仍然占用资源）
    }
    
    /**
     * 销毁会话（完全释放资源）
     * 
     * 🔥 关键区别：
     * - release(): 归还到池，持久化会话可重用（不释放 Semaphore）
     * - destroy(): 完全销毁，释放所有资源包括 Semaphore
     * 
     * 使用场景：
     * - 登录检测完成后：调用 destroy() 释放资源
     * - AI会话中：调用 release() 保留会话
     */
    public void destroy(BrowserSession session) {
        if (session == null) return;

        String key = session.getInstanceId() != null
            ? buildKey(session.getUserId(), session.getName(), session.getInstanceId())
            : buildKey(session.getUserId(), session.getName());

        boolean removed = session.isPersistent()
            ? persistentSessions.remove(key, session)
            : temporarySessions.remove(session.getSessionId(), session);

        if (!removed) {
            // 资源仍应安全关闭，但未被本池跟踪时不能修改池计数或许可。
            destroySessionQuietly(session);
            log.debug("[浏览器池] 会话已移除或未被跟踪，跳过重复计数 - 会话: {}", session.getSessionId());
            return;
        }
        
        // 销毁会话
        destroySessionQuietly(session);
        
        // 🔥 关键：释放 Semaphore（无论持久化还是临时）
        int active = activeCount.decrementAndGet();
        semaphore.release();
        
        log.debug("[浏览器池] 销毁会话 - 用户: {}, 名称: {}, 持久化: {}, 活跃: {}/{}", 
            session.getUserId(), session.getName(), session.isPersistent(), active, semaphore.availablePermits());
    }

    /**
     * 强制关闭指定会话
     */
    public void closeSession(String userId, String name) {
        String key = buildKey(userId, name);
        BrowserSession session = persistentSessions.get(key);
        if (session != null) {
            destroy(session);
            log.info("[浏览器池] 关闭会话: {}", key);
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 创建浏览器会话
     * 
     * 包含重试机制，确保在短暂故障时能够恢复
     * 
     * @param userId 用户ID
     * @param name 会话名称
     * @param instanceId 实例ID
     * @param persistent 是否持久化
     * @param headless 是否无头模式
     * @return BrowserSession
     */
    private BrowserSession createSession(String userId, String name, String instanceId, boolean persistent, boolean headless) {
        // 🔐 使用会话级锁（userId:name），防止同一会话的重复并发创建
        // ✅ 允许：1:deepseek 和 1:yuanqi 并发创建（不同会话）
        // ✅ 阻止：两个 1:deepseek 并发创建（相同会话，避免重复）
        String sessionKey = BrowserSessionLockUtil.buildSessionKey(userId, name);
        java.util.concurrent.locks.Lock sessionLock = BrowserSessionLockUtil.getSessionLock(sessionKey);
        
        sessionLock.lock();
        try {
            log.debug("[浏览器池] 获取会话锁 - 会话: {}", sessionKey);
            return doCreateSessionWithRetry(userId, name, instanceId, persistent, headless);
        } finally {
            sessionLock.unlock();
            log.debug("[浏览器池] 释放会话锁 - 会话: {}", sessionKey);
        }
    }
    
    /**
     * 实际执行会话创建并重试
     */
    private BrowserSession doCreateSessionWithRetry(String userId, String name, String instanceId, boolean persistent, boolean headless) {
        PlaywrightProperties.BrowserConfig browserConfig = properties.getBrowser();
        int maxRetries = browserConfig.getMaxRetries();
        long retryInterval = browserConfig.getRetryInterval();
        
        // 锁文件可能属于仍在运行的浏览器进程。仅在显式开启时清理，默认安全失败。
        if (persistent && browserConfig.isCleanupStaleLocks()) {
            try {
                cleanupBrowserLockFiles(userId, name, instanceId);
            } catch (Exception e) {
                log.debug("[浏览器池] 预清理锁文件异常: {}", e.getMessage());
            }
        }
        
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            Browser browser = null;
            BrowserContext context = null;
            
            try {
                // 创建浏览器上下文（可能返回 Browser 和 Context）
                BrowserContextResult result = doCreateBrowserContext(userId, name, instanceId, persistent, headless);
                browser = result.browser;
                context = result.context;
                
                // 授予剪贴板权限
                try {
                    context.grantPermissions(Arrays.asList("clipboard-read", "clipboard-write"));
                } catch (Exception e) {
                    log.debug("[浏览器池] 授予剪贴板权限失败 - 用户: {}, 错误: {}", userId, e.getMessage());
                }
                
                log.debug("[浏览器池] 创建会话成功 - 用户: {}, 名称: {}, 尝试次数: {}", 
                    userId, name, attempt);
                
                // 返回包含 Browser 的会话（仅临时会话）
                return new BrowserSession(userId, name, browser, context, instanceId, persistent, headless, 
                    properties.getPool().getSessionTimeout());
                    
            } catch (com.microsoft.playwright.TimeoutError e) {
                lastException = e;
                log.warn("[浏览器池] 创建会话超时 - 用户: {}, 尝试: {}/{}, 错误: {}", 
                    userId, attempt, maxRetries, e.getMessage());
                // 清理失败时创建的资源
                cleanupFailedResources(browser, context);
            } catch (com.microsoft.playwright.impl.TargetClosedError e) {
                lastException = e;
                log.warn("[浏览器池] 浏览器目标已关闭 - 用户: {}, 尝试: {}/{}, 错误: {}", 
                    userId, attempt, maxRetries, e.getMessage());
                // 清理失败时创建的资源
                cleanupFailedResources(browser, context);
                if (browserConfig.isCleanupStaleLocks()) {
                    cleanupBrowserLockFiles(userId, name, instanceId);
                }
            } catch (Exception e) {
                lastException = e;
                // 最后一次失败时输出完整堆栈，其他次只记录错误信息
                if (attempt == maxRetries) {
                    log.error("[浏览器池] 创建会话失败 - 用户: {}, 尝试: {}/{}, 错误: {}", 
                        userId, attempt, maxRetries, e.getMessage(), e);
                } else {
                    log.warn("[浏览器池] 创建会话失败 - 用户: {}, 尝试: {}/{}, 错误: {}", 
                        userId, attempt, maxRetries, e.getMessage());
                }
                // 清理失败时创建的资源
                cleanupFailedResources(browser, context);
            }
            
            // 如果不是最后一次尝试，等待后重试
            if (attempt < maxRetries) {
                try {
                    Thread.sleep(retryInterval * attempt); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("[浏览器池] 创建会话被中断 - 用户: " + userId, ie);
                }
            }
        }
        
        // 所有重试均失败，这是严重错误，必须告知用户
        String errorMsg = String.format(
            "无法创建浏览器会话 - 用户: %s, 已重试 %d 次\n" +
            "可能原因: 1) Playwright 未正确安装 2) 系统资源不足 3) 浏览器进程崩溃\n" +
            "建议: 1) 检查 Playwright 安装 2) 重启 Engine 服务 3) 检查系统资源",
            userId, maxRetries);
        log.error("{}", errorMsg, lastException);
        throw new RuntimeException(errorMsg, lastException);
    }
    
    /**
     * 浏览器上下文创建结果（包含Browser和Context）
     */
    private static class BrowserContextResult {
        final Browser browser;  // 仅临时会话有值
        final BrowserContext context;
        
        BrowserContextResult(Browser browser, BrowserContext context) {
            this.browser = browser;
            this.context = context;
        }
    }
    
    /**
     * 实际创建浏览器上下文
     * 返回 Browser 和 Context，确保临时会话的 Browser 能被跟踪和关闭
     */
    private BrowserContextResult doCreateBrowserContext(String userId, String name, String instanceId, 
                                                         boolean persistent, boolean headless) {
        PlaywrightProperties.BrowserConfig browserConfig = properties.getBrowser();
        if (persistent) {
            // 🎯 从实例池获取 Playwright 实例（Round-Robin 分配）
            Playwright playwright = playwrightInstancePool.acquirePlaywright();
            BrowserType browserType = playwright.chromium();
            
            // 持久化上下文：支持实例ID隔离
            Path dataRoot = Paths.get(properties.getDataDir());
            Path userDataPath = instanceId != null
                ? SafePathResolver.resolveUnder(dataRoot, name, userId, instanceId)
                : SafePathResolver.resolveUnder(dataRoot, name, userId);
            
            try {
                if (!Files.exists(userDataPath)) {
                    Files.createDirectories(userDataPath);
                    log.debug("[浏览器池] 创建用户数据目录: {}", userDataPath);
                }
            } catch (Exception e) {
                log.warn("[浏览器池] 创建用户数据目录失败 - 路径: {}, 错误: {}", userDataPath, e.getMessage());
            }
            
            // 🔒 实例级锁：每个 Playwright 实例独立加锁
            // 优势：不同实例可以并发创建，只有同一实例的调用才串行
            java.util.concurrent.locks.ReentrantLock instanceLock = playwrightInstancePool.getLockForInstance(playwright);
            instanceLock.lock();
            try {
                log.debug("[浏览器池] 获取 Playwright 实例锁 - 用户: {}, 会话: {}", userId, name);
                
                // 🟠 P1修复：设置超时，防止页面加载卡死
                BrowserContext context = browserType.launchPersistentContext(userDataPath,
                    BrowserLaunchOptionsFactory.createPersistentContextOptions(properties, headless));
                
                log.debug("[浏览器池] 释放 Playwright 实例锁 - 用户: {}, 会话: {}", userId, name);
                
                BrowserLaunchOptionsFactory.configureContextTimeouts(context, properties);
                
                // 持久化上下文不返回 Browser（由 Playwright 内部管理）
                return new BrowserContextResult(null, context);
            } finally {
                instanceLock.unlock();
            }
        } else {
            // 临时上下文：从全局Browser池获取Browser（性能优化）
            Browser browser = globalBrowserPool.acquireBrowser();
            
            try {
                // 🟠 P1修复：设置超时，防止页面加载卡死
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setViewportSize(browserConfig.getViewportWidth(), browserConfig.getViewportHeight()));
                
                BrowserLaunchOptionsFactory.configureContextTimeouts(context, properties);
                
                // 返回 Browser 和 Context，确保两者都能被正确关闭
                return new BrowserContextResult(browser, context);
            } catch (Exception e) {
                // 创建Context失败，归还Browser到池
                globalBrowserPool.releaseBrowser(browser);
                throw e;
            }
        }
    }
    
    /**
     * 清理创建失败时的资源
     * 确保不留下僵尸进程
     */
    private void cleanupFailedResources(Browser browser, BrowserContext context) {
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                log.debug("[浏览器池] 清理失败的Context异常: {}", e.getMessage());
            }
        }
        if (browser != null) {
            try {
                if (browser.isConnected()) {
                    browser.close();
                }
            } catch (Exception e) {
                log.debug("[浏览器池] 清理失败的Browser异常: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 清理浏览器锁文件（增强版：支持NFS等远程文件系统）
     * 当浏览器异常退出时，可能留下锁文件导致无法重新启动
     */
    private void cleanupBrowserLockFiles(String userId, String name, String instanceId) {
        try {
            Path dataRoot = Paths.get(properties.getDataDir());
            Path userDataPath = instanceId != null
                ? SafePathResolver.resolveUnder(dataRoot, name, userId, instanceId)
                : SafePathResolver.resolveUnder(dataRoot, name, userId);
            if (!Files.exists(userDataPath)) {
                return;
            }
            
            // 等待一小段时间，确保进程完全退出
            Thread.sleep(800);
            
            // 清理 SingletonLock 文件（最常见的锁文件）
            Path singletonLock = userDataPath.resolve("SingletonLock");
            if (Files.exists(singletonLock)) {
                forceDeleteLockFile(singletonLock, "SingletonLock");
            }
            
            // 清理其他锁文件（包括 .lock 后缀）
            try (var stream = Files.list(userDataPath)) {
                stream.filter(p -> {
                    String fileName = p.getFileName().toString();
                    return fileName.contains("Lock") || 
                           fileName.contains("lock") ||
                           fileName.endsWith(".lock");
                }).forEach(lockFile -> {
                    forceDeleteLockFile(lockFile, lockFile.getFileName().toString());
                });
            }
        } catch (Exception e) {
            log.debug("[浏览器池] 清理锁文件异常: {}", e.getMessage());
        }
    }
    
    /**
     * 强制删除锁文件（支持NFS等远程文件系统）
     * 
     * @param lockFile 锁文件路径
     * @param fileName 文件名（用于日志）
     */
    private void forceDeleteLockFile(Path lockFile, String fileName) {
        boolean deleted = false;
        
        // 策略1：常规删除（5次重试）
        for (int i = 0; i < 5; i++) {
            try {
                Files.deleteIfExists(lockFile);
                if (!Files.exists(lockFile)) {
                    log.info("[浏览器池] 清理锁文件成功: {} (常规删除, 尝试{}次)", fileName, i + 1);
                    deleted = true;
                    break;
                }
            } catch (Exception e) {
                if (i < 4) {
                    try {
                        Thread.sleep(300 * (i + 1)); // 递增等待：300ms, 600ms, 900ms...
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    log.debug("[浏览器池] 常规删除失败: {} - {}", fileName, e.getMessage());
                }
            }
        }
        
        // 策略2：如果常规删除失败，尝试强制清空文件内容后删除（解决NFS锁问题）
        if (!deleted && Files.exists(lockFile)) {
            try {
                // 步骤1：清空文件内容（解除文件锁）
                java.nio.file.StandardOpenOption[] options = {
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
                };
                Files.write(lockFile, new byte[0], options);
                
                Thread.sleep(200);
                
                // 步骤2：尝试删除
                Files.deleteIfExists(lockFile);
                
                if (!Files.exists(lockFile)) {
                    log.info("[浏览器池] 清理锁文件成功: {} (强制清空后删除)", fileName);
                    deleted = true;
                }
            } catch (Exception e) {
                log.debug("[浏览器池] 强制清空失败: {} - {}", fileName, e.getMessage());
            }
        }
        
        // 策略3：如果仍然失败，尝试重命名为临时文件（避免阻塞后续启动）
        if (!deleted && Files.exists(lockFile)) {
            try {
                Path tempFile = lockFile.getParent().resolve(fileName + ".tmp." + System.currentTimeMillis());
                Files.move(lockFile, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                
                // 异步删除临时文件
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000);
                        Files.deleteIfExists(tempFile);
                    } catch (Exception e) {
                        // 忽略
                    }
                });
                
                log.info("[浏览器池] 清理锁文件成功: {} (重命名为临时文件)", fileName);
                deleted = true;
            } catch (Exception e) {
                log.debug("[浏览器池] 重命名失败: {} - {}", fileName, e.getMessage());
            }
        }
        
        // 最终失败警告
        if (!deleted && Files.exists(lockFile)) {
            log.warn("[浏览器池] 无法清理锁文件: {}，可能需要手动删除或重启服务", fileName);
        }
    }

    /**
     * 包装会话（设置自动清理回调）
     */
    private BrowserSession wrapSession(BrowserSession session) {
        session.setOnClose(() -> release(session));
        // 设置页面关闭回调，自动清理剪贴板锁和截图锁
        session.setOnPageClose(page -> {
            if (clipboardManager != null) {
                clipboardManager.cleanupPageLock(page);
            }
            if (screenshotUtil != null) {
                screenshotUtil.cleanupPageLock(page);
            }
        });
        
        // 设置Browser归还回调（临时会话destroy时归还Browser到全局池）
        if (!session.isPersistent()) {
            session.setBrowserReleaseCallback(browser -> {
                try {
                    globalBrowserPool.releaseBrowser(browser);
                    log.debug("[会话] Browser已归还到全局池 - 用户: {}", session.getUserId());
                } catch (Exception e) {
                    log.warn("[会话] Browser归还失败: {}", e.getMessage());
                }
            });
        }
        
        return session;
    }

    /**
     * 构建会话键
     */
    private String buildKey(String userId, String name) {
        return userId + ":" + name;
    }
    
    /**
     * 构建会话键（包含实例ID）
     */
    private String buildKey(String userId, String name, String instanceId) {
        return userId + ":" + name + ":" + instanceId;
    }

    // ==================== 定时清理 ====================

    /**
     * 定时清理过期会话
     */
    @Scheduled(fixedDelayString = "${engine.playwright.pool.cleanup-interval:300000}")
    public void cleanupExpiredSessions() {
        if (shutdown || !properties.isEnabled()) return;
        
        int cleaned = 0;
        
        // 清理过期的持久化会话
        Iterator<Map.Entry<String, BrowserSession>> it = persistentSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, BrowserSession> entry = it.next();
            BrowserSession session = entry.getValue();
            
            if (session.isExpired() && !session.isInUse()) {
                it.remove();
                destroySessionQuietly(session);
                activeCount.decrementAndGet();
                semaphore.release();
                cleaned++;
            }
        }
        
        // 清理过期的临时会话
        Iterator<Map.Entry<String, BrowserSession>> tempIt = temporarySessions.entrySet().iterator();
        while (tempIt.hasNext()) {
            Map.Entry<String, BrowserSession> entry = tempIt.next();
            BrowserSession session = entry.getValue();
            
            if (session.isExpired() && !session.isInUse()) {
                tempIt.remove();
                destroySessionQuietly(session);
                activeCount.decrementAndGet();
                semaphore.release();
                cleaned++;
            }
        }
        
        if (cleaned > 0) {
            log.info("[浏览器池] 清理过期会话: {} 个，当前活跃: {}", cleaned, activeCount.get());
        }
        
        // 资源泄漏检测：创建数和销毁数差异过大时警告
        int created = totalCreatedCount.get();
        int destroyed = totalDestroyedCount.get();
        int diff = created - destroyed - activeCount.get();
        if (diff > 5) {
            log.warn("[浏览器池] 可能存在资源泄漏 - 创建: {}, 销毁: {}, 活跃: {}, 差异: {}", 
                created, destroyed, activeCount.get(), diff);
        }
    }

    /**
     * 安全地销毁Session（静默处理异常）
     */
    private void destroySessionQuietly(BrowserSession session) {
        if (session == null) {
            return;
        }
        try {
            session.destroy();
            totalDestroyedCount.incrementAndGet();
        } catch (Exception e) {
            log.warn("[浏览器池] 销毁Session异常: {}", e.getMessage());
        }
    }

    // ==================== 状态查询 ====================

    /**
     * 获取当前活跃会话数
     */
    public int getActiveCount() {
        return activeCount.get();
    }

    /**
     * 获取持久化会话数
     */
    public int getPersistentCount() {
        return persistentSessions.size();
    }

    /**
     * 获取临时会话数
     */
    public int getTemporaryCount() {
        return temporarySessions.size();
    }

    /**
     * 获取可用槽位数
     */
    public int getAvailableSlots() {
        return semaphore != null ? semaphore.availablePermits() : 0;
    }

    /**
     * 获取池状态
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("activeCount", activeCount.get());
        status.put("persistentCount", persistentSessions.size());
        status.put("temporaryCount", temporarySessions.size());
        status.put("maxSize", properties.getPool().getMaxSize());
        status.put("availableSlots", getAvailableSlots());
        status.put("shutdown", shutdown);
        status.put("totalCreated", totalCreatedCount.get());
        status.put("totalDestroyed", totalDestroyedCount.get());
        return status;
    }
    
    /**
     * 获取资源泄漏检测信息
     */
    public String getResourceLeakInfo() {
        int created = totalCreatedCount.get();
        int destroyed = totalDestroyedCount.get();
        int active = activeCount.get();
        int diff = created - destroyed - active;
        
        return String.format("创建:%d, 销毁:%d, 活跃:%d, 差异:%d%s", 
            created, destroyed, active, diff, diff > 0 ? "(可能泄漏)" : "");
    }
}
