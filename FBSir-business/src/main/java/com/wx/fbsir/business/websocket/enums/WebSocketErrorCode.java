package com.wx.fbsir.business.websocket.enums;

/**
 * WebSocket错误码枚举
 * 
 * 设计原则：
 * 1. 每种错误场景对应唯一错误码
 * 2. 错误码格式：E + 4位数字（E1000-E9999）
 * 3. 分类明确：认证(1xxx)、授权(2xxx)、连接(3xxx)、系统(4xxx)、通用(9xxx)
 * 4. 包含是否可重试标志和友好提示
 * 
 * @author FBSir
 * @date 2025-12-24
 */
public enum WebSocketErrorCode {
    
    // ========== 认证错误 (E1xxx) - 不可重试，立即终止 ==========
    
    /**
     * 主机ID为空
     * 场景：客户端未配置主机ID或配置为空
     */
    EMPTY_HOST_ID(
        "E1001", 
        "主机ID不能为空", 
        "请在配置文件中设置有效的主机ID（host-id），联系管理员申请授权",
        false,
        true
    ),
    
    /**
     * 主机ID未授权
     * 场景：主机ID不在白名单中
     */
    HOST_ID_NOT_AUTHORIZED(
        "E1002", 
        "主机ID未授权", 
        "主机ID [%s] 未在白名单中，请联系管理员添加授权",
        false,
        true
    ),
    
    /**
     * 主机ID已被禁用
     * 场景：主机ID在白名单中但状态为禁用
     */
    HOST_ID_DISABLED(
        "E1003", 
        "主机ID已被禁用", 
        "主机ID [%s] 已被管理员禁用，请联系管理员了解原因",
        false,
        true
    ),
    
    /**
     * 主机ID已过期
     * 场景：主机ID的授权已到期
     */
    HOST_ID_EXPIRED(
        "E1004", 
        "主机ID已过期", 
        "主机ID [%s] 授权已于 %s 过期，请联系管理员续期",
        false,
        true
    ),
    
    // ========== 授权错误 (E2xxx) - 不可重试，立即终止 ==========
    
    /**
     * IP不在白名单中
     * 场景：客户端IP不在该主机ID允许的IP列表中
     */
    HOST_TYPE_MISMATCH(
        "E1005",
        "主机类型不匹配",
        "主机ID [%s] 类型不匹配，请检查白名单中的主机类型配置",
        false,
        true
    ),

    IP_NOT_IN_WHITELIST(
        "E2001", 
        "IP地址未授权", 
        "当前IP地址 [%s] 不在主机ID [%s] 的允许列表中\n" +
        "请检查：\n" +
        "1. 服务端观察到的连接IP是否正确配置在白名单中\n" +
        "2. 如果使用代理，请联系管理员配置代理IP\n" +
        "3. 如需添加当前IP，请联系管理员",
        false,
        true
    ),
    
    /**
     * IP在黑名单中
     * 场景：客户端IP在全局黑名单中
     */
    IP_IN_BLACKLIST(
        "E2002", 
        "IP地址已被封禁", 
        "您的IP地址 [%s] 已被加入黑名单，无法连接\n" +
        "封禁原因：%s\n" +
        "如有疑问，请联系管理员申请解封",
        false,
        true
    ),
    
    // ========== 连接错误 (E3xxx) ==========
    
    /**
     * 重复连接（不可重试）
     * 场景：该主机ID已有活跃连接，不允许重复连接
     */
    DUPLICATE_CONNECTION(
        "E3001", 
        "重复连接被拒绝", 
        "主机ID [%s] 已有活跃连接，系统仅允许单一连接\n" +
        "请检查：\n" +
        "1. 是否有其他相同主机ID的实例正在运行\n" +
        "2. 如果确认没有其他实例，请等待旧连接超时（约2分钟）后重试\n" +
        "3. 或联系管理员强制断开旧连接",
        false,
        true
    ),
    
    /**
     * 连接数超限（可重试，但不建议）
     * 场景：服务器连接数已达上限
     */
    CONNECTION_LIMIT_EXCEEDED(
        "E3002", 
        "服务器连接数已满", 
        "主节点当前连接数已达上限，暂时无法接受新连接\n" +
        "建议：\n" +
        "1. 稍后再试（等待其他连接释放）\n" +
        "2. 如持续出现，联系管理员扩容\n" +
        "3. 确认是否有僵尸连接占用资源",
        true,
        false
    ),
    
    /**
     * 被管理员断开（不可重试）
     * 场景：管理员主动断开连接
     */
    ADMIN_DISCONNECTED(
        "E3003", 
        "被管理员断开", 
        "管理员主动断开了您的连接\n" +
        "主机ID：%s\n" +
        "断开原因：%s\n" +
        "请联系管理员了解详情",
        false,
        true
    ),
    
    /**
     * 连接频率超限（不可重试）
     * 场景：短时间内连接次数过多，触发限流
     */
    CONNECTION_RATE_LIMIT(
        "E3004", 
        "连接请求过于频繁", 
        "检测到异常高频连接尝试，已触发安全防护\n" +
        "IP：%s\n" +
        "请检查：\n" +
        "1. 程序是否异常重启或无限重连\n" +
        "2. 配置是否错误导致反复连接失败\n" +
        "3. 等待 %d 分钟后系统将自动解除限制",
        false,
        true
    ),
    
    // ========== 系统错误 (E4xxx) ==========
    
    /**
     * 主节点重启（可重试5分钟）
     * 场景：主节点服务重启或宕机，连接断开
     */
    SERVER_RESTART(
        "E4001", 
        "主节点连接断开", 
        "检测到主节点重启或网络中断\n" +
        "系统将自动重连，最多尝试5分钟\n" +
        "如果5分钟后仍无法连接，请检查：\n" +
        "1. 主节点服务是否正常启动\n" +
        "2. 网络连接是否正常\n" +
        "3. 防火墙是否允许连接",
        true,
        false
    ),
    
    /**
     * 消息格式错误（通用错误）
     * 场景：发送的消息格式不正确
     */
    INVALID_MESSAGE_FORMAT(
        "E4002", 
        "消息格式错误", 
        "发送的消息格式不符合协议规范\n" +
        "请检查消息JSON格式是否正确",
        false,
        false
    ),
    
    /**
     * 未注册就发送业务消息（通用错误）
     * 场景：连接后未完成注册就发送业务消息
     */
    NOT_REGISTERED(
        "E4003", 
        "会话未注册", 
        "请先完成Engine注册后再发送业务消息",
        false,
        false
    ),
    
    // ========== 通用错误 (E9xxx) ==========
    
    /**
     * 未知错误（可重试，但需要日志分析）
     * 场景：其他未分类的错误
     */
    UNKNOWN_ERROR(
        "E9999", 
        "未知错误", 
        "发生未预期的错误\n" +
        "错误信息：%s\n" +
        "请联系技术支持并提供日志文件",
        false,
        false
    );
    
    /**
     * 错误码
     */
    private final String code;
    
    /**
     * 错误标题（简短）
     */
    private final String title;
    
    /**
     * 错误详情（友好的用户提示）
     */
    private final String message;
    
    /**
     * 是否允许重试
     */
    private final boolean retryable;
    
    /**
     * 是否应该立即终止Engine服务
     */
    private final boolean fatal;
    
    WebSocketErrorCode(String code, String title, String message, boolean retryable, boolean fatal) {
        this.code = code;
        this.title = title;
        this.message = message;
        this.retryable = retryable;
        this.fatal = fatal;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getTitle() {
        return title;
    }
    
    public String getMessage() {
        return message;
    }
    
    /**
     * 获取格式化的错误消息（支持参数替换）
     */
    public String getFormattedMessage(Object... args) {
        try {
            return String.format(message, args);
        } catch (Exception e) {
            return message;
        }
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public boolean isFatal() {
        return fatal;
    }
    
    /**
     * 根据错误码获取枚举
     */
    public static WebSocketErrorCode fromCode(String code) {
        if (code == null || code.isEmpty()) {
            return UNKNOWN_ERROR;
        }
        for (WebSocketErrorCode errorCode : values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return UNKNOWN_ERROR;
    }
    
    /**
     * 是否需要立即终止（致命错误）
     */
    public static boolean shouldTerminate(String code) {
        WebSocketErrorCode errorCode = fromCode(code);
        return errorCode.isFatal();
    }
    
    /**
     * 是否可以重试
     */
    public static boolean canRetry(String code) {
        WebSocketErrorCode errorCode = fromCode(code);
        return errorCode.isRetryable();
    }
}
