package com.wx.fbsir.business.websocket.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接频率限制器
 * 
 * 功能：
 * 1. 防止恶意高频连接攻击（DOS防护）
 * 2. 检测异常连接模式（配置错误导致的无限重连）
 * 3. 自动封禁异常IP/主机ID
 * 
 * 限流策略：
 * - 1分钟内连接失败超过10次 → 警告
 * - 5分钟内连接失败超过30次 → 临时封禁10分钟
 * - 1小时内连接失败超过100次 → 临时封禁1小时
 * 
 * @author FBSir
 * @date 2025-12-24
 */
@Service
public class ConnectionRateLimiter {
    
    private static final Logger log = LoggerFactory.getLogger(ConnectionRateLimiter.class);
    
    /**
     * IP连接失败记录: IP -> 失败记录
     */
    private final Map<String, FailureRecord> ipFailureMap = new ConcurrentHashMap<>();
    
    /**
     * 主机ID连接失败记录: HostId -> 失败记录
     */
    private final Map<String, FailureRecord> hostIdFailureMap = new ConcurrentHashMap<>();
    
    /**
     * IP封禁记录: IP -> 封禁过期时间戳
     */
    private final Map<String, Long> ipBanMap = new ConcurrentHashMap<>();
    
    /**
     * 主机ID封禁记录: HostId -> 封禁过期时间戳
     */
    private final Map<String, Long> hostIdBanMap = new ConcurrentHashMap<>();
    
    // ========== 限流配置 ==========
    private static final int WARN_THRESHOLD_1MIN = 10;      // 1分钟内失败10次警告
    private static final int BAN_THRESHOLD_5MIN = 30;       // 5分钟内失败30次封禁10分钟
    private static final int BAN_THRESHOLD_1HOUR = 100;     // 1小时内失败100次封禁1小时
    
    private static final long BAN_DURATION_10MIN = 10 * 60 * 1000L;   // 10分钟
    private static final long BAN_DURATION_1HOUR = 60 * 60 * 1000L;   // 1小时
    
    private static final long WINDOW_1MIN = 60 * 1000L;
    private static final long WINDOW_5MIN = 5 * 60 * 1000L;
    private static final long WINDOW_1HOUR = 60 * 60 * 1000L;
    
    /**
     * 检查IP是否被封禁
     * 
     * @param ip 客户端IP
     * @return 封禁结果
     */
    public RateLimitResult checkIpBan(String ip) {
        if (ip == null || ip.isEmpty()) {
            return RateLimitResult.allowed();
        }
        
        // 清理过期封禁
        cleanExpiredBans();
        
        Long banExpireTime = ipBanMap.get(ip);
        if (banExpireTime != null && System.currentTimeMillis() < banExpireTime) {
            long remainingMinutes = (banExpireTime - System.currentTimeMillis()) / 60000;
            return RateLimitResult.banned(
                String.format("IP [%s] 因异常高频连接已被临时封禁，剩余时间：%d分钟", ip, remainingMinutes),
                (int) remainingMinutes
            );
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * 检查主机ID是否被封禁
     * 
     * @param hostId 主机ID
     * @return 封禁结果
     */
    public RateLimitResult checkHostIdBan(String hostId) {
        if (hostId == null || hostId.isEmpty()) {
            return RateLimitResult.allowed();
        }
        
        cleanExpiredBans();
        
        Long banExpireTime = hostIdBanMap.get(hostId);
        if (banExpireTime != null && System.currentTimeMillis() < banExpireTime) {
            long remainingMinutes = (banExpireTime - System.currentTimeMillis()) / 60000;
            return RateLimitResult.banned(
                String.format("主机ID [%s] 因异常高频连接已被临时封禁，剩余时间：%d分钟", hostId, remainingMinutes),
                (int) remainingMinutes
            );
        }
        
        return RateLimitResult.allowed();
    }
    
    /**
     * 记录连接失败
     * 
     * @param ip 客户端IP
     * @param hostId 主机ID（可能为空）
     * @param reason 失败原因
     */
    public void recordFailure(String ip, String hostId, String reason) {
        long now = System.currentTimeMillis();
        
        // 记录IP失败
        if (ip != null && !ip.isEmpty()) {
            FailureRecord ipRecord = ipFailureMap.computeIfAbsent(ip, k -> new FailureRecord());
            ipRecord.addFailure(now);
            
            // 检查是否需要封禁IP
            checkAndBanIp(ip, ipRecord, reason);
        }
        
        // 记录主机ID失败（如果有）
        if (hostId != null && !hostId.isEmpty()) {
            FailureRecord hostRecord = hostIdFailureMap.computeIfAbsent(hostId, k -> new FailureRecord());
            hostRecord.addFailure(now);
            
            // 检查是否需要封禁主机ID
            checkAndBanHostId(hostId, hostRecord, reason);
        }
    }
    
    /**
     * 检查并封禁IP
     */
    private void checkAndBanIp(String ip, FailureRecord record, String reason) {
        long now = System.currentTimeMillis();
        
        int failures1Min = record.countFailures(now - WINDOW_1MIN, now);
        int failures5Min = record.countFailures(now - WINDOW_5MIN, now);
        int failures1Hour = record.countFailures(now - WINDOW_1HOUR, now);
        
        // 1小时内失败100次 → 封禁1小时
        if (failures1Hour >= BAN_THRESHOLD_1HOUR) {
            ipBanMap.put(ip, now + BAN_DURATION_1HOUR);
            log.error("[频率限制] IP [{}] 已封禁1小时 - 1小时内失败{}次 - 原因: {}", 
                ip, failures1Hour, reason);
            return;
        }
        
        // 5分钟内失败30次 → 封禁10分钟
        if (failures5Min >= BAN_THRESHOLD_5MIN) {
            ipBanMap.put(ip, now + BAN_DURATION_10MIN);
            log.warn("[频率限制] IP [{}] 已封禁10分钟 - 5分钟内失败{}次 - 原因: {}", 
                ip, failures5Min, reason);
            return;
        }
        
        // 1分钟内失败10次 → 警告
        if (failures1Min >= WARN_THRESHOLD_1MIN) {
            log.warn("[频率限制] IP [{}] 高频失败警告 - 1分钟内失败{}次 - 原因: {}", 
                ip, failures1Min, reason);
        }
    }
    
    /**
     * 检查并封禁主机ID
     */
    private void checkAndBanHostId(String hostId, FailureRecord record, String reason) {
        long now = System.currentTimeMillis();
        
        int failures1Min = record.countFailures(now - WINDOW_1MIN, now);
        int failures5Min = record.countFailures(now - WINDOW_5MIN, now);
        int failures1Hour = record.countFailures(now - WINDOW_1HOUR, now);
        
        // 1小时内失败100次 → 封禁1小时
        if (failures1Hour >= BAN_THRESHOLD_1HOUR) {
            hostIdBanMap.put(hostId, now + BAN_DURATION_1HOUR);
            log.error("[频率限制] 主机ID [{}] 已封禁1小时 - 1小时内失败{}次 - 原因: {}", 
                hostId, failures1Hour, reason);
            return;
        }
        
        // 5分钟内失败30次 → 封禁10分钟
        if (failures5Min >= BAN_THRESHOLD_5MIN) {
            hostIdBanMap.put(hostId, now + BAN_DURATION_10MIN);
            log.warn("[频率限制] 主机ID [{}] 已封禁10分钟 - 5分钟内失败{}次 - 原因: {}", 
                hostId, failures5Min, reason);
            return;
        }
        
        // 1分钟内失败10次 → 警告
        if (failures1Min >= WARN_THRESHOLD_1MIN) {
            log.warn("[频率限制] 主机ID [{}] 高频失败警告 - 1分钟内失败{}次 - 原因: {}", 
                hostId, failures1Min, reason);
        }
    }
    
    /**
     * 清理过期的封禁记录和失败记录
     */
    private void cleanExpiredBans() {
        long now = System.currentTimeMillis();
        
        // 清理过期的IP封禁
        ipBanMap.entrySet().removeIf(entry -> entry.getValue() < now);
        
        // 清理过期的主机ID封禁
        hostIdBanMap.entrySet().removeIf(entry -> entry.getValue() < now);
        
        // 清理1小时前的失败记录
        ipFailureMap.entrySet().removeIf(entry -> {
            entry.getValue().cleanOldFailures(now - WINDOW_1HOUR);
            return entry.getValue().isEmpty();
        });
        
        hostIdFailureMap.entrySet().removeIf(entry -> {
            entry.getValue().cleanOldFailures(now - WINDOW_1HOUR);
            return entry.getValue().isEmpty();
        });
    }
    
    /**
     * 手动解除IP封禁（管理员操作）
     */
    public void unbanIp(String ip) {
        ipBanMap.remove(ip);
        ipFailureMap.remove(ip);
        log.info("[频率限制] 管理员手动解除IP封禁: {}", ip);
    }
    
    /**
     * 手动解除主机ID封禁（管理员操作）
     */
    public void unbanHostId(String hostId) {
        hostIdBanMap.remove(hostId);
        hostIdFailureMap.remove(hostId);
        log.info("[频率限制] 管理员手动解除主机ID封禁: {}", hostId);
    }
    
    /**
     * 获取当前封禁统计
     */
    public BanStatistics getStatistics() {
        cleanExpiredBans();
        return new BanStatistics(
            ipBanMap.size(),
            hostIdBanMap.size(),
            ipFailureMap.size(),
            hostIdFailureMap.size()
        );
    }
    
    /**
     * 失败记录
     */
    private static class FailureRecord {
        private final java.util.concurrent.ConcurrentLinkedDeque<Long> failures = 
            new java.util.concurrent.ConcurrentLinkedDeque<>();
        
        public void addFailure(long timestamp) {
            failures.add(timestamp);
        }
        
        public int countFailures(long fromTime, long toTime) {
            return (int) failures.stream()
                .filter(time -> time >= fromTime && time <= toTime)
                .count();
        }
        
        public void cleanOldFailures(long beforeTime) {
            failures.removeIf(time -> time < beforeTime);
        }
        
        public boolean isEmpty() {
            return failures.isEmpty();
        }
    }
    
    /**
     * 限流结果
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String reason;
        private final int remainingMinutes;
        
        private RateLimitResult(boolean allowed, String reason, int remainingMinutes) {
            this.allowed = allowed;
            this.reason = reason;
            this.remainingMinutes = remainingMinutes;
        }
        
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null, 0);
        }
        
        public static RateLimitResult banned(String reason, int remainingMinutes) {
            return new RateLimitResult(false, reason, remainingMinutes);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
        
        public int getRemainingMinutes() {
            return remainingMinutes;
        }
    }
    
    /**
     * 封禁统计
     */
    public static class BanStatistics {
        private final int bannedIpCount;
        private final int bannedHostIdCount;
        private final int trackedIpCount;
        private final int trackedHostIdCount;
        
        public BanStatistics(int bannedIpCount, int bannedHostIdCount, 
                           int trackedIpCount, int trackedHostIdCount) {
            this.bannedIpCount = bannedIpCount;
            this.bannedHostIdCount = bannedHostIdCount;
            this.trackedIpCount = trackedIpCount;
            this.trackedHostIdCount = trackedHostIdCount;
        }
        
        public int getBannedIpCount() {
            return bannedIpCount;
        }
        
        public int getBannedHostIdCount() {
            return bannedHostIdCount;
        }
        
        public int getTrackedIpCount() {
            return trackedIpCount;
        }
        
        public int getTrackedHostIdCount() {
            return trackedHostIdCount;
        }
    }
}
