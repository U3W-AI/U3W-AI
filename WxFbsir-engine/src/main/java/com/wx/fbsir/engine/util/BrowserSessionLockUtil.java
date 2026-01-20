package com.wx.fbsir.engine.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 浏览器会话锁工具类 - 支持两种粒度的锁控制
 * 
 * 设计目标：
 * 1. 会话级锁（细粒度）- 用于浏览器会话创建
 *    允许：用户1的 deepseek 和 yuanqi 会话并发创建（不同会话）
 *    阻止：用户1的两个 deepseek 请求并发创建（相同会话，避免重复）
 * 
 * 2. 用户级锁（粗粒度）- 用于长时间运行的任务
 *    阻止：用户的所有长时间任务并发执行（如知识库配置）
 *    避免：资源冲突和任务混乱
 * 
 * 使用场景：
 * - BrowserPoolManager.createSession() → 使用会话级锁
 * - RobotController.配置知识库() → 使用用户级锁
 * - YuanQiKnowledgeController.配置知识库() → 使用用户级锁
 */
public class BrowserSessionLockUtil {
    private static final Logger log = LoggerFactory.getLogger(BrowserSessionLockUtil.class);
    
    // 会话级锁：Key格式：userId:name，例如：1:deepseek、1:yuanqi
    private static final ConcurrentHashMap<String, ReentrantLock> SESSION_LOCK_MAP = new ConcurrentHashMap<>();
    
    // 用户级锁：Key格式：userId，例如：1、2
    private static final ConcurrentHashMap<String, ReentrantLock> USER_LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * 获取指定会话的锁
     * @param sessionKey 会话键（格式：userId:name）
     * @return 该会话的专属锁
     */
    public static Lock getSessionLock(String sessionKey) {
        // computeIfAbsent：不存在则创建，存在则返回已有锁，保证唯一性
        ReentrantLock lock = SESSION_LOCK_MAP.computeIfAbsent(sessionKey, k -> {
            log.debug("[会话锁] 创建会话锁 - 会话: {}", sessionKey);
            return new ReentrantLock();
        });
        
        // 记录锁的等待情况（说明有并发冲突）
        if (lock.isLocked() && !lock.isHeldByCurrentThread()) {
            log.warn("[会话锁] 检测到并发冲突，等待获取锁 - 会话: {}, 队列长度: {}", 
                sessionKey, lock.getQueueLength());
        }
        
        return lock;
    }

    /**
     * 移除会话锁（可选，避免内存泄漏）
     * @param sessionKey 会话键
     */
    public static void removeSessionLock(String sessionKey) {
        ReentrantLock removed = SESSION_LOCK_MAP.remove(sessionKey);
        if (removed != null) {
            log.debug("[会话锁] 移除会话锁 - 会话: {}", sessionKey);
        }
    }
    
    /**
     * 定期清理长时间未使用的锁（避免内存泄漏）
     * 建议：在定时任务中调用
     */
    public static void cleanupUnusedLocks() {
        SESSION_LOCK_MAP.entrySet().removeIf(entry -> {
            ReentrantLock lock = entry.getValue();
            // 只清理未被持有且队列为空的锁
            if (!lock.isLocked() && lock.getQueueLength() == 0) {
                log.debug("[会话锁] 清理未使用的锁 - 会话: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 获取锁统计信息（用于监控和调试）
     */
    public static int getActiveLockCount() {
        return SESSION_LOCK_MAP.size();
    }
    
    /**
     * 构建会话键
     * @param userId 用户ID
     * @param name 会话名称
     * @return 会话键（格式：userId:name）
     */
    public static String buildSessionKey(String userId, String name) {
        return userId + ":" + name;
    }
    
    // ========================================
    // 用户级锁（粗粒度）- 用于长时间运行的任务
    // ========================================
    
    /**
     * 获取指定用户的锁（用于长时间任务，如知识库配置）
     * @param userId 用户ID
     * @return 该用户的专属锁
     */
    public static Lock getUserSessionLock(String userId) {
        ReentrantLock lock = USER_LOCK_MAP.computeIfAbsent(userId, k -> {
            log.debug("[用户锁] 创建用户锁 - 用户: {}", userId);
            return new ReentrantLock();
        });
        
        if (lock.isLocked() && !lock.isHeldByCurrentThread()) {
            log.warn("[用户锁] 用户有其他任务正在执行，等待获取锁 - 用户: {}, 队列长度: {}", 
                userId, lock.getQueueLength());
        }
        
        return lock;
    }
    
    /**
     * 移除用户锁
     * @param userId 用户ID
     */
    public static void removeUserSessionLock(String userId) {
        ReentrantLock removed = USER_LOCK_MAP.remove(userId);
        if (removed != null) {
            log.debug("[用户锁] 移除用户锁 - 用户: {}", userId);
        }
    }
}