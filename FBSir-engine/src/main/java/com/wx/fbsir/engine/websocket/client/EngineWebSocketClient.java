package com.wx.fbsir.engine.websocket.client;

import com.wx.fbsir.engine.config.EngineProperties;
import com.wx.fbsir.engine.util.DeviceInfoUtil;
import com.wx.fbsir.engine.websocket.message.EngineMessage;
import com.wx.fbsir.engine.websocket.message.MessageType;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Engine WebSocket 客户端
 * 
 * 企业级 WebSocket 客户端实现，解决老项目的以下问题：
 * - 心跳机制不完善
 * - 重连逻辑存在竞态条件
 * - 连接状态管理混乱
 *
 * @author FBSir
 * @date 2025-12-15
 */
public class EngineWebSocketClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(EngineWebSocketClient.class);

    private final EngineProperties properties;
    private final ScheduledExecutorService scheduler;
    private final Consumer<EngineMessage> messageHandler;

    /**
     * 心跳定时任务
     */
    private ScheduledFuture<?> heartbeatTask;

    /**
     * 是否正在重连中（避免竞态条件）
     * <p>使用AtomicBoolean确保多线程环境下的可见性和原子性
     * <p>用于防止多个线程同时触发重连逻辑
     */
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);

    /**
     * 是否主动关闭（区分异常断开和主动关闭）
     * <p>设计原理：
     * <ul>
     *   <li>true: 主动关闭或被拒绝连接，不自动重连</li>
     *   <li>false: 异常断开（网络抖动、服务器重启），自动重连</li>
     * </ul>
     * <p>⚠️ 关键：一旦设置为true，整个客户端生命周期内保持，防止误重连
     */
    private final AtomicBoolean intentionalClose = new AtomicBoolean(false);

    /**
     * 重连次数计数器
     * <p>用途：
     * <ul>
     *   <li>限制重连次数，防止无限重连</li>
     *   <li>配合{@link WebSocketConstants#RECONNECT_MAX_RETRIES}使用</li>
     *   <li>重连成功后自动重置为0</li>
     *   <li>🟡 P2修复：用于计算指数退避延迟</li>
     * </ul>
     */
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    
    /**
     * 指数退避基础延迟（毫秒）
     * <p>🟡 P2修复：重连延迟 = BASE_DELAY * 2^(reconnectCount-1)
     * <p>示例：1s, 2s, 4s, 8s, 16s, 30s(最大)
     */
    private static final long RECONNECT_BASE_DELAY_MS = 1000;
    
    /**
     * 最大重连延迟（毫秒）
     * <p>🟡 P2修复：限制最大延迟为30秒，避免过长等待
     */
    private static final long RECONNECT_MAX_DELAY_MS = 30000;

    /**
     * 最后一次收到消息的时间
     */
    private final AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 最后一次发送心跳的时间
     */
    private final AtomicLong lastHeartbeatTime = new AtomicLong(0);
    
    /**
     * 心跳超时标志（避免在定时任务中调用close()导致死锁）
     */
    private final AtomicBoolean heartbeatTimeout = new AtomicBoolean(false);

    /**
     * 第一次断开的时间戳（用于总重连超时检查）
     * <p>超时逻辑：
     * <ul>
     *   <li>超过{@link WebSocketConstants#RECONNECT_TIMEOUT_MS}未连接成功</li>
     *   <li>视为致命错误，停止重连并退出程序</li>
     *   <li>避免无限重连消耗系统资源</li>
     * </ul>
     */
    private final AtomicLong firstDisconnectTime = new AtomicLong(0);

    /**
     * 能力列表（连接时上报给Admin）
     * <p>volatile保证可见性，但List本身不是线程安全的
     * <p>包含Engine支持的所有能力（如baidu_search、image_processing等）
     * <p>⚠️ 注意：setCapabilities时进行了防御性拷贝
     */
    private volatile java.util.List<java.util.Map<String, Object>> capabilities;

    /**
     * 性能数据缓存（用于心跳消息）
     * <p>每5分钟更新一次，避免频繁调用系统 API
     */
    private volatile java.util.Map<String, Object> cachedPerformanceData = null;
    
    /**
     * 性能数据最后更新时间
     */
    private volatile long lastPerformanceUpdateTime = 0;
    
    /**
     * 性能数据更新间隔（5分钟）
     */
    private static final long PERFORMANCE_UPDATE_INTERVAL = 5 * 60 * 1000;

    /**
     * 构造器
     *
     * @param serverUri      服务器地址
     * @param properties     配置属性
     * @param scheduler      调度器
     * @param messageHandler 消息处理器
     */
    public EngineWebSocketClient(URI serverUri,
                                  EngineProperties properties,
                                  ScheduledExecutorService scheduler,
                                  Consumer<EngineMessage> messageHandler) {
        super(serverUri);
        this.properties = properties;
        this.scheduler = scheduler;
        this.messageHandler = messageHandler;
        
        // 设置连接超时
        this.setConnectionLostTimeout(properties.getConnection().getHeartbeatTimeout());
        
        log.info("[Engine] 初始化完成 - 目标: {}", serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        // 重置状态
        reconnectCount.set(0);
        reconnecting.set(false);
        firstDisconnectTime.set(0);
        lastMessageTime.set(System.currentTimeMillis());
        heartbeatTimeout.set(false);
        
        // 发送注册消息
        sendRegisterMessage();
        
        // 启动心跳检测
        startHeartbeat();
    }

    @Override
    public void onMessage(String message) {
        lastMessageTime.set(System.currentTimeMillis());
        heartbeatTimeout.set(false);
        
        if (message == null || message.isEmpty()) {
            log.warn("[WebSocket] 收到空消息，已忽略");
            return;
        }
        
        log.debug("[WebSocket] 收到消息 - 长度: {} 字符", message.length());
        
        try {
            EngineMessage engineMessage = EngineMessage.fromJson(message);
            if (engineMessage == null) {
                log.warn("[WebSocket] 消息解析失败 - 原始消息: {}", 
                    message.length() > 200 ? message.substring(0, 200) + "..." : message);
                return;
            }
            
            // 处理系统消息（心跳响应、注册确认等）
            if (handleSystemMessage(engineMessage)) {
                return;
            }
            
            // 业务消息交给处理器
            if (messageHandler != null) {
                String requestId = engineMessage.getPayloadValue("requestId");
                log.debug("[Engine] 收到消息: {} - 用户: {}, 请求ID: {}", 
                    engineMessage.getType(), engineMessage.getUserId(), requestId);
                messageHandler.accept(engineMessage);
            }
            
        } catch (Exception e) {
            log.error("[WebSocket] 消息处理异常 - 错误类型: {}, 错误信息: {}", 
                e.getClass().getSimpleName(), e.getMessage());
            log.info("========================================");
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        // 停止心跳
        stopHeartbeat();
        
        // 判断是否曾经成功连接过（用于区分黑名单拒绝和主节点宕机）
        boolean everConnected = lastMessageTime.get() > 0;
        
        // 从reason中解析HTTP状态码（握手失败时reason包含HTTP响应信息）
        int httpStatusCode = parseHttpStatusCode(reason);
        
        // ==================== 明确拒绝场景（立即退出，不重试） ====================
        
        // 1. 服务器主动拒绝（认证/授权失败）
        if (code == 1011 || code == 1008 || code == 1003) {
            intentionalClose.set(true);
            printFatalError("服务器拒绝连接", 
                "服务器主动关闭了连接\n" +
                "1. 检查主机ID是否在白名单中\n" +
                "2. 检查IP是否被封禁\n" +
                "3. 检查主节点服务是否正常\n" +
                "4. 状态码: " + code + ", 原因: " + (reason != null ? reason : "未知"));
            return;
        }
        
        // 2. 重复连接被拒绝（4008）
        if (code == 4008) {
            intentionalClose.set(true);
            printFatalError("重复连接被拒绝", 
                "主机ID已有活跃连接，不允许重复连接\n" +
                "1. 可能有另一个相同主机ID的实例正在运行\n" +
                "2. 请检查是否有重复启动的进程\n" +
                "3. 如果确认没有其他实例，请等待旧连接超时后重试\n" +
                "4. 主机ID: " + properties.getHostId());
            return;
        }
        
        // 3. 管理员断开（4007）- 立即退出，不重试
        if (code == 4007) {
            intentionalClose.set(true);
            printFatalError("被管理员断开", 
                "管理员主动断开了您的连接\n" +
                "1. 请联系管理员了解原因\n" +
                "2. 主机ID: " + properties.getHostId());
            return;
        }
        
        // 4. 服务过载（1013）- 立即退出，不重试
        if (code == 1013) {
            intentionalClose.set(true);
            printFatalError("服务器过载", 
                "主节点连接数已达上限，无法接受新连接\n" +
                "1. 请稍后再试\n" +
                "2. 如果持续出现，请联系管理员扩容\n" +
                "3. 主节点地址: " + properties.getWsUrl());
            return;
        }
        
        // 5. HTTP 403 - 黑名单拒绝（立即退出，不重试）
        if (httpStatusCode == 403) {
            intentionalClose.set(true);
            printFatalError("IP被黑名单拒绝", 
                "您的IP地址已被加入黑名单，无法连接主节点\n" +
                "1. 请联系管理员解除黑名单\n" +
                "2. 您的IP: " + getLocalIpForLog() + "\n" +
                "3. 主节点地址: " + properties.getWsUrl());
            return;
        }
        
        // 6. HTTP 429 - 限流拒绝（立即退出，不重试）
        if (httpStatusCode == 429) {
            intentionalClose.set(true);
            printFatalError("连接被限流拒绝", 
                "连接请求过于频繁，已被限流\n" +
                "1. 请稍后再试\n" +
                "2. 如果持续出现，请联系管理员\n" +
                "3. 您的IP: " + getLocalIpForLog());
            return;
        }
        
        // 7. 握手阶段被拒绝（其他4xx错误）- 从未成功连接过
        if (!everConnected && code == 1002 && httpStatusCode >= 400 && httpStatusCode < 500) {
            intentionalClose.set(true);
            printFatalError("连接被服务器拒绝", 
                "WebSocket握手失败，服务器拒绝连接\n" +
                "1. HTTP状态码: " + httpStatusCode + "\n" +
                "2. 请检查主机ID是否正确配置\n" +
                "3. 请联系管理员检查您的IP: " + getLocalIpForLog() + "\n" +
                "4. 主节点地址: " + properties.getWsUrl());
            return;
        }
        
        // ==================== 可重试场景（主节点宕机/网络问题） ====================
        // 只有曾经成功连接过，才认为是主节点宕机，允许重试
        
        if (!intentionalClose.get() && properties.getReconnect().isEnabled() && everConnected) {
            // 记录第一次断开时间
            if (firstDisconnectTime.get() == 0) {
                firstDisconnectTime.set(System.currentTimeMillis());
                
                // 区分不同的断开原因
                String disconnectReason = "未知原因";
                if (code == 1006) {
                    disconnectReason = "网络连接异常（Broken pipe）";
                } else if (code == 1000) {
                    disconnectReason = "正常关闭";
                } else if (code == 1001) {
                    disconnectReason = "端点离线";
                } else if (code == 1002) {
                    disconnectReason = "协议错误";
                } else if (code == 1009) {
                    disconnectReason = "消息过大";
                } else if (code == 1010) {
                    disconnectReason = "缺少扩展";
                } else if (code == 1011) {
                    disconnectReason = "服务器错误";
                }
                
                log.info("[Engine] 连接断开，开始自动重连... (状态码: {}, 原因: {}, HTTP: {})", 
                    code, disconnectReason, httpStatusCode > 0 ? httpStatusCode : "N/A");
            }
            
            // 检查是否超过5分钟
            long disconnectDuration = System.currentTimeMillis() - firstDisconnectTime.get();
            if (disconnectDuration > 5 * 60 * 1000) {
                intentionalClose.set(true);
                printFatalError("重连超时", 
                    "已尝试重连5分钟，仍无法连接主节点\n" +
                    "1. 检查主节点是否已启动\n" +
                    "2. 检查网络连接是否正常\n" +
                    "3. 检查防火墙设置\n" +
                    "4. 检查主节点地址配置: " + properties.getWsUrl() + "\n" +
                    "5. 最后断开原因: " + reason);
                return;
            }
            
            scheduleReconnect();
            return;
        }
        
        // ==================== 首次连接失败（非黑名单，可能是主节点未启动） ====================
        // 从未成功连接过，但不是明确的拒绝错误（可能是连接超时、网关超时等）
        if (!everConnected && !intentionalClose.get() && properties.getReconnect().isEnabled()) {
            // 记录第一次断开时间
            if (firstDisconnectTime.get() == 0) {
                firstDisconnectTime.set(System.currentTimeMillis());
                log.info("[Engine] 首次连接失败，开始自动重连... (状态码: {}, HTTP: {})", 
                    code, httpStatusCode > 0 ? httpStatusCode : "N/A");
            }
            
            // 首次连接失败也允许重试5分钟（可能是主节点还没启动）
            long disconnectDuration = System.currentTimeMillis() - firstDisconnectTime.get();
            if (disconnectDuration > 5 * 60 * 1000) {
                intentionalClose.set(true);
                printFatalError("连接失败", 
                    "尝试连接5分钟，仍无法连接主节点\n" +
                    "1. 检查主节点是否已启动\n" +
                    "2. 检查网络连接是否正常\n" +
                    "3. 检查主节点地址配置: " + properties.getWsUrl() + "\n" +
                    "4. 状态码: " + code + (httpStatusCode > 0 ? ", HTTP: " + httpStatusCode : ""));
                return;
            }
            
            scheduleReconnect();
        }
    }
    
    /**
     * 从关闭原因中解析HTTP状态码
     * 格式示例: "Invalid status code received: 403 Status line: HTTP/1.1 403 Forbidden"
     */
    private int parseHttpStatusCode(String reason) {
        if (reason == null || reason.isEmpty()) {
            return 0;
        }
        try {
            // 尝试匹配 "status code received: XXX" 格式
            if (reason.contains("status code received:")) {
                String[] parts = reason.split("status code received:");
                if (parts.length > 1) {
                    String codePart = parts[1].trim().split(" ")[0];
                    return Integer.parseInt(codePart);
                }
            }
            // 尝试匹配 "HTTP/1.1 XXX" 格式
            if (reason.contains("HTTP/")) {
                int httpIndex = reason.indexOf("HTTP/");
                String httpPart = reason.substring(httpIndex);
                String[] parts = httpPart.split(" ");
                if (parts.length >= 2) {
                    return Integer.parseInt(parts[1]);
                }
            }
        } catch (Exception e) {
            // 解析失败，返回0
        }
        return 0;
    }
    
    /**
     * 获取本机IP用于日志显示
     */
    private String getLocalIpForLog() {
        try {
            return DeviceInfoUtil.getLocalIp();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void onError(Exception ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        
        // 连接被拒绝或超时时，允许重连而不是立即退出
        // 只有在明确的认证/授权失败时才退出
        log.warn("[Engine] 连接错误: {}", msg);
    }

    /**
     * 发送注册消息（包含完整的设备信息、硬件信息和能力列表）
     * 
     * 说明：
     * - 设备信息：主机名、操作系统、Java版本、网络地址等
     * - 硬件信息：CPU型号、核心数、内存容量、磁盘容量等
     * - 能力列表：Engine支持的功能列表（如Playwright、AI对话等）
     */
    private void sendRegisterMessage() {
        try {
            // 1. 获取设备基础信息（操作系统、Java版本等）
            java.util.Map<String, String> deviceInfo = com.wx.fbsir.engine.util.DeviceInfoUtil.getDeviceInfo();
            String deviceId = com.wx.fbsir.engine.util.DeviceInfoUtil.getDeviceId();
            
            // 2. 获取网络信息（本地IP、公网IP）
            String localIp = com.wx.fbsir.engine.util.NetworkInfoUtil.getLocalIp();
            String publicIp = com.wx.fbsir.engine.util.NetworkInfoUtil.getPublicIp();
            
            // 3. 获取硬件信息（CPU、内存、磁盘）
            java.util.Map<String, Object> hardwareInfo = com.wx.fbsir.engine.util.SystemPerformanceMonitor.getHardwareInfo();
            
            // 4. 构建注册消息
            EngineMessage registerMsg = EngineMessage.builder()
                .type(MessageType.ENGINE_REGISTER)
                .engineId(properties.getHostId())
                .version(properties.getVersion())
                // 设备标识
                .payload("deviceId", deviceId)
                // 能力列表
                .payload("capabilities", capabilities != null ? new java.util.ArrayList<>(capabilities) : new java.util.ArrayList<>())
                // 设备基础信息
                .payload("hostname", deviceInfo.get("hostname"))
                .payload("osName", deviceInfo.get("osName"))
                .payload("osVersion", deviceInfo.get("osVersion"))
                .payload("osArch", deviceInfo.get("osArch"))
                .payload("javaVersion", deviceInfo.get("javaVersion"))
                .payload("javaVendor", deviceInfo.get("javaVendor"))
                .payload("macAddress", deviceInfo.get("macAddress"))
                // 网络信息
                .payload("localIp", localIp)
                .payload("publicIp", publicIp)
                // 硬件信息
                .payload("cpuModel", hardwareInfo.get("cpuModel"))
                .payload("cpuCores", hardwareInfo.get("cpuCores"))
                .payload("cpuLogicalCores", hardwareInfo.get("cpuLogicalCores"))
                .payload("totalMemoryMB", hardwareInfo.get("totalMemoryMB"))
                .payload("totalMemoryGB", hardwareInfo.get("totalMemoryGB"))
                .payload("totalDiskGB", hardwareInfo.get("totalDiskGB"))
                .build();
            
            // 5. 发送消息
            sendMessage(registerMsg);
            
            // 6. 简化日志输出：单行显示关键信息
            log.info("[Engine] 注册成功 → ID:{} | 版本:{} | {}核 | 内存:{}GB | 能力:{}", 
                properties.getHostId(), 
                properties.getVersion(),
                hardwareInfo.get("cpuCores"),
                hardwareInfo.get("totalMemoryGB"),
                capabilities != null ? capabilities.size() : 0);
            
        } catch (Exception e) {
            log.error("[Engine] 发送注册消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理系统消息（心跳响应、注册确认、错误消息等）
     * @return true表示已处理，false表示需要继续传递
     */
    private boolean handleSystemMessage(EngineMessage message) {
        String typeStr = message.getType();
        if (typeStr == null) {
            return false;
        }
        
        if ("HEARTBEAT_PONG".equals(typeStr)) {
            log.debug("[Engine] 收到心跳响应");
            return true;
        }
        
        if ("ENGINE_REGISTER_ACK".equals(typeStr)) {
            log.info("[Engine] 注册成功");
            return true;
        }
        
        // 处理ERROR消息
        if ("ERROR".equals(typeStr)) {
            handleErrorMessage(message);
            return true;
        }
        
        return false;
    }
    
    /**
     * 处理服务器返回的错误消息
     * 根据错误码决定是否终止Engine服务
     */
    private void handleErrorMessage(EngineMessage message) {
        String errorCode = message.getPayloadValue("code");
        String errorMessage = message.getPayloadValue("message");
        
        if (errorCode == null || errorCode.isEmpty()) {
            log.warn("[Engine] 收到错误消息但无错误码: {}", errorMessage);
            return;
        }
        
        log.error("[Engine] 收到服务器错误 - 错误码: {}, 错误信息: {}", errorCode, errorMessage);
        
        // 根据错误码判断是否需要终止
        if (shouldTerminateOnError(errorCode)) {
            // 致命错误，立即终止
            intentionalClose.set(true);
            stopHeartbeat();
            
            // 打印友好的错误提示并退出
            printFatalError("连接被拒绝 (" + errorCode + ")", errorMessage != null ? errorMessage : "未知错误");
        } else {
            // 非致命错误，仅记录日志
            log.warn("[Engine] 非致命错误，继续运行: {} - {}", errorCode, errorMessage);
        }
    }
    
    /**
     * 判断错误码是否需要终止Engine服务
     * 
     * 终止场景：
     * - E1xxx: 认证错误（主机ID相关）
     * - E2xxx: 授权错误（IP相关）
     * - E3001: 重复连接
     * - E3003: 被管理员断开
     * - E3004: 连接频率超限
     * 
     * 不终止场景：
     * - E3002: 连接数超限（可等待重试）
     * - E4001: 主节点重启（自动重连）
     * - E4xxx: 其他系统错误
     */
    private boolean shouldTerminateOnError(String errorCode) {
        if (errorCode == null || errorCode.isEmpty()) {
            return false;
        }
        
        // E1xxx - 认证错误（致命）
        if (errorCode.startsWith("E1")) {
            return true;
        }
        
        // E2xxx - 授权错误（致命）
        if (errorCode.startsWith("E2")) {
            return true;
        }
        
        // 特殊错误码（直接匹配字符串）
        if ("ADMIN_DISCONNECT".equals(errorCode)) {
            return true; // 被管理员断开，立即退出
        }
        
        // E3xxx - 连接错误（部分致命）
        switch (errorCode) {
            case "E3001": // 重复连接
            case "E3003": // 被管理员断开
            case "E3004": // 连接频率超限
                return true;
            case "E3002": // 连接数超限（可等待）
                return false;
        }
        
        // E4xxx - 系统错误（非致命）
        if (errorCode.startsWith("E4")) {
            return false;
        }
        
        // 其他未知错误（非致命）
        return false;
    }

    /**
     * 清理连接状态
     */
    private void cleanupState() {
        reconnecting.set(false);
        reconnectCount.set(0);
        firstDisconnectTime.set(0);
        log.debug("[WebSocket] 连接状态已清理");
    }

    /**
     * 设置能力列表（防御性拷贝）
     */
    public void setCapabilities(java.util.List<java.util.Map<String, Object>> caps) {
        this.capabilities = caps != null ? new java.util.ArrayList<>(caps) : null;
    }

    /**
     * 打印致命错误并退出
     */
    private void printFatalError(String title, String details) {
        System.err.println();
        System.err.println("╔════════════════════════════════════════════════════");
        System.err.println("║  Engine 启动失败");
    System.err.println("╠════════════════════════════════════════════════════");
        System.err.println("║  错误: " + title);
    System.err.println("╠════════════════════════════════════════════════════");
        System.err.println("║  解决方案:");
        for (String line : details.split("\n")) {
            System.err.println("║    " + line);
        }
        System.err.println("╚════════════════════════════════════════════════════════════╝");
        
        System.exit(1);
    }

    /**
     * 获取友好的错误提示
     * @return 提示信息，null表示非致命错误
     */
    private String getFriendlyErrorTip(String errorCode) {
        if (errorCode == null) {
            return null;
        }
        
        return switch (errorCode) {
        case "EMPTY_HOST_ID" -> 
            "主机ID未配置\n" +
            "1. 编辑 application.yml\n" +
            "2. 设置 wxfbsir.engine.host-id 为有效的主机ID\n" +
            "3. 主机ID需向管理员申请";
            
        case "HOST_NOT_IN_WHITELIST" -> 
            "主机ID未授权\n" +
            "1. 检查 application.yml 中的 host-id 是否正确\n" +
            "2. 联系管理员将此主机ID添加到白名单\n" +
            "3. 当前配置的主机ID: " + properties.getHostId();
            
        case "HOST_DISABLED" -> 
            "主机ID已被禁用\n" +
            "1. 联系管理员了解禁用原因\n" +
            "2. 请求管理员重新启用此主机ID\n" +
            "3. 或申请新的主机ID";
            
        case "HOST_EXPIRED" -> 
            "主机ID已过期\n" +
            "1. 联系管理员续期此主机ID\n" +
            "2. 或申请新的主机ID";
            
        case "IP_NOT_ALLOWED" -> 
            "当前IP不在允许列表中\n" +
            "1. 联系管理员将当前IP添加到允许列表\n" +
            "2. 或从允许的IP地址启动Engine";
            
        case "IP_BLOCKED" -> 
            "当前IP已被封禁\n" +
            "1. 联系管理员了解封禁原因\n" +
            "2. 请求解除封禁或更换IP地址";
            
        case "DUPLICATE_CONNECTION" -> 
            "此主机ID已有其他Engine连接\n" +
            "1. 检查是否有其他Engine使用相同的主机ID\n" +
            "2. 停止其他Engine或使用不同的主机ID\n" +
            "3. 一个主机ID只能有一个Engine连接";
            
        case "ADMIN_DISCONNECT" -> 
            "被管理员主动断开\n" +
            "1. 联系管理员了解断开原因\n" +
            "2. 解决问题后重新启动";
            
        default -> null;
        };
    }

    /**
     * 字符串右填充（用于格式化输出）
     */
    private static String padRight(String s, int n) {
        if (s == null) s = "";
        if (s.length() >= n) return s.substring(0, n);
        StringBuilder sb = new StringBuilder(s);  
        while (sb.length() < n) {
        sb.append(' ');
        }
        return sb.toString();
    }

    /**
     * 发送消息（检查心跳超时状态）
     *
     * @param message 消息对象
     */
    public void sendMessage(EngineMessage message) {
        // 心跳超时时，主动关闭连接
        if (heartbeatTimeout.get()) {
            log.warn("[WebSocket] 检测到心跳超时，关闭连接");
            try {
                close();
            } catch (Exception e) {
                log.debug("[WebSocket] 关闭连接异常: {}", e.getMessage());
            }
            return;
        }
    
    if (!isOpen()) {
        log.warn("[WebSocket] 连接未建立，消息发送失败 - 消息类型: {}", message.getType());
        return;
    }
    
    try {
        String json = message.toJson();
        send(json);
        log.debug("[WebSocket] 消息发送成功 - 类型: {}, 长度: {} 字符", 
            message.getType(), json.length());
    } catch (Exception e) {
        log.error("[WebSocket] 消息发送失败 - 错误: {}", e.getMessage());
    }
}

/**
 * 启动心跳检测
 */
private void startHeartbeat() {
    if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
        heartbeatTask.cancel(false);
    }
    
    int interval = properties.getConnection().getHeartbeatInterval();
    
    heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
        try {
            if (!isOpen()) {
                return;
            }
            
            // 检查心跳超时（被动心跳：只检测不关闭）
            long lastMsg = lastMessageTime.get();
            int timeout = properties.getConnection().getHeartbeatTimeout();
            long silenceDuration = System.currentTimeMillis() - lastMsg;
            
            if (silenceDuration > (interval + timeout) * 1000L) {
                if (heartbeatTimeout.compareAndSet(false, true)) {
                    log.warn("[WebSocket] 心跳超时 - 静默时长: {}s, 将由主线程关闭连接", silenceDuration / 1000);
                }
                return;
            }
            
            // 重置超时标志（恢复正常）
            heartbeatTimeout.set(false);
            
            // 构建心跳消息
            EngineMessage.Builder heartbeatBuilder = EngineMessage.builder()
                .type(MessageType.HEARTBEAT_PING)
                .engineId(properties.getHostId());
            
            // 每5分钟在心跳消息中携带实时性能数据
            long now = System.currentTimeMillis();
            if (cachedPerformanceData == null || (now - lastPerformanceUpdateTime) > PERFORMANCE_UPDATE_INTERVAL) {
                // 更新性能数据缓存
                cachedPerformanceData = com.wx.fbsir.engine.util.SystemPerformanceMonitor.getPerformanceData();
                lastPerformanceUpdateTime = now;
                
                // 将性能数据添加到心跳消息的 payload 中
                heartbeatBuilder.payload("performance", cachedPerformanceData);
                
                log.debug("[Engine] 心跳携带性能数据: CPU:{}% | 内存:{}% | 磁盘:{}%",
                    cachedPerformanceData.get("cpuUsagePercent"),
                    cachedPerformanceData.get("memoryUsagePercent"),
                    cachedPerformanceData.get("diskUsagePercent"));
            }
            
            EngineMessage heartbeat = heartbeatBuilder.build();
            sendMessage(heartbeat);
            lastHeartbeatTime.set(System.currentTimeMillis());
            log.debug("[WebSocket] 发送心跳");
            
        } catch (Exception e) {
            log.error("[WebSocket] 心跳发送异常 - 错误: {}", e.getMessage());
        }
    }, interval, interval, TimeUnit.SECONDS);
    
    log.debug("[Engine] 心跳已启动 - 间隔: {}秒", interval);
}

/**
 * 停止心跳检测
 * <p>⚠️ 线程安全：使用cancel(true)中断正在执行的任务，避免任务挂起
 * <p>📌 资源清理：确保ScheduledFuture被正确取消和释放
 */
private void stopHeartbeat() {
    if (heartbeatTask != null) {
        try {
            if (!heartbeatTask.isCancelled()) {
                // 使用cancel(true)中断正在执行的任务
                heartbeatTask.cancel(true);
            }
        } catch (Exception e) {
            log.debug("[WebSocket] 停止心跳异常: {}", e.getMessage());
        } finally {
            heartbeatTask = null;
        }
    }
    log.debug("[WebSocket] 心跳检测已停止");
}

/**
 * 调度重连（指数退避策略）
 */
private void scheduleReconnect() {
    // 避免重复调度重连
    if (!reconnecting.compareAndSet(false, true)) {
        log.debug("[WebSocket] 已在重连中，跳过本次调度");
        return;
    }
    
    int maxRetries = properties.getReconnect().getMaxRetries();
    int currentRetry = reconnectCount.incrementAndGet();
    
    // 检查重试次数
    if (maxRetries > 0 && currentRetry > maxRetries) {
        log.error("[WebSocket] 重连次数已达上限: {}, 停止重连", maxRetries);
        reconnecting.set(false);
        return;
    }
    
    // 计算重连延迟（指数退避）
    int initialDelay = properties.getReconnect().getInitialDelay();
    int maxDelay = properties.getReconnect().getMaxDelay();
    double multiplier = properties.getReconnect().getBackoffMultiplier();
    
    long delay = (long) (initialDelay * Math.pow(multiplier, currentRetry - 1));
    delay = Math.min(delay, maxDelay);
    
    // 优化日志输出：前3次使用DEBUG，3-10次每3次输出一次，10次以上每次输出
    if (currentRetry <= 3) {
        log.debug("[Engine] {}秒后第{}次重连", delay, currentRetry);
    } else if (currentRetry <= 10 && currentRetry % 3 == 0) {
        log.warn("[Engine] 重连中... 已尝试{}次，{}秒后继续", currentRetry, delay);
    } else if (currentRetry > 10) {
        log.warn("[Engine] 持续重连中... 第{}次尝试（{}秒后）| 建议检查网络或主节点状态", currentRetry, delay);
    }
    
    scheduler.schedule(() -> {
        try {
            if (intentionalClose.get()) {
                log.debug("[WebSocket] 检测到主动关闭，取消重连");
                return;
            }
            
            log.debug("[Engine] 重连中...");
            reconnect();
            
        } catch (Exception e) {
            log.debug("[WebSocket] 重连异常 - 错误: {}", e.getMessage());
        } finally {
            reconnecting.set(false);
        }
    }, delay, TimeUnit.SECONDS);
}

/**
 * 优雅关闭连接
 * <p>执行步骤：
 * <ol>
 *   <li>设置intentionalClose标志，阻止自动重连</li>
 *   <li>停止心跳定时任务</li>
 *   <li>发送注销消息通知服务器</li>
 *   <li>等待消息发送完成</li>
 *   <li>关闭WebSocket连接</li>
 *   <li>清理所有状态</li>
 * </ol>
 * <p>⚠️ 注意：使用Thread.sleep阻塞等待消息发送，未来可优化为异步
 */
public void closeGracefully() {
    log.info("[WebSocket] 准备优雅关闭连接");
    intentionalClose.set(true);
    
    // 停止心跳
    stopHeartbeat();
    
    try {
        // 发送注销消息
        if (isOpen()) {
            EngineMessage unregisterMsg = EngineMessage.builder()
                .type(MessageType.ENGINE_UNREGISTER)
                .engineId(properties.getHostId())
                .build();
            
            sendMessage(unregisterMsg);
            
            // 等待消息发送完成（使用常量）
            Thread.sleep(WebSocketConstants.MESSAGE_SEND_WAIT_MS);
        }
        
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("[WebSocket] 优雅关闭被中断");
    } catch (Exception e) {
        log.warn("[WebSocket] 发送注销消息失败: {}", e.getMessage());
    } finally {
        // 确保关闭连接并清理状态
        try {
            close();
        } catch (Exception e) {
            log.debug("[WebSocket] 关闭连接异常: {}", e.getMessage());
        }
        cleanupState();
    }
}

    /**
     * 获取连接状态
     */
    public ConnectionStatus getStatus() {
        return new ConnectionStatus(
            isOpen(),
            reconnecting.get(),
            reconnectCount.get(),
            lastMessageTime.get(),
            lastHeartbeatTime.get()
        );
    }

    /**
     * 连接状态信息
     */
    public record ConnectionStatus(
        boolean connected,
        boolean reconnecting,
        int reconnectCount,
        long lastMessageTime,
        long lastHeartbeatTime
    ) {}
}
