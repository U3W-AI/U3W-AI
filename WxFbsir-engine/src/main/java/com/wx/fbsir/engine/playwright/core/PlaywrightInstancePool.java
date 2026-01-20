package com.wx.fbsir.engine.playwright.core;

import com.microsoft.playwright.Playwright;
import com.wx.fbsir.engine.playwright.config.PlaywrightProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Playwright 实例池（支持大规模并发）
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 设计目标
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 问题：单个 Playwright 实例的 launchPersistentContext() 在高并发下有竞态问题
 * 解决：创建多个 Playwright 实例，每个实例独立加锁，支持真正的并发
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 并发能力示例
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 场景1：单用户多AI（池大小=8）
 * - 用户1-deepseek → 实例0（锁0） ━┓
 * - 用户1-yuanqi  → 实例1（锁1） ━┫
 * - 用户1-kimi    → 实例2（锁2） ━┫ ✅ 8个请求同时执行
 * - 用户1-baidu   → 实例3（锁3） ━┫
 * - ...更多AI...                 ━┛
 * 
 * 场景2：多用户多请求（池大小=12）
 * - 用户1-deepseek → 实例0  ━┓
 * - 用户2-yuanqi  → 实例1  ━┫
 * - 用户3-kimi    → 实例2  ━┫ ✅ 12个用户同时执行
 * - 用户4-baidu   → 实例3  ━┫
 * - ...更多用户...         ━┛
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 核心特性
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 配置灵活：支持手动配置或动态计算池大小
 * 2. 实例级锁：每个实例独立加锁，互不干扰
 * 3. 轮询分配：使用 Round-Robin 算法分配实例，均衡负载
 * 4. 自动回收：应用关闭时自动清理所有实例
 * 
 * @author wxfbsir
 * @date 2026-01-20
 */
@Component
public class PlaywrightInstancePool {
    
    private static final Logger log = LoggerFactory.getLogger(PlaywrightInstancePool.class);
    
    private final PlaywrightProperties properties;
    
    public PlaywrightInstancePool(PlaywrightProperties properties) {
        this.properties = properties;
    }
    
    /**
     * 实例包装器（包含实例和锁）
     */
    private static class PlaywrightInstance {
        final Playwright playwright;
        final ReentrantLock lock;
        final int id;
        
        PlaywrightInstance(int id) {
            this.id = id;
            this.playwright = Playwright.create();
            this.lock = new ReentrantLock();
            log.info("[Playwright池] 实例#{} 创建成功", id);
        }
        
        void close() {
            try {
                playwright.close();
                log.info("[Playwright池] 实例#{} 已关闭", id);
            } catch (Exception e) {
                log.warn("[Playwright池] 实例#{} 关闭失败: {}", id, e.getMessage());
            }
        }
    }
    
    /**
     * 实例池（ConcurrentHashMap 保证线程安全）
     */
    private final ConcurrentHashMap<Integer, PlaywrightInstance> instances = new ConcurrentHashMap<>();
    
    /**
     * 轮询计数器（用于 Round-Robin 分配）
     */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);
    
    /**
     * 池大小（实例数量）
     */
    private int poolSize;
    
    /**
     * 是否已初始化
     */
    private volatile boolean initialized = false;
    
    /**
     * 是否已关闭
     */
    private volatile boolean shutdown = false;
    
    /**
     * 初始化实例池
     */
    @PostConstruct
    public void init() {
        int cores = Runtime.getRuntime().availableProcessors();
        
        // 从配置读取池大小，如果为0则动态计算
        int configuredSize = properties.getInstancePool().getSize();
        
        if (configuredSize > 0) {
            // 使用配置的固定值
            poolSize = Math.min(32, configuredSize); // 最大32个实例
            log.info("[Playwright池] 使用配置的池大小: {}", poolSize);
        } else {
            // 动态计算池大小
            if (cores <= 8) {
                // 8核及以下：使用核心数（支持单用户多AI并发）
                poolSize = cores;
            } else {
                // 8核以上：核心数 * 0.75（避免资源浪费）
                poolSize = (int) (cores * 0.75);
            }
            // 确保最少2个，最多32个
            poolSize = Math.max(2, Math.min(32, poolSize));
            log.info("[Playwright池] 动态计算池大小 - CPU核心数: {}, 池大小: {}", cores, poolSize);
        }
        
        log.info("[Playwright池] 开始初始化 - 并发能力: {}个浏览器会话可同时创建", poolSize);
        
        // 创建所有实例
        for (int i = 0; i < poolSize; i++) {
            try {
                PlaywrightInstance instance = new PlaywrightInstance(i);
                instances.put(i, instance);
            } catch (Exception e) {
                log.error("[Playwright池] 实例#{} 创建失败: {}", i, e.getMessage(), e);
                throw new RuntimeException("Playwright实例池初始化失败", e);
            }
        }
        
        initialized = true;
        log.info("[Playwright池] 初始化完成 - 可用实例数: {}/{}, 支持场景: 单用户{}个AI并发 或 {}个用户同时请求", 
            instances.size(), poolSize, poolSize, poolSize);
    }
    
    /**
     * 获取 Playwright 实例（线程安全，支持并发）
     * 
     * 使用 Round-Robin 算法分配实例，确保负载均衡
     * 每个实例都有独立的锁，互不干扰
     * 
     * @return Playwright 实例
     */
    public Playwright acquirePlaywright() {
        if (!initialized) {
            throw new IllegalStateException("Playwright实例池未初始化");
        }
        if (shutdown) {
            throw new IllegalStateException("Playwright实例池已关闭");
        }
        
        // Round-Robin 分配
        int index = Math.abs(roundRobinCounter.getAndIncrement() % poolSize);
        PlaywrightInstance instance = instances.get(index);
        
        if (instance == null) {
            throw new IllegalStateException("Playwright实例#" + index + " 不存在");
        }
        
        log.debug("[Playwright池] 分配实例#{} - 当前请求序号: {}", index, roundRobinCounter.get());
        return instance.playwright;
    }
    
    /**
     * 获取指定实例的锁（用于串行化该实例的 launchPersistentContext 调用）
     * 
     * @param playwright Playwright 实例
     * @return 该实例的锁
     */
    public ReentrantLock getLockForInstance(Playwright playwright) {
        for (PlaywrightInstance instance : instances.values()) {
            if (instance.playwright == playwright) {
                return instance.lock;
            }
        }
        throw new IllegalArgumentException("Playwright实例不属于此池");
    }
    
    /**
     * 获取池统计信息
     */
    public String getStats() {
        int locked = 0;
        for (PlaywrightInstance instance : instances.values()) {
            if (instance.lock.isLocked()) {
                locked++;
            }
        }
        return String.format("池大小: %d, 使用中: %d, 空闲: %d", 
            poolSize, locked, poolSize - locked);
    }
    
    /**
     * 关闭实例池
     */
    @PreDestroy
    public void shutdown() {
        if (!shutdown) {
            shutdown = true;
            log.info("[Playwright池] 开始关闭 - 实例数: {}", instances.size());
            
            for (PlaywrightInstance instance : instances.values()) {
                instance.close();
            }
            
            instances.clear();
            log.info("[Playwright池] 已完全关闭");
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 获取池大小
     */
    public int getPoolSize() {
        return poolSize;
    }
}
