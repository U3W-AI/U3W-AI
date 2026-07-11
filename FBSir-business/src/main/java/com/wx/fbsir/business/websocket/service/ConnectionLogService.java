package com.wx.fbsir.business.websocket.service;

import com.wx.fbsir.business.websocket.domain.WsConnectionLog;
import com.wx.fbsir.business.websocket.mapper.WsConnectionLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 连接记录服务
 * 
 * 设计原则：
 * 1. 连接时记录基本信息
 * 2. 注册时更新设备信息
 * 3. 断开时统一写入统计信息（消息数、心跳数等）
 * 4. 避免高频写入数据库
 * 5. 使用MyBatis Mapper操作数据库
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Service
public class ConnectionLogService {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLogService.class);

    /**
     * 连接状态常量
     */
    public static final int STATUS_CONNECTING = 0;       // 连接中
    public static final int STATUS_REGISTERED = 1;       // 已注册
    public static final int STATUS_NORMAL_CLOSE = 2;     // 正常断开
    public static final int STATUS_ABNORMAL_CLOSE = 3;   // 异常断开
    public static final int STATUS_REJECT_WHITELIST = 4; // 被拒绝（白名单）
    public static final int STATUS_REJECT_BLACKLIST = 5; // 被拒绝（黑名单）
    public static final int STATUS_REJECT_DUPLICATE = 6; // 被拒绝（重复连接）
    public static final int STATUS_ADMIN_DISCONNECT = 7; // 被管理员断开
    public static final int STATUS_SERVER_RESTART = 8;   // 主节点重启导致断开

    @Autowired(required = false)
    private WsConnectionLogMapper connectionLogMapper;

    /**
     * 应用启动时清理旧的活跃连接状态
     * 将所有状态为0（连接中）或1（已注册）的记录标记为"主节点重启"
     */
    @EventListener(ApplicationReadyEvent.class)
    public void cleanupActiveConnectionsOnStartup() {
        if (connectionLogMapper == null) {
            log.debug("[连接记录] Mapper未注入，跳过启动清理");
            return;
        }

        try {
            // 查询所有活跃连接（状态为0或1）
            WsConnectionLog query = new WsConnectionLog();
            List<WsConnectionLog> activeLogs = connectionLogMapper.selectList(query);
            
            int cleanedCount = 0;
            for (WsConnectionLog log : activeLogs) {
                if (log.getStatus() == STATUS_CONNECTING || log.getStatus() == STATUS_REGISTERED) {
                    // 构建详细的错误信息
                    String errorMessage = String.format(
                        "主节点重启导致连接失效 - 原状态: %s, 主机ID: %s, 连接时间: %s",
                        log.getStatus() == STATUS_CONNECTING ? "连接中" : "已注册",
                        log.getHostId() != null ? log.getHostId() : "未知",
                        log.getConnectTime()
                    );
                    
                    // 更新为主节点重启状态，并记录错误信息
                    connectionLogMapper.updateDisconnected(
                        log.getSessionId(),
                        STATUS_SERVER_RESTART,
                        1006,  // WebSocket异常关闭状态码
                        "主节点重启",
                        0L, 0L, 0, 0, 
                        errorMessage  // 记录详细错误信息到last_error字段
                    );
                    cleanedCount++;
                }
            }
            
            if (cleanedCount > 0) {
                log.info("[连接记录] 启动清理完成 - 已将 {} 个活跃连接标记为'主节点重启'", cleanedCount);
            } else {
                log.debug("[连接记录] 启动清理完成 - 无需清理的活跃连接");
            }

        } catch (Exception e) {
            log.error("[连接记录] 启动清理失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 记录连接请求
     *
     * @param sessionId  会话ID
     * @param remoteIp   客户端IP
     * @param remotePort 客户端端口
     * @param requestUri 请求URI
     */
    @Async
    public void logConnection(String sessionId, String remoteIp, Integer remotePort, String requestUri) {
        if (connectionLogMapper == null) {
            log.debug("[连接记录] Mapper未注入，跳过记录");
            return;
        }

        try {
            WsConnectionLog connectionLog = new WsConnectionLog();
            connectionLog.setSessionId(sessionId);
            connectionLog.setRemoteIp(remoteIp);
            connectionLog.setRemotePort(remotePort);
            connectionLog.setRequestUri(requestUri);
            connectionLog.setStatus(STATUS_CONNECTING);
            
            connectionLogMapper.insert(connectionLog);
            log.debug("[连接记录] 新连接 - IP: {}", remoteIp);

        } catch (Exception e) {
            log.error("[连接记录] 记录失败 - SessionID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 更新连接为已注册状态
     *
     * @param sessionId     会话ID
     * @param hostId        主机ID
     * @param deviceId      设备指纹ID
     * @param engineVersion 引擎版本
     * @param deviceInfo    设备信息
     */
    @Async
    public void updateRegistered(String sessionId, String hostId, String deviceId, 
                                  String engineVersion, Map<String, Object> deviceInfo) {
        if (connectionLogMapper == null) {
            return;
        }

        try {
            String hostname = deviceInfo != null ? (String) deviceInfo.get("hostname") : null;
            String osName = deviceInfo != null ? (String) deviceInfo.get("osName") : null;
            String osVersion = deviceInfo != null ? (String) deviceInfo.get("osVersion") : null;
            String javaVersion = deviceInfo != null ? (String) deviceInfo.get("javaVersion") : null;
            String macAddress = deviceInfo != null ? (String) deviceInfo.get("macAddress") : null;
            
            connectionLogMapper.updateRegistered(sessionId, hostId, deviceId, engineVersion,
                hostname, osName, osVersion, javaVersion, macAddress, STATUS_REGISTERED);
            
            log.debug("[连接记录] 注册成功 - HostID: {}", hostId);

        } catch (Exception e) {
            log.error("[连接记录] 更新注册失败 - SessionID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 更新连接记录的IP地址（注册时使用客户端上报的公网IP替换连接IP）
     *
     * @param sessionId 会话ID
     * @param publicIp  公网IP
     */
    @Async
    public void updateConnectionIp(String sessionId, String publicIp) {
        if (connectionLogMapper == null || publicIp == null || publicIp.isEmpty()) {
            return;
        }

        try {
            connectionLogMapper.updateConnectionIp(sessionId, publicIp);
            log.debug("[记录] 更新IP - SessionID: {}, 公网IP: {}", sessionId, publicIp);
        } catch (Exception e) {
            log.error("[连接记录] 更新IP失败 - SessionID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 更新连接为被拒绝状态
     *
     * @param sessionId    会话ID
     * @param hostId       主机ID（可能是伪造的）
     * @param status       拒绝状态
     * @param rejectReason 拒绝原因
     */
    @Async
    public void updateRejected(String sessionId, String hostId, int status, String rejectReason) {
        updateRejected(sessionId, hostId, status, rejectReason, null);
    }
    
    /**
     * 更新连接为被拒绝状态（带错误码）
     *
     * @param sessionId    会话ID
     * @param hostId       主机ID（可能是伪造的）
     * @param status       拒绝状态
     * @param rejectReason 拒绝原因
     * @param errorCode    错误码（E1001-E9999）
     */
    @Async
    public void updateRejected(String sessionId, String hostId, int status, String rejectReason, String errorCode) {
        if (connectionLogMapper == null) {
            return;
        }

        try {
            connectionLogMapper.updateRejected(sessionId, hostId, status, rejectReason, errorCode);
            log.debug("[记录] 拒绝 - HostID: {}, 错误码: {}", hostId, errorCode);

        } catch (Exception e) {
            log.error("[连接记录] 记录拒绝失败 - SessionID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 更新连接断开状态（包含统计信息，仅在断开时写入一次）
     * 
     * ⚠️ 重要：此方法使用异步执行，可能有延迟
     * 如果需要立即更新数据库，请使用 updateDisconnectedWithStatsSync
     *
     * @param sessionId       会话ID
     * @param closeCode       关闭状态码
     * @param closeReason     关闭原因
     * @param status          断开状态
     * @param messageSent     发送消息数
     * @param messageReceived 接收消息数
     * @param heartbeatCount  心跳次数
     * @param errorCount      错误次数
     * @param lastError       最后错误信息
     */
    @Async
    public void updateDisconnectedWithStats(String sessionId, Integer closeCode, String closeReason,
                                             int status, long messageSent, long messageReceived,
                                             int heartbeatCount, int errorCount, String lastError) {
        updateDisconnectedWithStatsSync(sessionId, closeCode, closeReason, status, 
            messageSent, messageReceived, heartbeatCount, errorCount, lastError);
    }

    /**
     * 更新连接断开状态（同步版本，立即执行）
     * 
     * 用于连接断开时立即更新数据库，确保前端能实时看到状态变化
     *
     * @param sessionId       会话ID
     * @param closeCode       关闭状态码
     * @param closeReason     关闭原因
     * @param status          断开状态
     * @param messageSent     发送消息数
     * @param messageReceived 接收消息数
     * @param heartbeatCount  心跳次数
     * @param errorCount      错误次数
     * @param lastError       最后错误信息
     */
    public void updateDisconnectedWithStatsSync(String sessionId, Integer closeCode, String closeReason,
                                                 int status, long messageSent, long messageReceived,
                                                 int heartbeatCount, int errorCount, String lastError) {
        if (connectionLogMapper == null) {
            log.error("[连接记录] Mapper未注入，无法更新数据库");
            return;
        }

        try {
            int updateCount = connectionLogMapper.updateDisconnected(sessionId, status, closeCode, closeReason,
                messageSent, messageReceived, heartbeatCount, errorCount, lastError);
            
            if (updateCount == 0) {
                log.warn("[连接记录] 更新影响行数为0，SessionID可能不存在: {}", sessionId);
            } else {
                log.debug("[连接记录] 断开状态更新成功 - SessionID: {}, Status: {}", sessionId, status);
            }

        } catch (Exception e) {
            log.error("[连接记录] 更新断开状态失败 - SessionID: {}, 错误: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * 简化的断开状态更新（用于拒绝连接等场景）
     */
    @Async
    public void updateDisconnected(String sessionId, Integer closeCode, String closeReason, boolean isNormal) {
        int status = isNormal ? STATUS_NORMAL_CLOSE : STATUS_ABNORMAL_CLOSE;
        updateDisconnectedWithStats(sessionId, closeCode, closeReason, status, 0, 0, 0, 0, null);
    }

    /**
     * 判断断开原因是否为主节点重启导致
     * 
     * 特征：
     * - ClosedChannelException: 通道被关闭（主节点重启）
     * - 状态码 1006: 异常关闭（未正常握手关闭）
     */
    public static boolean isServerRestartDisconnect(Integer closeCode, String closeReason) {
        // 状态码 1006 表示异常关闭
        if (closeCode != null && closeCode == 1006) {
            return true;
        }
        // ClosedChannelException 通常表示服务端重启
        if (closeReason != null && closeReason.contains("ClosedChannelException")) {
            return true;
        }
        return false;
    }

    /**
     * 获取可读的断开原因描述
     */
    public static String getReadableDisconnectReason(Integer closeCode, String closeReason) {
        if (isServerRestartDisconnect(closeCode, closeReason)) {
            return "主节点重启导致连接断开";
        }
        if (closeCode != null) {
            return switch (closeCode) {
                case 1000 -> "正常关闭";
                case 1001 -> "端点离开";
                case 1002 -> "协议错误";
                case 1003 -> "数据类型错误";
                case 1006 -> "异常关闭（可能是主节点重启）";
                case 1007 -> "数据格式错误";
                case 1008 -> "策略违规";
                case 1009 -> "消息过大";
                case 1011 -> "服务器错误";
                case 4007 -> "被管理员断开";
                default -> closeReason != null ? closeReason : "未知原因";
            };
        }
        return closeReason != null ? closeReason : "未知原因";
    }
}
