package com.wx.fbsir.engine.websocket.message;

/**
 * WebSocket 消息类型枚举
 * 
 * 添加新类型：1.添加枚举 2.创建Controller 3.注册处理器 4.更新文档
 * 流式消息：TASK_PROGRESS(多次) + TASK_RESULT(最终)
 * 单次消息：XXX_RESULT(自动生成)
 * 
 * @author wxfbsir
 * @date 2025-12-15
 */
public enum MessageType {

    // ==========================================================================
    // 业务消息（Business Messages）
    // 说明：由Engine处理的业务功能，需在CapabilityRegistry注册
    // ==========================================================================
    
    /** 健康检查 | Controller: HealthCheckController | once() | 请求: {"type":"HEALTH_CHECK","engineId":"engine-001"} */
    HEALTH_CHECK("HEALTH_CHECK", "健康检查"),
    
    // ---------- 演示能力（Demo Capabilities） ----------
    
    /** 百度热搜抓取演示 | Controller: BaiduHotSearchDemoController | stream() | 流式输出完整示例 | 请求: {"type":"BAIDU_HOT_SEARCH_DEMO","engineId":"engine-001","payload":{"clickIndex":0,"needScreenshot":true}} */
    BAIDU_HOT_SEARCH_DEMO("BAIDU_HOT_SEARCH_DEMO", "百度热搜抓取演示"),
    
    /** 简单健康检查演示 | Controller: SimpleHealthCheckDemoController | once() | 单次输出完整示例 | 请求: {"type":"SIMPLE_HEALTH_CHECK_DEMO","engineId":"engine-001","payload":{"includeDetails":true}} */
    SIMPLE_HEALTH_CHECK_DEMO("SIMPLE_HEALTH_CHECK_DEMO", "简单健康检查演示"),
    
    // ==========================================================================
    // 🤖 AIGC专属消息（AI-Generated Content Messages）
    // 说明：所有AI相关的消息类型，与通用消息完全隔离
    // 前缀规则：AI_ 开头的消息由AIGC模块独立处理
    // ==========================================================================
    
    /** 🤖 AI任务日志 | Engine→Admin | 执行状态文本 | 前端显示在任务流程区 */
    AI_TASK_LOG("AI_TASK_LOG", "AI任务日志"),
    
    /** 🤖 AI任务截图 | Engine→Admin | 截图URL | 前端显示在可视化区 */
    AI_TASK_SCREENSHOT("AI_TASK_SCREENSHOT", "AI任务截图"),
    
    /** 🤖 AI任务结果 | Engine→Admin | 最终结果 | 包含answer、shareUrl等 */
    AI_TASK_RESULT("AI_TASK_RESULT", "AI任务结果"),
    
    /** 🤖 AI任务错误 | Engine→Admin | 错误信息 */
    AI_TASK_ERROR("AI_TASK_ERROR", "AI任务错误"),
    
    // ==========================================================================
    // 通用响应消息（Generic Response Messages）
    // 说明：非AI业务的通用任务响应，保留兼容性
    // ==========================================================================
    
    /** 任务日志 | Engine主动发送 | 执行状态文本消息 | 非AI业务使用 */
    TASK_LOG("TASK_LOG", "任务日志"),
    
    /** 任务截图 | Engine主动发送 | 截图URL消息 | 非AI业务使用 */
    TASK_SCREENSHOT("TASK_SCREENSHOT", "任务截图"),
    
    /** 任务进度通知 | Engine主动发送 | 已废弃，请使用 TASK_LOG 或 AI_TASK_LOG */
    @Deprecated
    TASK_PROGRESS("TASK_PROGRESS", "任务进度"),
    
    /** 任务结果 | Engine主动发送 | 非AI业务使用 */
    TASK_RESULT("TASK_RESULT", "任务结果"),
    
    // ==========================================================================
    // 系统消息（System Messages）
    // 说明：框架内部使用，由EngineWebSocketHandler处理
    // ==========================================================================
    
    /**
     * 心跳请求（Engine → Admin）
     */
    HEARTBEAT_PING("HEARTBEAT_PING", "心跳请求"),
    
    /**
     * 心跳响应（Admin → Engine）
     */
    HEARTBEAT_PONG("HEARTBEAT_PONG", "心跳响应"),
    
    /**
     * Engine注册请求（Engine → Admin）
     */
    ENGINE_REGISTER("ENGINE_REGISTER", "Engine注册"),
    
    /**
     * Engine注册确认（Admin → Engine）
     */
    ENGINE_REGISTER_ACK("ENGINE_REGISTER_ACK", "Engine注册确认"),
    
    /**
     * Engine注销（Engine → Admin）
     */
    ENGINE_UNREGISTER("ENGINE_UNREGISTER", "Engine注销"),
    
    /**
     * 管理员断开连接（Admin → Engine）
     */
    ADMIN_DISCONNECT("ADMIN_DISCONNECT", "管理员断开"),
    
    /**
     * 错误消息（双向）
     */
    ERROR("ERROR", "错误消息"),
    
    /**
     * 未知类型
     */
    UNKNOWN("UNKNOWN", "未知类型");

    private final String code;
    private final String description;

    MessageType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 获取消息类型
     *
     * @param code 消息类型代码
     * @return 消息类型枚举，未找到返回 UNKNOWN
     */
    public static MessageType fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN;
        }
        for (MessageType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return UNKNOWN;
    }
    
    // ==========================================================================
    // 🤖 AIGC辅助方法（用于消息类型判断和路由）
    // ==========================================================================
    
    /**
     * 判断是否为AIGC相关消息类型
     * 
     * @param code 消息类型代码
     * @return true=AI消息，需要由AIGC模块处理
     */
    public static boolean isAiMessage(String code) {
        return code != null && code.startsWith("AI_");
    }
    
    /**
     * 判断当前枚举是否为AI消息类型
     */
    public boolean isAiMessage() {
        return this.code.startsWith("AI_");
    }
    
    /**
     * 判断是否为AI任务响应消息（LOG/SCREENSHOT/RESULT/ERROR）
     */
    public static boolean isAiTaskResponse(String code) {
        return code != null && (
            code.equals("AI_TASK_LOG") || 
            code.equals("AI_TASK_SCREENSHOT") || 
            code.equals("AI_TASK_RESULT") ||
            code.equals("AI_TASK_ERROR")
        );
    }
    
    /**
     * 判断是否为AI最终结果消息
     */
    public static boolean isAiResultMessage(String code) {
        return "AI_TASK_RESULT".equals(code) || "AI_TASK_ERROR".equals(code);
    }
}
