package com.wx.fbsir.engine.playwright.core;

import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import com.wx.fbsir.engine.playwright.pool.BrowserPoolManager;
import com.wx.fbsir.engine.playwright.session.BrowserSession;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Playwright 任务执行器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 提供统一的线程池管理，避免线程泄漏
 * 2. 封装浏览器任务执行，自动管理会话生命周期
 * 3. 支持同步和异步任务执行
 * 4. 支持任务超时控制
 * 5. 提供任务状态监控
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 设计亮点（解决 cube-engine 线程问题）
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 统一线程池：所有浏览器任务通过同一个线程池执行
 * 2. 有界队列：防止任务积压导致 OOM
 * 3. 拒绝策略：任务队列满时快速失败，而非无限等待
 * 4. 优雅关闭：PreDestroy 确保所有任务完成后关闭
 * 5. 任务封装：自动获取/释放浏览器会话
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用方式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```java
 * @Autowired
 * private PlaywrightTaskExecutor taskExecutor;
 * 
 * // 同步执行（等待结果）
 * String result = taskExecutor.execute("userId", "baidu", session -> {
 *     Page page = session.getOrCreatePage();
 *     page.navigate("https://baidu.com");
 *     return page.title();
 * });
 * 
 * // 异步执行
 * CompletableFuture<String> future = taskExecutor.executeAsync("userId", "task", session -> {
 *     // 执行操作...
 *     return "result";
 * });
 * 
 * // 带超时的执行
 * String result = taskExecutor.executeWithTimeout("userId", "task", 30, TimeUnit.SECONDS, session -> {
 *     // 执行操作...
 *     return "result";
 * });
 * ```
 * 
 * @author wxfbsir
 * @date 2025-12-16
 */
@Component
public class PlaywrightTaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightTaskExecutor.class);

    private final PlaywrightProperties properties;
    private final BrowserPoolManager browserPool;

    /**
     * 主任务线程池
     */
    private ThreadPoolExecutor taskExecutor;

    /**
     * 调度线程池（用于超时控制）
     */
    private ScheduledExecutorService scheduledExecutor;

    /**
     * 活跃任务计数
     */
    private final AtomicInteger activeTaskCount = new AtomicInteger(0);

    /**
     * 完成任务计数
     */
    private final AtomicInteger completedTaskCount = new AtomicInteger(0);

    /**
     * 失败任务计数
     */
    private final AtomicInteger failedTaskCount = new AtomicInteger(0);
    
    /**
     * 资源泄漏警告计数（任务完成但会话未正确释放）
     */
    private final AtomicInteger resourceLeakWarnings = new AtomicInteger(0);

    /**
     * 是否已关闭
     */
    private volatile boolean shutdown = false;

    public PlaywrightTaskExecutor(PlaywrightProperties properties, BrowserPoolManager browserPool) {
        this.properties = properties;
        this.browserPool = browserPool;
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("[任务执行器] 已禁用");
            return;
        }

        PlaywrightProperties.ThreadPoolConfig config = properties.getThreadPool();

        // 创建任务线程池
        taskExecutor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                new ThreadFactory() {
                    private final AtomicInteger counter = new AtomicInteger(0);
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "playwright-task-" + counter.incrementAndGet());
                        t.setDaemon(true);
                        return t;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy() {
                    @Override
                    public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                        // 友好拒绝：记录日志并抛出异常，由上层捕获并通知用户
                        log.warn("[任务执行器] 任务被拒绝 - 线程池繁忙，活跃线程: {}/{}, 队列: {}/{}",
                            e.getActiveCount(), e.getMaximumPoolSize(),
                            e.getQueue().size(), config.getQueueCapacity());
                        super.rejectedExecution(r, e);
                    }
                }
        );

        // 创建调度线程池
        scheduledExecutor = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "playwright-scheduler");
            t.setDaemon(true);
            return t;
        });

        log.info("[任务执行器] 初始化完成 - 核心线程: {}, 最大线程: {}, 队列大小: {}",
                config.getCoreSize(), config.getMaxSize(), config.getQueueCapacity());
    }

    // ==================== 同步执行方法 ====================

    /**
     * 同步执行浏览器任务（持久化会话）
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param task 任务函数
     * @return 任务结果
     */
    public <T> T execute(String userId, String sessionName, Function<BrowserSession, T> task) {
        return execute(userId, sessionName, true, properties.isHeadless(), task);
    }

    /**
     * 同步执行浏览器任务（完整参数）
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param persistent 是否持久化
     * @param headless 是否无头模式
     * @param task 任务函数
     * @return 任务结果
     */
    public <T> T execute(String userId, String sessionName, boolean persistent, boolean headless,
                         Function<BrowserSession, T> task) {
        return execute(userId, sessionName, null, persistent, headless, task);
    }
    
    /**
     * 同步执行浏览器任务（支持实例ID隔离）
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param instanceId 实例ID（用于同一用户多浏览器实例隔离）
     * @param persistent 是否持久化
     * @param headless 是否无头模式
     * @param task 任务函数
     * @return 任务结果
     */
    public <T> T execute(String userId, String sessionName, String instanceId, boolean persistent, boolean headless,
                         Function<BrowserSession, T> task) {
        checkNotShutdown();

        long startTime = System.currentTimeMillis();
        activeTaskCount.incrementAndGet();
        String taskKey = instanceId != null ? userId + ":" + sessionName + ":" + instanceId : userId + ":" + sessionName;
        BrowserSession session = null;
        try {
            session = browserPool.acquire(userId, sessionName, instanceId, persistent, headless);
            T result = task.apply(session);
            completedTaskCount.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[任务执行器] 执行完成 - 任务: {}, 耗时: {}ms", taskKey, duration);
            return result;
        } catch (IllegalStateException e) {
            failedTaskCount.incrementAndGet();
            log.error("[任务执行器] 会话状态异常 - 任务: {}, 错误: {}", taskKey, e.getMessage());
            throw new RuntimeException("[任务执行器] 会话状态异常: " + e.getMessage(), e);
        } catch (com.microsoft.playwright.TimeoutError e) {
            failedTaskCount.incrementAndGet();
            log.error("[任务执行器] 浏览器操作超时 - 任务: {}, 错误: {}", taskKey, e.getMessage());
            throw new RuntimeException("[任务执行器] 浏览器操作超时: " + e.getMessage(), e);
        } catch (Exception e) {
            failedTaskCount.incrementAndGet();
            log.error("[任务执行器] 任务执行失败 - 任务: {}, 错误类型: {}, 错误信息: {}", 
                taskKey, e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("[任务执行器] 任务执行失败: " + e.getMessage(), e);
        } finally {
            activeTaskCount.decrementAndGet();
            // 确保会话被正确释放
            if (session != null) {
                try {
                    browserPool.release(session);
                } catch (Exception e) {
                    resourceLeakWarnings.incrementAndGet();
                    log.warn("[任务执行器] 会话关闭异常 - 任务: {}, 错误: {}", taskKey, e.getMessage());
                }
            }
        }
    }

    // ==================== 异步执行方法 ====================

    /**
     * 异步执行浏览器任务
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param task 任务函数
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> executeAsync(String userId, String sessionName,
                                                  Function<BrowserSession, T> task) {
        return executeAsync(userId, sessionName, true, properties.isHeadless(), task);
    }

    /**
     * 异步执行浏览器任务（完整参数）
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param persistent 是否持久化
     * @param headless 是否无头模式
     * @param task 任务函数
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> executeAsync(String userId, String sessionName,
                                                  boolean persistent, boolean headless,
                                                  Function<BrowserSession, T> task) {
        return executeAsync(userId, sessionName, null, persistent, headless, task);
    }
    
    /**
     * 异步执行浏览器任务（支持实例ID隔离）
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param instanceId 实例ID（用于同一用户多浏览器实例隔离）
     * @param persistent 是否持久化
     * @param headless 是否无头模式
     * @param task 任务函数
     * @return CompletableFuture
     */
    public <T> CompletableFuture<T> executeAsync(String userId, String sessionName, String instanceId,
                                                  boolean persistent, boolean headless,
                                                  Function<BrowserSession, T> task) {
        checkNotShutdown();
        
        String taskKey = instanceId != null ? userId + ":" + sessionName + ":" + instanceId : userId + ":" + sessionName;

        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            activeTaskCount.incrementAndGet();
            BrowserSession session = null;
            try {
                session = browserPool.acquire(userId, sessionName, instanceId, persistent, headless);
                T result = task.apply(session);
                completedTaskCount.incrementAndGet();
                long duration = System.currentTimeMillis() - startTime;
                log.debug("[任务执行器] 异步任务完成 - 任务: {}, 耗时: {}ms", taskKey, duration);
                return result;
            } catch (IllegalStateException e) {
                failedTaskCount.incrementAndGet();
                log.error("[任务执行器] 异步任务会话异常 - 任务: {}, 错误: {}", taskKey, e.getMessage());
                throw new CompletionException("[任务执行器] 会话状态异常: " + e.getMessage(), e);
            } catch (com.microsoft.playwright.TimeoutError e) {
                failedTaskCount.incrementAndGet();
                log.error("[任务执行器] 异步任务超时 - 任务: {}, 错误: {}", taskKey, e.getMessage());
                throw new CompletionException("[任务执行器] 浏览器操作超时: " + e.getMessage(), e);
            } catch (Exception e) {
                failedTaskCount.incrementAndGet();
                log.error("[任务执行器] 异步任务失败 - 任务: {}, 错误类型: {}, 错误信息: {}", 
                    taskKey, e.getClass().getSimpleName(), e.getMessage());
                throw new CompletionException("[任务执行器] 任务执行失败: " + e.getMessage(), e);
            } finally {
                activeTaskCount.decrementAndGet();
                // 确保会话被正确释放
                if (session != null) {
                    try {
                        browserPool.release(session);
                    } catch (Exception e) {
                        resourceLeakWarnings.incrementAndGet();
                        log.warn("[任务执行器] 异步任务会话关闭异常 - 任务: {}, 错误: {}", taskKey, e.getMessage());
                    }
                }
            }
        }, taskExecutor);
    }

    // ==================== 带超时的执行方法 ====================

    /**
     * 带超时的同步执行
     * 
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param timeout 超时时间
     * @param unit 时间单位
     * @param task 任务函数
     * @return 任务结果
     */
    public <T> T executeWithTimeout(String userId, String sessionName,
                                     long timeout, TimeUnit unit,
                                     Function<BrowserSession, T> task) {
        log.debug("[任务执行器] 带超时执行 - 用户: {}, 会话: {}, 超时: {} {}", userId, sessionName, timeout, unit);
        CompletableFuture<T> future = executeAsync(userId, sessionName, task);
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.error("[任务执行器] 任务超时 - 用户: {}, 会话: {}, 超时时间: {} {}", userId, sessionName, timeout, unit);
            throw new RuntimeException("[任务执行器] 任务执行超时: " + timeout + " " + unit, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[任务执行器] 任务被中断 - 用户: {}, 会话: {}", userId, sessionName);
            throw new RuntimeException("[任务执行器] 任务被中断", e);
        } catch (ExecutionException e) {
            log.error("[任务执行器] 任务执行异常 - 用户: {}, 会话: {}, 错误: {}", 
                userId, sessionName, e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
            throw new RuntimeException("[任务执行器] 任务执行失败", e.getCause());
        }
    }

    // ==================== 临时会话执行方法 ====================

    /**
     * 使用临时会话执行任务（无痕模式）
     */
    public <T> T executeTemporary(String taskId, Function<BrowserSession, T> task) {
        return execute(taskId, "temp", false, properties.isHeadless(), task);
    }

    /**
     * 使用临时会话异步执行任务
     */
    public <T> CompletableFuture<T> executeTemporaryAsync(String taskId, Function<BrowserSession, T> task) {
        return executeAsync(taskId, "temp", false, properties.isHeadless(), task);
    }

    // ==================== 延迟执行方法 ====================

    /**
     * 延迟执行任务
     * 
     * @param delay 延迟时间
     * @param unit 时间单位
     * @param userId 用户ID
     * @param sessionName 会话名称
     * @param task 任务函数
     * @return ScheduledFuture
     */
    public <T> ScheduledFuture<T> schedule(long delay, TimeUnit unit,
                                            String userId, String sessionName,
                                            Function<BrowserSession, T> task) {
        checkNotShutdown();

        return scheduledExecutor.schedule(() -> execute(userId, sessionName, task), delay, unit);
    }

    // ==================== 工具方法 ====================

    /**
     * 检查是否已关闭
     */
    private void checkNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("PlaywrightTaskExecutor 已关闭");
        }
    }

    /**
     * 获取活跃任务数
     */
    public int getActiveTaskCount() {
        return activeTaskCount.get();
    }

    /**
     * 获取完成任务数
     */
    public int getCompletedTaskCount() {
        return completedTaskCount.get();
    }

    /**
     * 获取失败任务数
     */
    public int getFailedTaskCount() {
        return failedTaskCount.get();
    }

    /**
     * 获取资源泄漏警告数
     */
    public int getResourceLeakWarnings() {
        return resourceLeakWarnings.get();
    }
    
    /**
     * 获取线程池状态
     */
    public java.util.Map<String, Object> getStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("enabled", properties.isEnabled());
        status.put("shutdown", shutdown);
        status.put("activeTaskCount", activeTaskCount.get());
        status.put("completedTaskCount", completedTaskCount.get());
        status.put("failedTaskCount", failedTaskCount.get());
        status.put("resourceLeakWarnings", resourceLeakWarnings.get());

        if (taskExecutor != null) {
            status.put("poolSize", taskExecutor.getPoolSize());
            status.put("activeThreads", taskExecutor.getActiveCount());
            status.put("queueSize", taskExecutor.getQueue().size());
            status.put("completedTasks", taskExecutor.getCompletedTaskCount());
        }

        return status;
    }

    // ==================== 关闭方法 ====================

    @PreDestroy
    public void shutdown() {
        if (shutdown) return;
        shutdown = true;

        log.info("[任务执行器] 正在关闭...");

        // 关闭调度线程池
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
            try {
                if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduledExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduledExecutor.shutdownNow();
            }
        }

        // 关闭任务线程池
        if (taskExecutor != null) {
            taskExecutor.shutdown();
            try {
                if (!taskExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("[任务执行器] 等待任务完成超时，强制关闭");
                    taskExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                taskExecutor.shutdownNow();
            }
        }

        log.info("[任务执行器] 已关闭 - 完成: {}, 失败: {}, 资源泄漏警告: {}",
                completedTaskCount.get(), failedTaskCount.get(), resourceLeakWarnings.get());
    }
}
