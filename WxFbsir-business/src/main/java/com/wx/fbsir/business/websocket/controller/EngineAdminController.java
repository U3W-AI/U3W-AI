package com.wx.fbsir.business.websocket.controller;

import com.wx.fbsir.business.websocket.config.WebSocketProperties;
import com.wx.fbsir.business.websocket.message.EngineMessage;
import com.wx.fbsir.business.websocket.message.MessageType;
import com.wx.fbsir.business.websocket.server.EngineSession;
import com.wx.fbsir.business.websocket.server.EngineSessionManager;
import com.wx.fbsir.business.websocket.service.ConnectionLogService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.enums.BusinessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine 管理控制器
 * 
 * 提供 Engine 连接管理和监控接口
 *
 * @author wxfbsir
 * @date 2025-12-15
 */
@RestController
@RequestMapping("/ws/admin")
public class EngineAdminController {

    private static final Logger log = LoggerFactory.getLogger(EngineAdminController.class);

    private final EngineSessionManager sessionManager;
    private final ConnectionLogService connectionLogService;
    private final WebSocketProperties properties;

    private static final DateTimeFormatter FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public EngineAdminController(EngineSessionManager sessionManager,
                                  ConnectionLogService connectionLogService,
                                  WebSocketProperties properties) {
        this.sessionManager = sessionManager;
        this.connectionLogService = connectionLogService;
        this.properties = properties;
    }

    /**
     * 获取连接统计信息
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", sessionManager.getStats());
        result.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有已连接的 Engine 列表
     */
    @GetMapping("/engines")
    public ResponseEntity<Map<String, Object>> getEngines() {
        List<EngineSession> sessions = sessionManager.getRegisteredSessions();
        
        List<Map<String, Object>> engineList = sessions.stream()
            .map(this::sessionToMap)
            .collect(Collectors.toList());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("total", engineList.size());
        result.put("engines", engineList);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定 Engine 的详细信息
     */
    @GetMapping("/engines/{engineId}")
    public ResponseEntity<Map<String, Object>> getEngine(@PathVariable String engineId) {
        EngineSession session = sessionManager.getSessionByEngineId(engineId);
        
        Map<String, Object> result = new HashMap<>();
        if (session == null) {
            result.put("success", false);
            result.put("message", "Engine 不存在: " + engineId);
            return ResponseEntity.ok(result);
        }
        
        result.put("success", true);
        result.put("engine", sessionToMap(session));
        return ResponseEntity.ok(result);
    }

    /**
     * 断开指定 Engine 的连接
     */
    @PreAuthorize("@ss.hasPermi('business:host:connection:disconnect')")
    @Log(title = "强制下线主机", businessType = BusinessType.DELETE)
    @DeleteMapping("/engines/{engineId}")
    public ResponseEntity<Map<String, Object>> disconnectEngine(@PathVariable String engineId) {
        EngineSession session = sessionManager.getSessionByEngineId(engineId);
        
        Map<String, Object> result = new HashMap<>();
        if (session == null) {
            result.put("success", false);
            result.put("message", "Engine 不存在: " + engineId);
            return ResponseEntity.ok(result);
        }
        
        // 1. 先同步更新数据库状态为"被管理员断开"（状态7）
        String sessionId = session.getSessionId();
        connectionLogService.updateDisconnectedWithStats(
            sessionId,
            4007,  // 自定义关闭码：管理员断开
            "管理员断开",
            ConnectionLogService.STATUS_ADMIN_DISCONNECT,  // 状态7
            session.getMessageSent(),
            session.getMessageReceived(),
            session.getHeartbeatCount(),
            session.getErrorCount(),
            "管理员主动断开连接"
        );
        
        log.info("[管理接口] 已更新数据库状态 - SessionID: {}, HostID: {}, Status: 7(管理员断开)", sessionId, engineId);
        
        // 2. 标记为正常关闭，避免afterConnectionClosed中重复更新数据库
        session.markAsNormalClose();
        
        // 3. 发送断开通知（使用ERROR类型，Engine端会识别为致命错误并退出）
        EngineMessage disconnectMsg = EngineMessage.builder()
            .type(MessageType.ERROR)
            .engineId(engineId)
            .payload("message", "您的连接已被管理员断开，请联系管理员了解详情")
            .payload("code", "ADMIN_DISCONNECT")
            .build();
        sessionManager.sendMessage(session, disconnectMsg);
        
        // 4. 等待消息发送完成
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 5. 移除会话（使用自定义状态码4007）
        sessionManager.removeSession(sessionId, 
            new org.springframework.web.socket.CloseStatus(4007, "管理员断开"));
        
        result.put("success", true);
        result.put("message", "Engine 已断开: " + engineId);
        log.warn("[管理接口] 管理员断开 Engine - HostID: {}", engineId);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 向指定 Engine 发送任务
     */
    @PostMapping("/engines/{engineId}/task")
    public ResponseEntity<Map<String, Object>> sendTask(
            @PathVariable String engineId,
            @RequestBody Map<String, Object> taskData) {
        
        EngineSession session = sessionManager.getSessionByEngineId(engineId);
        
        Map<String, Object> result = new HashMap<>();
        if (session == null) {
            result.put("success", false);
            result.put("message", "Engine 不存在: " + engineId);
            return ResponseEntity.ok(result);
        }
        
        // 构建任务消息（移除TASK_REQUEST，改为直接使用PLAYWRIGHT_TEST或其他具体类型）
        // 注意：此处应该根据taskData中的实际type来决定消息类型
        String messageType = (String) taskData.getOrDefault("type", "PLAYWRIGHT_TEST");
        String taskId = UUID.randomUUID().toString().replace("-", "");
        EngineMessage taskMsg = EngineMessage.builder()
            .type(messageType)
            .engineId(engineId)
            .payload("taskId", taskId)
            .payload(taskData)
            .build();
        
        boolean sent = sessionManager.sendMessage(session, taskMsg);
        
        result.put("success", sent);
        result.put("taskId", taskId);
        result.put("message", sent ? "任务已发送" : "任务发送失败");
        
        log.info("[管理接口] 发送任务 - EngineID: {}, TaskID: {}, 结果: {}", 
            engineId, taskId, sent);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 广播消息给所有 Engine
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody Map<String, Object> messageData) {
        String type = (String) messageData.getOrDefault("type", "TASK_REQUEST");
        
        EngineMessage message = EngineMessage.builder()
            .type(type)
            .payload(messageData)
            .build();
        
        int successCount = sessionManager.broadcast(message);
        int totalCount = sessionManager.getRegisteredSessions().size();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("sentCount", successCount);
        result.put("totalCount", totalCount);
        
        log.info("[管理接口] 广播消息 - 类型: {}, 成功: {}/{}", type, successCount, totalCount);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 获取 WebSocket 配置信息
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", properties.isEnabled());
        config.put("enginePath", properties.getEnginePath());
        config.put("clientPath", properties.getClientPath());
        config.put("maxConnections", properties.getMaxConnections());
        config.put("maxMessageSize", properties.getMaxMessageSize());
        config.put("heartbeatInterval", properties.getHeartbeatInterval());
        config.put("heartbeatTimeout", properties.getHeartbeatTimeout());
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("config", config);
        return ResponseEntity.ok(result);
    }

    /**
     * 将 EngineSession 转换为 Map
     */
    private Map<String, Object> sessionToMap(EngineSession session) {
        Map<String, Object> map = new HashMap<>();
        
        // 基本信息
        map.put("engineId", session.getEngineId());
        map.put("sessionId", session.getSessionId());
        map.put("version", session.getVersion());
        map.put("deviceId", session.getDeviceId());
        map.put("status", session.getStatus().name());
        
        // 时间信息
        map.put("connectedAt", formatInstant(session.getConnectedAt()));
        map.put("lastActiveAt", formatInstant(session.getLastActiveAt()));
        map.put("lastHeartbeatAt", formatInstant(session.getLastHeartbeatAt()));
        map.put("durationSeconds", session.getDurationSeconds());
        map.put("idleSeconds", session.getIdleSeconds());
        
        // 能力列表（完整信息）
        map.put("capabilities", session.getCapabilities());
        
        // 设备信息（静态硬件信息）
        map.put("deviceInfo", session.getDeviceInfo());
        
        // 性能数据（实时动态数据，每5分钟通过心跳更新）
        map.put("performanceData", session.getPerformanceData());
        
        // 统计信息
        Map<String, Object> stats = new HashMap<>();
        stats.put("messageSent", session.getMessageSent());
        stats.put("messageReceived", session.getMessageReceived());
        stats.put("heartbeatCount", session.getHeartbeatCount());
        stats.put("errorCount", session.getErrorCount());
        stats.put("lastError", session.getLastError());
        map.put("stats", stats);
        
        return map;
    }

    private String formatInstant(Instant instant) {
        return instant != null ? FORMATTER.format(instant) : null;
    }
}
