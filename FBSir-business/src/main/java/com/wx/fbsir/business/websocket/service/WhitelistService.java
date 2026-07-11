package com.wx.fbsir.business.websocket.service;

import com.wx.fbsir.business.websocket.domain.WsHostWhitelist;
import com.wx.fbsir.business.websocket.domain.WsIpBlacklist;
import com.wx.fbsir.business.websocket.mapper.WsHostWhitelistMapper;
import com.wx.fbsir.business.websocket.mapper.WsIpBlacklistMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 主机白名单验证服务
 * 
 * 功能：
 * 1. 验证主机ID是否在白名单中
 * 2. 验证主机ID是否已有连接（单连接限制）
 * 3. 验证IP是否在黑名单中
 * 4. 使用MyBatis Mapper操作数据库
 * 5. 直接查询数据库，不使用缓存
 * 6. 自动封禁频繁失败的IP
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Service
public class WhitelistService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistService.class);

    @Autowired(required = false)
    private WsHostWhitelistMapper whitelistMapper;
    
    @Autowired(required = false)
    private WsIpBlacklistMapper blacklistMapper;
    
    @Autowired(required = false)
    private IpAccessControlService ipAccessControl;

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String code;
        private final String message;

        public ValidationResult(boolean valid, String code, String message) {
            this.valid = valid;
            this.code = code;
            this.message = message;
        }

        public boolean isValid() { return valid; }
        public String getCode() { return code; }
        public String getMessage() { return message; }

        public static ValidationResult success() {
            return new ValidationResult(true, "SUCCESS", "验证通过");
        }

        public static ValidationResult fail(String code, String message) {
            return new ValidationResult(false, code, message);
        }
    }

    /**
     * 验证主机ID是否允许连接
     *
     * @param hostId   主机ID
     * @param remoteIp 客户端IP
     * @return 验证结果
     */
    public ValidationResult validateHostId(String hostId, String remoteIp) {
        return validateHostId(hostId, remoteIp, null);
    }

    public ValidationResult validateHostId(String hostId, String remoteIp, String expectedHostType) {
        if (whitelistMapper == null) {
            log.warn("[白名单] Mapper未注入，跳过白名单验证");
            return ValidationResult.success();
        }

        try {
            // 1. 检查IP黑名单（直接查询数据库）
            ValidationResult ipResult = checkIpBlacklist(remoteIp);
            if (!ipResult.isValid()) {
                return ipResult;
            }

            // 2. 检查主机ID是否为空
            if (hostId == null || hostId.trim().isEmpty()) {
                log.debug("[白名单] ID为空 - {}", remoteIp);
                recordFailure(remoteIp, "EMPTY_HOST_ID", "主机ID为空");
                return ValidationResult.fail("EMPTY_HOST_ID", "主机ID不能为空，请向管理员申请主机ID");
            }

            // 3. 查询白名单（只查询未删除的记录 del_flag=0）
            WsHostWhitelist whitelist = whitelistMapper.selectByHostId(hostId);
            
            // 3.1 检查是否存在（不存在或已被删除）
            if (whitelist == null) {
                log.warn("[白名单拒绝] 主机ID不存在或已被删除 - HostID: {}, IP: {}", hostId, remoteIp);
                recordFailure(remoteIp, "HOST_NOT_IN_WHITELIST", "主机ID不在白名单或已被删除");
                return ValidationResult.fail("HOST_NOT_IN_WHITELIST", 
                    "主机ID [" + hostId + "] 未授权或已被移除，请联系管理员添加到白名单");
            }

            // 3.2 检查状态（status=0 表示禁用）
            if (whitelist.getStatus() == null || whitelist.getStatus() != 1) {
                log.warn("[白名单拒绝] 主机ID已被禁用 - HostID: {}, Status: {}, IP: {}", 
                    hostId, whitelist.getStatus(), remoteIp);
                recordFailure(remoteIp, "HOST_DISABLED", "主机ID已禁用");
                return ValidationResult.fail("HOST_DISABLED", 
                    "主机ID [" + hostId + "] 已被管理员停用，请联系管理员启用后再连接");
            }

            // 5. 检查过期时间
            if (expectedHostType != null && !expectedHostType.trim().isEmpty()) {
                String actualHostType = whitelist.getHostType();
                if (actualHostType != null && !actualHostType.trim().isEmpty()
                    && !actualHostType.trim().equalsIgnoreCase(expectedHostType.trim())) {
                    log.warn("[白名单拒绝] 主机类型不匹配 - HostID: {}, Expected: {}, Actual: {}, IP: {}",
                        hostId, expectedHostType, actualHostType, remoteIp);
                    recordFailure(remoteIp, "HOST_TYPE_MISMATCH", "主机类型不匹配");
                    return ValidationResult.fail("HOST_TYPE_MISMATCH",
                        "主机ID [" + hostId + "] 已登记为 [" + actualHostType + "] 类型，不允许作为 ["
                            + expectedHostType + "] 节点连接");
                }
            }

            LocalDateTime expireTime = whitelist.getExpireTime();
            if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
                log.debug("[白名单] 已过期 - {}", hostId);
                recordFailure(remoteIp, "HOST_EXPIRED", "主机ID已过期: " + hostId);
                return ValidationResult.fail("HOST_EXPIRED", 
                    "主机ID [" + hostId + "] 已过期，请联系管理员续期");
            }

            // 6. 检查IP限制（如果配置了允许的IP列表）
            String allowedIps = whitelist.getAllowedIps();
            if (allowedIps != null && !allowedIps.trim().isEmpty()) {
                boolean ipAllowed = false;
                for (String allowedIp : allowedIps.split(",")) {
                    if (allowedIp.trim().equals(remoteIp) || allowedIp.trim().equals("*")) {
                        ipAllowed = true;
                        break;
                    }
                }
                if (!ipAllowed) {
                    log.debug("[白名单] IP不允许 - {}", hostId);
                    recordFailure(remoteIp, "IP_NOT_ALLOWED", "IP不在白名单允许列表");
                    return ValidationResult.fail("IP_NOT_ALLOWED", 
                        "当前IP [" + remoteIp + "] 不在允许列表中");
                }
            }

            log.debug("[白名单] 通过 - {}", hostId);
            return ValidationResult.success();

        } catch (Exception e) {
            log.error("[白名单] 验证异常 - HostID: {}, 错误: {}", hostId, e.getMessage(), e);
            return ValidationResult.fail("VALIDATION_ERROR", "白名单验证失败，请稍后重试");
        }
    }

    /**
     * 记录失败尝试（用于自动封禁）
     */
    private void recordFailure(String ip, String code, String reason) {
        if (ipAccessControl != null) {
            ipAccessControl.recordFailedAttempt(ip, code, reason);
        }
    }

    /**
     * 检查主机ID是否已有连接
     * 注意：在线状态存储在内存中（EngineSessionManager），此方法预留供外部调用
     * 实际的重复连接检查由 EngineSessionManager.registerEngine 处理
     *
     * @param hostId 主机ID
     * @return 验证结果
     */
    public ValidationResult checkDuplicateConnection(String hostId) {
        // 在线状态存储在内存中，由EngineSessionManager管理
        // 此方法直接返回成功，实际的重复检查在registerEngine时处理
        return ValidationResult.success();
    }

    /**
     * 检查IP是否在黑名单中
     *
     * @param remoteIp 客户端IP
     * @return 验证结果
     */
    public ValidationResult checkIpBlacklist(String remoteIp) {
        try {
            if (blacklistMapper == null) {
                return ValidationResult.success();
            }
            
            // 查询生效中的黑名单（只查询 status=1 AND del_flag=0 且未过期的记录）
            WsIpBlacklist blacklist = blacklistMapper.selectActiveByIpAddress(remoteIp);
            
            if (blacklist != null) {
                // 异步更新命中次数
                try {
                    if (ipAccessControl != null) {
                        ipAccessControl.updateHitCount(blacklist.getId());
                    } else {
                        blacklistMapper.updateHitCount(blacklist.getId());
                    }
                } catch (Exception e) {
                    log.warn("[黑名单] 更新命中次数失败: {}", e.getMessage());
                }
                
                // block_type: 1-临时封禁，2-永久封禁
                if (blacklist.getBlockType() != null && blacklist.getBlockType() >= 1) {
                    String blockTypeText = blacklist.getBlockType() == 2 ? "永久封禁" : "临时封禁";
                    String reason = blacklist.getBlockReason();
                    
                    log.warn("[黑名单拒绝] IP已被封禁 - IP: {}, 类型: {}, 原因: {}", 
                        remoteIp, blockTypeText, reason);
                    
                    String message = "IP地址 [" + remoteIp + "] 已被" + blockTypeText;
                    if (reason != null && !reason.trim().isEmpty()) {
                        message += "，原因: " + reason;
                    }
                    message += "，请联系管理员解除封禁";
                    
                    return ValidationResult.fail("IP_BLOCKED", message);
                }
            }

            return ValidationResult.success();

        } catch (Exception e) {
            log.error("[黑名单] 检查异常 - IP: {}, 错误: {}", remoteIp, e.getMessage(), e);
            return ValidationResult.success(); // 查询异常时允许连接，避免误拦截
        }
    }

    /**
     * 添加IP到黑名单
     *
     * @param ipAddress   IP地址
     * @param blockReason 封禁原因
     * @param blockType   封禁类型（1-临时，2-永久）
     * @param expireTime  过期时间（可选）
     */
    public void addToBlacklist(String ipAddress, String blockReason, int blockType, LocalDateTime expireTime) {
        if (blacklistMapper == null) {
            log.warn("[黑名单] Mapper未注入，无法添加黑名单");
            return;
        }

        try {
            WsIpBlacklist blacklist = new WsIpBlacklist();
            blacklist.setIpAddress(ipAddress);
            blacklist.setBlockReason(blockReason);
            blacklist.setBlockType(blockType);
            blacklist.setExpireTime(expireTime);
            blacklist.setStatus(1);
            
            blacklistMapper.insert(blacklist);
            log.info("[黑名单] 添加 - IP: {}, 原因: {}", ipAddress, blockReason);

        } catch (Exception e) {
            log.error("[黑名单] 添加失败 - IP: {}, 错误: {}", ipAddress, e.getMessage(), e);
        }
    }

}
