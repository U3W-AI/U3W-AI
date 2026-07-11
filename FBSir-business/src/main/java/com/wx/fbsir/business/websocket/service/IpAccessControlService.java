package com.wx.fbsir.business.websocket.service;

import com.wx.fbsir.business.websocket.domain.WsIpBlacklist;
import com.wx.fbsir.business.websocket.mapper.WsIpBlacklistMapper;
import com.wx.fbsir.common.core.redis.RedisCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * IP访问控制服务
 * 
 * 功能：
 * 1. 记录IP访问失败次数（使用Redis）
 * 2. 自动将频繁失败的IP加入黑名单
 * 3. 全局限流控制
 * 4. 单IP限流控制
 *
 * 防护策略：
 * - 30秒内失败5次 → 自动封禁1天
 * - 全局限流：每秒最多100个新连接
 * - IP级限流：每个IP每秒最多10个连接
 *
 * @author FBSir
 * @date 2025-12-16
 */
@Service
public class IpAccessControlService {

    private static final Logger log = LoggerFactory.getLogger(IpAccessControlService.class);

    // Redis Key前缀
    private static final String KEY_PREFIX_FAIL_COUNT = "ws:ip:fail:";
    private static final String KEY_RATE_LIMIT_GLOBAL = "ws:rate:global";
    private static final String KEY_RATE_LIMIT_IP = "ws:rate:ip:";

    // 配置参数
    private static final int FAIL_THRESHOLD = 5;           // 失败阈值
    private static final int FAIL_WINDOW_SECONDS = 30;     // 失败统计窗口（秒）
    private static final int AUTO_BLOCK_HOURS = 24;        // 自动封禁时长（小时）
    private static final int GLOBAL_RATE_LIMIT = 100;      // 全局每秒限流
    private static final int IP_RATE_LIMIT = 10;           // 单IP每秒限流

    @Autowired(required = false)
    private RedisCache redisCache;

    @Autowired(required = false)
    private WsIpBlacklistMapper blacklistMapper;

    /**
     * 检查IP是否被限流
     *
     * @param ip IP地址
     * @return true=允许访问，false=被限流
     */
    public boolean checkRateLimit(String ip) {
        if (redisCache == null) {
            return true;
        }

        try {
            // 1. 检查全局限流
            String globalKey = KEY_RATE_LIMIT_GLOBAL + ":" + getCurrentSecond();
            Long globalCount = incrementAndGet(globalKey, 2);
            if (globalCount != null && globalCount > GLOBAL_RATE_LIMIT) {
                log.warn("[限流] 全局限流触发 - 当前QPS: {}", globalCount);
                return false;
            }

            // 2. 检查单IP限流
            String ipKey = KEY_RATE_LIMIT_IP + ip + ":" + getCurrentSecond();
            Long ipCount = incrementAndGet(ipKey, 2);
            if (ipCount != null && ipCount > IP_RATE_LIMIT) {
                log.warn("[限流] IP限流触发 - IP: {}, 当前QPS: {}", ip, ipCount);
                recordFailedAttempt(ip, "RATE_LIMITED", "触发IP级别限流");
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("[限流] 检查异常: {}", e.getMessage(), e);
            return true; // 异常时放行
        }
    }

    /**
     * 记录失败尝试，达到阈值自动加黑名单
     *
     * @param ip      IP地址
     * @param code    错误码
     * @param reason  失败原因
     */
    @Async
    public void recordFailedAttempt(String ip, String code, String reason) {
        if (redisCache == null) {
            return;
        }

        try {
            String failKey = KEY_PREFIX_FAIL_COUNT + ip;
            
            // 增加失败计数
            Long failCount = incrementAndGet(failKey, FAIL_WINDOW_SECONDS);
            
            log.debug("[访问控制] IP: {} 失败次数: {}/{}", ip, failCount, FAIL_THRESHOLD);
            
            // 达到阈值，自动加入黑名单
            if (failCount != null && failCount >= FAIL_THRESHOLD) {
                autoBlockIp(ip, reason, failCount.intValue());
                // 重置计数
                redisCache.deleteObject(failKey);
            }
        } catch (Exception e) {
            log.error("[访问控制] 记录失败尝试异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 自动将IP加入黑名单
     */
    private void autoBlockIp(String ip, String reason, int failCount) {
        if (blacklistMapper == null) {
            return;
        }

        try {
            // 检查是否已在黑名单
            WsIpBlacklist existing = blacklistMapper.selectByIpAddress(ip);
            if (existing != null && existing.getStatus() == 1) {
                log.info("[自动封禁] IP已在黑名单中: {}", ip);
                return;
            }

            // 创建黑名单记录
            WsIpBlacklist blacklist = new WsIpBlacklist();
            blacklist.setIpAddress(ip);
            blacklist.setBlockReason(String.format("自动封禁: %d秒内失败%d次 - %s", 
                FAIL_WINDOW_SECONDS, failCount, reason));
            blacklist.setBlockType(1); // 临时封禁
            blacklist.setExpireTime(LocalDateTime.now().plusHours(AUTO_BLOCK_HOURS));
            blacklist.setStatus(1);
            blacklist.setCreateBy("system");

            blacklistMapper.insert(blacklist);
            
            log.warn("[自动封禁] IP: {} 已被自动加入黑名单 - 原因: {}, 封禁时长: {}小时", 
                ip, reason, AUTO_BLOCK_HOURS);
        } catch (Exception e) {
            log.error("[自动封禁] 添加黑名单失败 - IP: {}, 错误: {}", ip, e.getMessage(), e);
        }
    }

    /**
     * 更新黑名单命中计数（异步）
     *
     * @param blacklistId 黑名单ID
     */
    @Async
    public void updateHitCount(Long blacklistId) {
        if (blacklistMapper == null || blacklistId == null) {
            return;
        }

        try {
            blacklistMapper.updateHitCount(blacklistId);
        } catch (Exception e) {
            log.error("[黑名单] 更新命中计数失败: {}", e.getMessage());
        }
    }

    /**
     * 检查IP是否在黑名单中
     *
     * @param ip IP地址
     * @return null=不在黑名单，非null=黑名单记录
     */
    public WsIpBlacklist checkBlacklistWithCache(String ip) {
        if (blacklistMapper == null) {
            return null;
        }

        try {
            return blacklistMapper.selectActiveByIpAddress(ip);
        } catch (Exception e) {
            log.error("[黑名单] 检查异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Redis自增操作
     */
    private Long incrementAndGet(String key, int expireSeconds) {
        try {
            Long count = redisCache.redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisCache.expire(key, expireSeconds);
            }
            return count;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前秒数（用于限流key）
     */
    private long getCurrentSecond() {
        return System.currentTimeMillis() / 1000;
    }
}
