package com.wx.fbsir.business.websocket.server;

import com.wx.fbsir.business.websocket.config.WebSocketProperties;
import com.wx.fbsir.business.websocket.enums.WebSocketErrorCode;
import com.wx.fbsir.business.websocket.message.EngineMessage;
import com.wx.fbsir.business.websocket.message.MessageType;
import com.wx.fbsir.business.websocket.service.ConnectionLogService;
import com.wx.fbsir.business.websocket.service.ConnectionRateLimiter;
import com.wx.fbsir.business.websocket.service.WhitelistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine WebSocket 处理器
 * 
 * 处理 Engine 节点的 WebSocket 连接和消息
 * 解决老项目的以下问题：
 * - 单一 onMessage 方法 500+ 行 if-else
 * - 消息处理没有异常隔离
 * - 缺乏消息类型校验
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Component
public class EngineWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(EngineWebSocketHandler.class);

    private final EngineSessionManager sessionManager;
    private final WebSocketProperties properties;
    private final EngineMessageRouter messageRouter;
    private final WhitelistService whitelistService;
    private final ConnectionLogService connectionLogService;
    private final ConnectionRateLimiter rateLimiter;

    public EngineWebSocketHandler(EngineSessionManager sessionManager,
                                   WebSocketProperties properties,
                                   EngineMessageRouter messageRouter,
                                   WhitelistService whitelistService,
                                   ConnectionLogService connectionLogService,
                                   ConnectionRateLimiter rateLimiter) {
        this.sessionManager = sessionManager;
        this.properties = properties;
        this.messageRouter = messageRouter;
        this.whitelistService = whitelistService;
        this.connectionLogService = connectionLogService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = session.getId();
        String connectionIp = getRemoteIp(session);  // 连接来源IP（可能是127.0.0.1或代理IP）
        Integer remotePort = getRemotePort(session);
        
        log.debug("[WebSocket] 新连接 - 连接IP: {}", connectionIp);
        
        // 记录连接请求（先使用连接IP，注册时会更新为公网IP）
        connectionLogService.logConnection(sessionId, connectionIp, remotePort, "/ws/engine");
        
        // 注意：连接阶段不做IP验证，等待客户端注册时上报公网IP后再验证
        // 这样可以避免本地开发时127.0.0.1被误判
        
        EngineSession engineSession = sessionManager.addSession(session);
        if (engineSession == null) {
            // 连接数超限，拒绝连接
            log.warn("[WebSocket] 连接数超限 - IP: {}", connectionIp);
            connectionLogService.updateRejected(sessionId, null,
                ConnectionLogService.STATUS_REJECT_WHITELIST, "连接数超限");
            
            sendErrorAndClose(session, WebSocketErrorCode.CONNECTION_LIMIT_EXCEEDED.getMessage(), 
                WebSocketErrorCode.CONNECTION_LIMIT_EXCEEDED.getCode(), CloseStatus.SERVICE_OVERLOAD);
            return;
        }
        
        // 设置消息大小限制
        session.setTextMessageSizeLimit(properties.getMaxMessageSize());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = session.getId();
        String payload = message.getPayload();
        
        log.debug("[WebSocket] 收到消息 - SessionID: {}, 长度: {} 字符", sessionId, payload.length());
        
        // 解析消息
        EngineMessage engineMessage = EngineMessage.fromJson(payload);
        if (engineMessage == null) {
            log.warn("[WebSocket] 消息解析失败 - SessionID: {}, 原始消息: {}", 
                sessionId, payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
            sendError(session, WebSocketErrorCode.INVALID_MESSAGE_FORMAT.getMessage(), 
                WebSocketErrorCode.INVALID_MESSAGE_FORMAT.getCode());
            return;
        }
        
        // 更新会话活跃时间
        EngineSession engineSession = sessionManager.getSession(sessionId);
        if (engineSession != null) {
            engineSession.updateActiveTime();
        }
        
        // 处理系统消息
        if (handleSystemMessage(session, engineMessage)) {
            return;
        }
        
        // 检查会话是否已注册
        if (engineSession == null || engineSession.getStatus() != EngineSession.SessionStatus.REGISTERED) {
            log.warn("[WebSocket] 会话未注册，拒绝处理业务消息 - SessionID: {}", sessionId);
            sendError(session, WebSocketErrorCode.NOT_REGISTERED.getMessage(), WebSocketErrorCode.NOT_REGISTERED.getCode());
            return;
        }
        
        // 路由到业务处理器
        messageRouter.route(engineSession, engineMessage);
    }

    /**
     * 处理系统消息
     *
     * @return true 已处理，false 需要继续处理
     */
    private boolean handleSystemMessage(WebSocketSession session, EngineMessage message) {
        MessageType type = message.getMessageType();
        
        switch (type) {
            case ENGINE_REGISTER:
                return handleRegister(session, message);
                
            case HEARTBEAT_PING:
                return handleHeartbeat(session, message);
                
            case ENGINE_UNREGISTER:
                return handleUnregister(session, message);
                
            default:
                return false;
        }
    }

    /**
     * 处理 Engine 注册（包含白名单验证）
     */
    private boolean handleRegister(WebSocketSession session, EngineMessage message) {
        String sessionId = session.getId();
        String remoteIp = getRemoteIp(session);
        
        // 获取注册信息（hostId 从 engineId 字段获取）
        String hostId = message.getEngineId();
        // 优先从顶层字段获取version，兼容旧版本从payload获取
        String version = message.getVersion();
        if (version == null || version.isEmpty()) {
            version = message.getPayloadValue("version");
        }
        String deviceId = message.getPayloadValue("deviceId");
        Object capabilitiesObj = message.getPayloadValue("capabilities");
        
        // 客户端上报的IP仅用于诊断，绝不参与授权或白名单判断。
        String clientLocalIp = message.getPayloadValue("localIp");
        String clientPublicIp = message.getPayloadValue("publicIp");
        String connectionIp = remoteIp;
        String primaryIp = remoteIp;
        
        // 收集设备信息用于记录
        Map<String, Object> deviceInfo = new HashMap<>();
        deviceInfo.put("hostname", message.getPayloadValue("hostname"));
        deviceInfo.put("osName", message.getPayloadValue("osName"));
        deviceInfo.put("osVersion", message.getPayloadValue("osVersion"));
        deviceInfo.put("javaVersion", message.getPayloadValue("javaVersion"));
        deviceInfo.put("macAddress", message.getPayloadValue("macAddress"));
        deviceInfo.put("localIp", clientLocalIp);
        deviceInfo.put("publicIp", clientPublicIp);
        deviceInfo.put("connectionIp", connectionIp);
        
        log.info("[WebSocket] 注册请求 - HostID: {}, 连接IP: {}, 公网IP: {}, 主要IP: {}", 
            hostId, connectionIp, clientPublicIp, primaryIp);
        
        // 连接记录与访问控制统一使用服务端观察到的 TCP 对端地址。
        connectionLogService.updateConnectionIp(sessionId, primaryIp);
        
        // 1. 验证主机ID是否为空
        if (hostId == null || hostId.trim().isEmpty()) {
            log.warn("[WebSocket] 拒绝: 主机ID为空 - 公网IP: {}", primaryIp);
            connectionLogService.updateRejected(sessionId, hostId, 
                ConnectionLogService.STATUS_REJECT_WHITELIST, "主机ID为空",
                WebSocketErrorCode.EMPTY_HOST_ID.getCode());
            
            // 记录失败并发送错误（使用公网IP）
            rateLimiter.recordFailure(primaryIp, null, "主机ID为空");
            sendErrorAndClose(session, WebSocketErrorCode.EMPTY_HOST_ID.getMessage(), 
                WebSocketErrorCode.EMPTY_HOST_ID.getCode(), CloseStatus.POLICY_VIOLATION);
            return true;
        }
        
        // 2. 检查公网IP频率限制
        ConnectionRateLimiter.RateLimitResult ipRateLimit = rateLimiter.checkIpBan(primaryIp);
        if (!ipRateLimit.isAllowed()) {
            log.warn("[WebSocket] 公网IP被限流 - IP: {}, 原因: {}", primaryIp, ipRateLimit.getReason());
            connectionLogService.updateRejected(sessionId, hostId,
                ConnectionLogService.STATUS_REJECT_BLACKLIST, ipRateLimit.getReason(),
                WebSocketErrorCode.CONNECTION_RATE_LIMIT.getCode());
            
            String errorMsg = WebSocketErrorCode.CONNECTION_RATE_LIMIT.getFormattedMessage(
                primaryIp, ipRateLimit.getRemainingMinutes());
            sendErrorAndClose(session, errorMsg, WebSocketErrorCode.CONNECTION_RATE_LIMIT.getCode(), CloseStatus.NOT_ACCEPTABLE);
            return true;
        }
        
        // 3. 检查公网IP黑名单
        WhitelistService.ValidationResult ipBlacklistResult = whitelistService.checkIpBlacklist(primaryIp);
        if (!ipBlacklistResult.isValid()) {
            log.warn("[WebSocket] 公网IP在黑名单中 - IP: {}, 原因: {}", primaryIp, ipBlacklistResult.getMessage());
            connectionLogService.updateRejected(sessionId, hostId, 
                ConnectionLogService.STATUS_REJECT_BLACKLIST, ipBlacklistResult.getMessage(),
                WebSocketErrorCode.IP_IN_BLACKLIST.getCode());
            
            rateLimiter.recordFailure(primaryIp, hostId, "IP在黑名单中");
            String errorMsg = WebSocketErrorCode.IP_IN_BLACKLIST.getFormattedMessage(primaryIp, ipBlacklistResult.getMessage());
            sendErrorAndClose(session, errorMsg, WebSocketErrorCode.IP_IN_BLACKLIST.getCode(), CloseStatus.NOT_ACCEPTABLE);
            return true;
        }
        
        // 4. 检查主机ID频率限制
        ConnectionRateLimiter.RateLimitResult hostRateLimit = rateLimiter.checkHostIdBan(hostId);
        if (!hostRateLimit.isAllowed()) {
            log.warn("[WebSocket] 主机ID被限流 - HostID: {}, 原因: {}", hostId, hostRateLimit.getReason());
            connectionLogService.updateRejected(sessionId, hostId,
                ConnectionLogService.STATUS_REJECT_WHITELIST, hostRateLimit.getReason(),
                WebSocketErrorCode.CONNECTION_RATE_LIMIT.getCode());
            
            String errorMsg = WebSocketErrorCode.CONNECTION_RATE_LIMIT.getFormattedMessage(
                primaryIp, hostRateLimit.getRemainingMinutes());
            sendErrorAndClose(session, errorMsg, WebSocketErrorCode.CONNECTION_RATE_LIMIT.getCode(), CloseStatus.POLICY_VIOLATION);
            return true;
        }
        
        // 5. 验证白名单（使用公网IP进行验证）
        WhitelistService.ValidationResult whitelistResult = whitelistService.validateHostId(hostId, primaryIp, "engine");
        if (!whitelistResult.isValid()) {
            log.warn("[拒绝] {} - HostID: {}, 公网IP: {}", whitelistResult.getCode(), hostId, primaryIp);
            
            // 记录失败并发送对应错误码（使用公网IP）
            rateLimiter.recordFailure(primaryIp, hostId, whitelistResult.getMessage());
            
            WebSocketErrorCode errorCode = mapValidationCodeToErrorCode(whitelistResult.getCode());
            String errorMsg = formatErrorMessage(errorCode, hostId, primaryIp, whitelistResult.getMessage());
            
            connectionLogService.updateRejected(sessionId, hostId, 
                ConnectionLogService.STATUS_REJECT_WHITELIST, whitelistResult.getMessage(),
                errorCode.getCode());
            
            sendErrorAndClose(session, errorMsg, errorCode.getCode(), CloseStatus.POLICY_VIOLATION);
            return true;
        }
        
        // 6. 检查是否已有连接（单连接限制）
        WhitelistService.ValidationResult duplicateResult = whitelistService.checkDuplicateConnection(hostId);
        if (!duplicateResult.isValid()) {
            log.warn("[WebSocket] 拒绝: 重复连接 - HostID: {}", hostId);
            connectionLogService.updateRejected(sessionId, hostId, 
                ConnectionLogService.STATUS_REJECT_DUPLICATE, duplicateResult.getMessage(),
                WebSocketErrorCode.DUPLICATE_CONNECTION.getCode());
            
            rateLimiter.recordFailure(primaryIp, hostId, "重复连接");
            String errorMsg = WebSocketErrorCode.DUPLICATE_CONNECTION.getFormattedMessage(hostId);
            sendErrorAndClose(session, errorMsg, WebSocketErrorCode.DUPLICATE_CONNECTION.getCode(), CloseStatus.POLICY_VIOLATION);
            return true;
        }
        
        // 7. 解析能力列表（必须是 Map 格式）
        List<Map<String, Object>> capabilities = new java.util.ArrayList<>();
        if (capabilitiesObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                if (item instanceof Map<?, ?> mapItem) {
                    Map<String, Object> cap = new HashMap<>();
                    mapItem.forEach((k, v) -> cap.put(String.valueOf(k), v));
                    capabilities.add(cap);
                }
            }
        }
        
        // 8. 注册 Engine（传入设备指纹用于同设备重连检测）
        boolean success = sessionManager.registerEngine(sessionId, hostId, deviceId, version, capabilities);
        
        if (success) {
            // 注册成功后，deviceId已在registerEngine中设置，无需重复设置
            
            // 更新设备信息到会话
            sessionManager.updateDeviceInfo(sessionId, deviceInfo);
            
            // 记录注册成功（在线状态存储在内存EngineSessionManager中，不写数据库）
            connectionLogService.updateRegistered(sessionId, hostId, deviceId, version, deviceInfo);
            
            // 发送注册确认
            EngineMessage ackMsg = EngineMessage.builder()
                .type(MessageType.ENGINE_REGISTER_ACK)
                .engineId(hostId)
                .payload("message", "注册成功")
                .payload("serverTime", System.currentTimeMillis())
                .build();
            
            try {
                session.sendMessage(new TextMessage(ackMsg.toJson()));
                log.info("[Engine] 节点已注册 → ID:{} | 版本:{}", hostId, version);
            } catch (Exception e) {
                log.error("[WebSocket] 发送注册确认失败 - HostID: {}, 错误: {}", hostId, e.getMessage());
            }
        } else {
            connectionLogService.updateRejected(sessionId, hostId, 
                ConnectionLogService.STATUS_REJECT_DUPLICATE, "EngineID已被占用",
                WebSocketErrorCode.DUPLICATE_CONNECTION.getCode());
            
            rateLimiter.recordFailure(primaryIp, hostId, "重复连接");
            String errorMsg = WebSocketErrorCode.DUPLICATE_CONNECTION.getFormattedMessage(hostId);
            sendErrorAndClose(session, errorMsg, WebSocketErrorCode.DUPLICATE_CONNECTION.getCode(), 
                new CloseStatus(4008, "EngineID已被占用"));
        }
        
        return true;
    }

    /**
     * 处理心跳（更新内存中的统计，不写数据库）
     * 
     * 说明：
     * - 更新心跳时间戳
     * - 如果心跳消息携带性能数据，则更新到 EngineSession
     * - 发送心跳响应
     */
    private boolean handleHeartbeat(WebSocketSession session, EngineMessage message) {
        String sessionId = session.getId();
        EngineSession engineSession = sessionManager.getSession(sessionId);
        
        if (engineSession != null) {
            // 更新心跳时间
            engineSession.updateHeartbeatTime();
            
            // 检查是否携带性能数据（每5分钟更新一次）
            Object performanceData = message.getPayloadValue("performance");
            if (performanceData != null && performanceData instanceof Map) {
                // 类型安全的转换
                @SuppressWarnings("unchecked")
                Map<String, Object> perfMap = (Map<String, Object>) performanceData;
                
                // 更新性能数据到会话中
                engineSession.updatePerformanceData(perfMap);
                
                // 记录性能数据日志（可选，用于监控）
                log.debug("[Engine] {} 性能更新 - CPU:{}% | 内存:{}% | 磁盘:{}% | JVM:{}%",
                    engineSession.getEngineId(),
                    perfMap.get("cpuUsagePercent"),
                    perfMap.get("memoryUsagePercent"),
                    perfMap.get("diskUsagePercent"),
                    perfMap.get("jvmUsagePercent"));
            }
        }
        
        // 发送心跳响应
        EngineMessage pongMsg = EngineMessage.builder()
            .type(MessageType.HEARTBEAT_PONG)
            .payload("serverTime", System.currentTimeMillis())
            .build();
        
        try {
            session.sendMessage(new TextMessage(pongMsg.toJson()));
        } catch (Exception e) {
            log.warn("[WebSocket] 发送心跳响应失败 - SessionID: {}", sessionId);
        }
        
        return true;
    }

    /**
     * 处理 Engine 注销
     */
    private boolean handleUnregister(WebSocketSession session, EngineMessage message) {
        String sessionId = session.getId();
        String hostId = message.getEngineId();
        
        log.debug("[WebSocket] Engine主动注销 - HostID: {}", hostId);
        
        // 标记为主动注销，在afterConnectionClosed中会检查这个标记
        EngineSession engineSession = sessionManager.getSession(sessionId);
        if (engineSession != null) {
            engineSession.markAsNormalClose();
        }
        
        // 注意：不在此处移除session，由afterConnectionClosed统一处理断开逻辑
        return true;
    }

    /**
     * 发送错误消息
     */
    private void sendError(WebSocketSession session, String message, String code) {
        try {
            EngineMessage errorMsg = EngineMessage.builder()
                .type(MessageType.ERROR)
                .payload("message", message)
                .payload("code", code)
                .build();
            session.sendMessage(new TextMessage(errorMsg.toJson()));
        } catch (Exception e) {
            log.error("[WebSocket] 发送错误消息失败 - 错误: {}", e.getMessage());
        }
    }

    /**
     * 发送错误消息并关闭连接
     */
    private void sendErrorAndClose(WebSocketSession session, String message, String code, CloseStatus closeStatus) {
        try {
            EngineMessage errorMsg = EngineMessage.builder()
                .type(MessageType.ERROR)
                .payload("message", message)
                .payload("code", code)
                .build();
            session.sendMessage(new TextMessage(errorMsg.toJson()));
            session.close(closeStatus);
        } catch (Exception e) {
            log.error("[WebSocket] 发送错误消息并关闭连接失败 - 错误: {}", e.getMessage());
            try {
                session.close(closeStatus);
            } catch (Exception ex) {
                log.error("[WebSocket] 关闭连接失败 - 错误: {}", ex.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = session.getId();
        
        // 转换为友好的错误原因
        String friendlyReason = getFriendlyErrorReason(exception);
        log.error("[WebSocket] 传输错误 - SessionID: {}, 原因: {}", sessionId, friendlyReason);
        
        // 记录错误到会话信息，会话清理由afterConnectionClosed统一处理
        EngineSession engineSession = sessionManager.getSession(sessionId);
        if (engineSession != null) {
            engineSession.recordError(friendlyReason);
        }
        
        // 注意：不在此处移除session，由afterConnectionClosed统一处理断开逻辑
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = session.getId();
        
        // 获取会话信息，记录统计并清理
        EngineSession engineSession = sessionManager.getSession(sessionId);
        if (engineSession == null) {
            log.debug("[Engine] 会话不存在，可能已被清理 - SessionID: {}", sessionId);
            sessionManager.removeSession(sessionId);
            return;
        }
        
        String engineId = engineSession.getEngineId();
        
        // 只有已注册的有效连接断开时才更新数据库状态
        if (engineId != null && !engineId.startsWith("pending-") 
            && engineSession.getStatus() == EngineSession.SessionStatus.REGISTERED) {
            
            // 根据状态码判断断开类型和原因
            int dbStatus;
            String closeReason = getFriendlyCloseReason(status, engineSession.getLastError());
            
            // 计算连接持续时间
            long connectionDuration = (System.currentTimeMillis() - engineSession.getRegisteredTime()) / 1000;
            long lastHeartbeatAgo = (System.currentTimeMillis() - engineSession.getLastHeartbeatTime()) / 1000;
            
            if (status.getCode() == 4007) {
                dbStatus = ConnectionLogService.STATUS_ADMIN_DISCONNECT;
            } else if (engineSession.isNormalClose()) {
                dbStatus = ConnectionLogService.STATUS_NORMAL_CLOSE;
                closeReason = "主动注销";
            } else {
                dbStatus = ConnectionLogService.STATUS_ABNORMAL_CLOSE;
            }
            
            // 同步更新数据库状态
            try {
                connectionLogService.updateDisconnectedWithStatsSync(
                    sessionId, status.getCode(), closeReason, dbStatus,
                    engineSession.getMessageSent(), engineSession.getMessageReceived(),
                    engineSession.getHeartbeatCount(), engineSession.getErrorCount(),
                    engineSession.getLastError()
                );
                
                // 🔍 增强日志：记录断开详情
                log.info("[Engine] 主机 {} 已断开 - {} | 状态码:{} | 持续:{}秒 | 心跳:{}次 | 上次心跳:{}秒前 | 消息:发送{}收到{}", 
                    engineId, closeReason, status.getCode(), connectionDuration, 
                    engineSession.getHeartbeatCount(), lastHeartbeatAgo,
                    engineSession.getMessageSent(), engineSession.getMessageReceived());
            } catch (Exception e) {
                log.error("[Engine] 更新连接记录失败 - SessionID: {}, 错误: {}", sessionId, e.getMessage());
            }
        } else {
            log.debug("[Engine] 跳过数据库更新 - EngineID: {}, Status: {}", 
                engineId, engineSession.getStatus());
        }
        
        // 清理会话
        sessionManager.removeSession(sessionId);
    }

    /**
     * 获取服务端观察到的 TCP 对端 IP。代理头可被客户端伪造，不作为
     * Engine 注册、黑白名单或限流的信任来源。
     */
    private String getRemoteIp(WebSocketSession session) {
        try {
            if (session.getRemoteAddress() != null) {
                return session.getRemoteAddress().getAddress().getHostAddress();
            }
        } catch (Exception e) {
            log.warn("[WebSocket] 获取客户端IP失败: {}", e.getMessage());
        }
        return "unknown";
    }
    
    /**
     * 获取客户端端口
     */
    private Integer getRemotePort(WebSocketSession session) {
        try {
            if (session.getRemoteAddress() != null) {
                return session.getRemoteAddress().getPort();
            }
        } catch (Exception e) {
            log.warn("[WebSocket] 获取客户端端口失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取友好的错误原因说明
     */
    private String getFriendlyErrorReason(Throwable exception) {
        if (exception == null) {
            return "未知错误";
        }
        
        String className = exception.getClass().getSimpleName();
        String message = exception.getMessage();
        
        // 根据异常类型返回友好说明
        return switch (className) {
            case "ClosedChannelException" -> "连接通道已关闭（主节点重启或网络中断）";
            case "IOException" -> {
                if (message != null && message.contains("Connection reset")) {
                    yield "连接被重置（网络异常或对端关闭）";
                } else if (message != null && message.contains("Broken pipe")) {
                    yield "连接管道断裂（网络中断）";
                }
                yield "IO异常: " + (message != null ? message : "未知");
            }
            case "SocketTimeoutException" -> "连接超时";
            case "ConnectException" -> "无法建立连接";
            case "EOFException" -> "连接意外终止（对端关闭）";
            default -> className + ": " + (message != null ? message : "无详细信息");
        };
    }

    /**
     * 获取友好的关闭原因说明
     */
    private String getFriendlyCloseReason(CloseStatus status, String lastError) {
        int code = status.getCode();
        String reason = status.getReason();
        
        // 如果有原始原因，优先使用
        if (reason != null && !reason.isEmpty()) {
            return reason;
        }
        
        // 根据状态码返回友好说明
        return switch (code) {
            case 1000 -> "正常关闭";
            case 1001 -> "端点离开（页面关闭或服务重启）";
            case 1002 -> "协议错误";
            case 1003 -> "不支持的数据类型";
            case 1005 -> "无状态码（异常关闭）";
            case 1006 -> {
                // 1006 是异常关闭，尝试从 lastError 获取更多信息
                if (lastError != null && !lastError.isEmpty()) {
                    if (lastError.contains("ClosedChannelException") || lastError.contains("通道已关闭")) {
                        yield "主节点重启或网络中断";
                    }
                    yield lastError;
                }
                yield "连接异常关闭（可能是主节点重启或网络中断）";
            }
            case 1007 -> "消息格式错误";
            case 1008 -> "策略违规";
            case 1009 -> "消息过大";
            case 1010 -> "客户端期望的扩展未被支持";
            case 1011 -> "服务器内部错误";
            case 1012 -> "服务重启";
            case 1013 -> "服务过载，稍后重试";
            case 1015 -> "TLS握手失败";
            case 4007 -> "管理员断开";
            default -> "关闭码: " + code;
        };
    }
    
    /**
     * 将白名单验证码映射到错误码
     */
    private WebSocketErrorCode mapValidationCodeToErrorCode(String validationCode) {
        return switch (validationCode) {
            case "EMPTY_HOST_ID" -> WebSocketErrorCode.EMPTY_HOST_ID;
            case "HOST_NOT_IN_WHITELIST" -> WebSocketErrorCode.HOST_ID_NOT_AUTHORIZED;
            case "HOST_DISABLED" -> WebSocketErrorCode.HOST_ID_DISABLED;
            case "HOST_EXPIRED" -> WebSocketErrorCode.HOST_ID_EXPIRED;
            case "HOST_TYPE_MISMATCH" -> WebSocketErrorCode.HOST_TYPE_MISMATCH;
            case "IP_NOT_ALLOWED" -> WebSocketErrorCode.IP_NOT_IN_WHITELIST;
            case "IP_BLOCKED" -> WebSocketErrorCode.IP_IN_BLACKLIST;
            default -> WebSocketErrorCode.UNKNOWN_ERROR;
        };
    }
    
    /**
     * 格式化错误消息
     */
    private String formatErrorMessage(WebSocketErrorCode errorCode, String hostId, String ip, String originalMessage) {
        return switch (errorCode) {
            case HOST_ID_NOT_AUTHORIZED, HOST_ID_DISABLED -> errorCode.getFormattedMessage(hostId);
            case IP_NOT_IN_WHITELIST -> errorCode.getFormattedMessage(ip, hostId);
            case IP_IN_BLACKLIST -> errorCode.getFormattedMessage(ip, originalMessage);
            default -> originalMessage;
        };
    }
}
