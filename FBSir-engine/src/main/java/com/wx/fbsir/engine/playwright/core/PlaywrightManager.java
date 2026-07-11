package com.wx.fbsir.engine.playwright.core;

import com.microsoft.playwright.Playwright;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Playwright 生命周期管理器（单例）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心职责
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 管理 Playwright 实例的生命周期（创建、复用、销毁）
 * 2. 确保全局只有一个 Playwright 实例（单例模式）
 * 3. 提供线程安全的 Playwright 访问
 * 4. 应用关闭时自动清理资源
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 设计原则
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 延迟初始化：只在第一次使用时创建 Playwright 实例
 * - 线程安全：使用 ReentrantLock 保证并发安全
 * - 优雅关闭：PreDestroy 钩子确保资源释放
 * - 故障恢复：支持 Playwright 实例重建
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 使用方式
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * ```java
 * @Autowired
 * private PlaywrightManager playwrightManager;
 * 
 * // 获取 Playwright 实例
 * Playwright playwright = playwrightManager.getPlaywright();
 * 
 * // 检查是否可用
 * if (playwrightManager.isAvailable()) {
 *     // 执行浏览器操作
 * }
 * ```
 * 
 * @author FBSir
 * @date 2025-12-16
 */
@Component
public class PlaywrightManager {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightManager.class);

    private final PlaywrightProperties properties;
    
    /**
     * Playwright 实例（延迟初始化）
     */
    private final AtomicReference<Playwright> playwrightRef = new AtomicReference<>();
    
    /**
     * 初始化锁
     */
    private final ReentrantLock initLock = new ReentrantLock();
    
    /**
     * 是否已关闭
     */
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    /**
     * 实例创建次数（用于追踪重建）
     */
    private final AtomicInteger instanceCount = new AtomicInteger(0);
    
    /**
     * 最后一次僵尸进程清理时间
     */
    private volatile long lastZombieCleanupTime = 0;
    
    /**
     * 僵尸进程清理间隔（5分钟）
     * <p>🟡 P2修复：缩短清理间隔，及时回收僵尸进程
     */
    private static final long ZOMBIE_CLEANUP_INTERVAL = 5 * 60 * 1000;

    public PlaywrightManager(PlaywrightProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化方法
     * 在 Spring 容器启动后执行
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("[Playwright] 已禁用，跳过初始化");
            return;
        }
        
        // 启动时清理可能的僵尸进程（上次异常退出留下的）
        cleanupZombieProcesses("startup");
        
        log.info("[Playwright] 管理器初始化完成，等待首次使用时创建实例");
    }

    /**
     * 获取 Playwright 实例（延迟初始化，线程安全）
     * 
     * @return Playwright 实例
     * @throws IllegalStateException 如果 Playwright 已关闭或禁用
     */
    public Playwright getPlaywright() {
        if (shutdown.get()) {
            log.error("[Playwright] 尝试获取已关闭的实例");
            throw new IllegalStateException("[Playwright] 管理器已关闭");
        }
        
        if (!properties.isEnabled()) {
            log.error("[Playwright] 功能已禁用，请检查配置 wxfbsir.engine.playwright.enabled");
            throw new IllegalStateException("[Playwright] 功能已禁用");
        }
        
        Playwright playwright = playwrightRef.get();
        if (playwright != null) {
            // 定期清理僵尸进程
            maybeCleanupZombieProcesses();
            return playwright;
        }
        
        // 双重检查锁定
        initLock.lock();
        try {
            playwright = playwrightRef.get();
            if (playwright != null) {
                return playwright;
            }
            
            // 创建前清理可能的僵尸进程
            cleanupZombieProcesses("before-create");
            
            // 创建新实例
            int count = instanceCount.incrementAndGet();
            log.info("[Playwright] 正在创建实例 (#{})...", count);
            
            playwright = Playwright.create();
            playwrightRef.set(playwright);
            
            log.info("[Playwright] 实例创建成功 (#{})", count);
            return playwright;
            
        } catch (Exception e) {
            log.error("[Playwright] 实例创建失败 - 错误类型: {}, 错误信息: {}", 
                e.getClass().getSimpleName(), e.getMessage());
            
            // 尝试清理后重试一次
            try {
                cleanupZombieProcesses("after-failure");
                Thread.sleep(2000);
                
                log.info("[Playwright] 清理后重试创建实例...");
                playwright = Playwright.create();
                playwrightRef.set(playwright);
                log.info("[Playwright] 重试创建成功");
                return playwright;
                
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("[Playwright] 创建被中断");
                throw new RuntimeException("[Playwright] 实例创建被中断", ie);
            } catch (Exception retryEx) {
                log.error("[Playwright] 重试创建仍然失败 - 错误类型: {}, 错误信息: {}", 
                    retryEx.getClass().getSimpleName(), retryEx.getMessage());
                throw new RuntimeException("[Playwright] 实例创建失败", retryEx);
            }
        } finally {
            initLock.unlock();
        }
    }

    /**
     * 检查 Playwright 是否可用
     * 
     * @return true 如果可用
     */
    public boolean isAvailable() {
        return properties.isEnabled() && !shutdown.get();
    }

    /**
     * 检查 Playwright 实例是否已初始化
     * 
     * @return true 如果已初始化
     */
    public boolean isInitialized() {
        return playwrightRef.get() != null;
    }

    /**
     * 重建 Playwright 实例（用于故障恢复）
     * 会先关闭旧实例，清理僵尸进程，再创建新实例
     * 
     * @return 新的 Playwright 实例
     */
    public Playwright rebuild() {
        if (shutdown.get()) {
            log.error("[Playwright] 尝试重建已关闭的实例");
            throw new IllegalStateException("[Playwright] 管理器已关闭");
        }
        
        initLock.lock();
        try {
            int count = instanceCount.incrementAndGet();
            log.warn("[Playwright] 正在重建实例 (#{})...", count);
            
            // 关闭旧实例
            Playwright old = playwrightRef.getAndSet(null);
            if (old != null) {
                log.info("[Playwright] 关闭旧实例...");
                closeWithLogging(old, "rebuild-old");
            }
            
            // 等待资源释放
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("[Playwright] 重建等待被中断");
            }
            
            // 清理僵尸进程
            cleanupZombieProcesses("rebuild");
            
            // 创建新实例
            Playwright playwright = Playwright.create();
            playwrightRef.set(playwright);
            log.info("[Playwright] 实例重建成功 (#{})", count);
            
            return playwright;
        } catch (Exception e) {
            log.error("[Playwright] 实例重建失败 - 错误类型: {}, 错误信息: {}", 
                e.getClass().getSimpleName(), e.getMessage());
            throw new RuntimeException("[Playwright] 实例重建失败", e);
        } finally {
            initLock.unlock();
        }
    }

    /**
     * 关闭 Playwright（应用关闭时自动调用）
     */
    @PreDestroy
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            log.info("[Playwright] 正在关闭，已创建实例数: {}", instanceCount.get());
            
            initLock.lock();
            try {
                Playwright playwright = playwrightRef.getAndSet(null);
                if (playwright != null) {
                    closeWithLogging(playwright, "shutdown");
                }
                
                // 关闭后清理所有僵尸进程
                cleanupZombieProcesses("shutdown");
                
                log.info("[Playwright] 已完全关闭");
            } finally {
                initLock.unlock();
            }
        }
    }

    /**
     * 带日志的关闭 Playwright 实例
     * 
     * @param playwright Playwright 实例
     * @param context 关闭上下文（用于日志）
     */
    private void closeWithLogging(Playwright playwright, String context) {
        try {
            log.debug("[Playwright] 正在关闭实例 ({})...", context);
            playwright.close();
            log.debug("[Playwright] 实例关闭成功 ({})", context);
        } catch (Exception e) {
            // 关闭异常不能静默处理，必须记录
            log.warn("[Playwright] 关闭实例时发生异常 ({}) - 错误类型: {}, 错误信息: {}", 
                context, e.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    /**
     * 条件性清理僵尸进程（每10分钟最多执行一次）
     */
    private void maybeCleanupZombieProcesses() {
        long now = System.currentTimeMillis();
        if (now - lastZombieCleanupTime > ZOMBIE_CLEANUP_INTERVAL) {
            lastZombieCleanupTime = now;
            cleanupZombieProcesses("periodic");
        }
    }
    
    /**
     * 清理僵尸进程（公开方法，供定时任务调用）
     */
    public void cleanupZombieProcesses() {
        cleanupZombieProcesses("scheduled");
    }
    
    /**
     * 清理僵尸 Chrome 进程
     * 解决旧项目中的僵尸进程问题
     * 
     * @param trigger 触发清理的原因（用于日志）
     */
    private void cleanupZombieProcesses(String trigger) {
        String os = System.getProperty("os.name").toLowerCase();
        
        try {
            if (os.contains("mac") || os.contains("linux")) {
                cleanupUnixZombieProcesses(trigger);
            } else if (os.contains("win")) {
                cleanupWindowsZombieProcesses(trigger);
            }
        } catch (Exception e) {
            // 僵尸进程清理失败不应影响主流程，但必须记录
            log.warn("[Playwright] 僵尸进程清理失败 ({}) - 错误: {}", trigger, e.getMessage());
        }
        
        // 清理数据目录中的锁文件
        cleanupAllLockFiles(trigger);
    }
    
    /**
     * Unix/Mac 系统僵尸进程清理
     */
    private void cleanupUnixZombieProcesses(String trigger) {
        try {
            // 查找所有 Chromium 僵尸进程
            ProcessBuilder findPb = new ProcessBuilder("bash", "-c",
                "ps aux | grep -E 'chromium|chrome' | grep -v grep | grep -E 'defunct|<defunct>' | awk '{print $2}'");
            Process findProcess = findPb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(findProcess.getInputStream()));
            String line;
            int killedCount = 0;
            
            while ((line = reader.readLine()) != null) {
                try {
                    int pid = Integer.parseInt(line.trim());
                    ProcessBuilder killPb = new ProcessBuilder("kill", "-9", String.valueOf(pid));
                    killPb.start().waitFor(2, TimeUnit.SECONDS);
                    killedCount++;
                    log.debug("[Playwright] 终止僵尸进程: PID {}", pid);
                } catch (NumberFormatException e) {
                    // 跳过非数字行
                }
            }
            
            findProcess.waitFor(5, TimeUnit.SECONDS);
            
            if (killedCount > 0) {
                log.info("[Playwright] 清理僵尸进程完成 ({}) - 终止数量: {}", trigger, killedCount);
            }
            
        } catch (Exception e) {
            log.debug("[Playwright] Unix僵尸进程清理异常: {}", e.getMessage());
        }
    }
    
    /**
     * Windows 系统僵尸进程清理
     */
    private void cleanupWindowsZombieProcesses(String trigger) {
        try {
            // Windows 上使用 taskkill 清理已无响应的 Chrome 进程
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c",
                "wmic process where \"name like '%chrome%' and Status='Not Responding'\" call terminate");
            Process process = pb.start();
            process.waitFor(10, TimeUnit.SECONDS);
            
            log.debug("[Playwright] Windows僵尸进程清理完成 ({})", trigger);
        } catch (Exception e) {
            log.debug("[Playwright] Windows僵尸进程清理异常: {}", e.getMessage());
        }
    }
    
    /**
     * 清理所有浏览器锁文件
     */
    private void cleanupAllLockFiles(String trigger) {
        try {
            Path dataDir = Paths.get(properties.getDataDir());
            if (!Files.exists(dataDir)) {
                return;
            }
            
            int cleanedCount = 0;
            
            // 遍历所有用户目录
            File[] sessionDirs = dataDir.toFile().listFiles(File::isDirectory);
            if (sessionDirs != null) {
                for (File sessionDir : sessionDirs) {
                    File[] userDirs = sessionDir.listFiles(File::isDirectory);
                    if (userDirs != null) {
                        for (File userDir : userDirs) {
                            cleanedCount += cleanupLockFilesInDir(userDir);
                        }
                    }
                }
            }
            
            if (cleanedCount > 0) {
                log.info("[Playwright] 清理锁文件完成 ({}) - 清理数量: {}", trigger, cleanedCount);
            }
        } catch (Exception e) {
            log.debug("[Playwright] 清理锁文件异常: {}", e.getMessage());
        }
    }
    
    /**
     * 清理指定目录中的锁文件
     */
    private int cleanupLockFilesInDir(File dir) {
        int count = 0;
        File[] lockFiles = dir.listFiles((d, name) -> 
            name.contains("Lock") || name.contains("lock") || name.endsWith(".lock"));
        
        if (lockFiles != null) {
            for (File lockFile : lockFiles) {
                try {
                    if (lockFile.delete()) {
                        count++;
                        log.debug("[Playwright] 删除锁文件: {}", lockFile.getName());
                    }
                } catch (Exception e) {
                    log.debug("[Playwright] 删除锁文件失败: {} - {}", lockFile.getName(), e.getMessage());
                }
            }
        }
        return count;
    }
    
    /**
     * 获取实例创建次数
     */
    public int getInstanceCount() {
        return instanceCount.get();
    }

    /**
     * 获取配置属性
     */
    public PlaywrightProperties getProperties() {
        return properties;
    }
}
