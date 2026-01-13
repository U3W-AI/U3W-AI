package com.wx.fbsir.engine.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 浏览器会话锁工具类 - 确保同一用户的浏览器会话串行使用
 */
public class BrowserSessionLockUtil {
    // 存储每个用户的专属锁（ConcurrentHashMap保证线程安全）
    private static final ConcurrentHashMap<String, Lock> USER_SESSION_LOCK_MAP = new ConcurrentHashMap<>();

    /**
     * 获取指定用户的会话锁
     * @param userId 用户ID
     * @return 该用户的专属锁
     */
    public static Lock getUserSessionLock(String userId) {
        // computeIfAbsent：不存在则创建，存在则返回已有锁，保证唯一性
        return USER_SESSION_LOCK_MAP.computeIfAbsent(userId, k -> new ReentrantLock());
    }

    /**
     * 移除用户锁（可选，避免内存泄漏）
     * @param userId 用户ID
     */
    public static void removeUserSessionLock(String userId) {
        USER_SESSION_LOCK_MAP.remove(userId);
    }
}