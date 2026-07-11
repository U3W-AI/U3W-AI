package com.wx.fbsir.engine.websocket.client;

/**
 * WebSocket常量定义（企业级架构优化：消除魔法数字）
 * 
 * <p>本类集中管理WebSocket相关的所有魔法数字，遵循以下设计原则：
 * <ul>
 *   <li>可维护性：统一修改配置无需搜索代码</li>
 *   <li>可读性：常量名清晰表达业务含义</li>
 *   <li>可测试性：便于单元测试调整参数</li>
 * </ul>
 * 
 * @author FBSir - Senior Architect
 * @date 2025-12-22
 */
public final class WebSocketConstants {

    private WebSocketConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 消息队列配置 ====================
    
    /**
     * 待发送消息队列最大容量
     * <p>设计考量：
     * <ul>
     *   <li>过小：高并发时频繁丢消息，用户体验差</li>
     *   <li>过大：重连期间消息堆积，内存溢出风险</li>
     *   <li>100条：平衡点，约10KB内存（每条消息~100字节）</li>
     * </ul>
     */
    public static final int MAX_PENDING_MESSAGES = 100;
    
    // ==================== 超时配置 ====================
    
    /**
     * 获取资源超时时间（毫秒）
     * <p>应用场景：
     * <ul>
     *   <li>Semaphore获取槽位超时</li>
     *   <li>避免无限等待导致线程饥饿</li>
     * </ul>
     */
    public static final long ACQUIRE_TIMEOUT_MS = 5000;
    
    /**
     * 重连总超时时间（毫秒）
     * <p>设计原理：
     * <ul>
     *   <li>超过5分钟未连接成功，视为致命错误</li>
     *   <li>避免无限重连消耗资源</li>
     *   <li>配合{@link #RECONNECT_MAX_RETRIES}限制重试次数</li>
     * </ul>
     */
    public static final long RECONNECT_TIMEOUT_MS = 5 * 60 * 1000;
    
    /**
     * 消息发送等待时间（毫秒）
     * <p>使用场景：
     * <ul>
     *   <li>发送注销消息后等待网络缓冲区刷新</li>
     *   <li>优雅关闭连接，确保消息送达</li>
     * </ul>
     * <p>⚠️ 注意：阻塞式等待，未来可优化为异步
     */
    public static final long MESSAGE_SEND_WAIT_MS = 500;
    
    // ==================== 心跳配置 ====================
    
    /**
     * 心跳发送间隔（秒）
     * <p>设计考量：
     * <ul>
     *   <li>过短：网络带宽浪费，服务器压力大</li>
     *   <li>过长：连接断开检测延迟高</li>
     *   <li>30秒：行业标准，平衡性能与实时性</li>
     * </ul>
     */
    public static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    
    /**
     * 心跳响应超时时间（秒）
     * <p>超时判定逻辑：
     * <ul>
     *   <li>距离上次收到消息超过 (INTERVAL + TIMEOUT) 秒</li>
     *   <li>标记连接异常，主线程负责关闭</li>
     *   <li>避免在定时任务中调用close()导致死锁</li>
     * </ul>
     */
    public static final int HEARTBEAT_TIMEOUT_SECONDS = 10;
    
    // ==================== 重连策略配置 ====================
    
    /**
     * 最大重连次数
     * <p>设计原理：
     * <ul>
     *   <li>12次 × 5秒 = 60秒总重连时间</li>
     *   <li>配合{@link #RECONNECT_TIMEOUT_MS}双重保护</li>
     *   <li>避免网络抖动时频繁重连</li>
     * </ul>
     */
    public static final int RECONNECT_MAX_RETRIES = 12;
    
    /**
     * 重连初始延迟（秒）
     * <p>首次重连等待时间，避免立即重连冲击服务器
     */
    public static final int RECONNECT_INITIAL_DELAY_SECONDS = 30;
    
    /**
     * 重连最大延迟（秒）
     * <p>指数退避上限，防止等待时间过长
     */
    public static final int RECONNECT_MAX_DELAY_SECONDS = 30;
    
    /**
     * 重连退避倍数
     * <p>当前配置：1.0（不使用指数退避）
     * <ul>
     *   <li>1.0：固定间隔重连</li>
     *   <li>1.5：每次延迟增加50%</li>
     *   <li>2.0：标准指数退避</li>
     * </ul>
     */
    public static final double RECONNECT_BACKOFF_MULTIPLIER = 1.0;
    
    // ==================== 标识符常量 ====================
    
    /**
     * 待注册Engine的ID前缀
     * <p>用于临时标识未完成注册的Engine连接
     * <p>格式："pending-" + sessionId
     */
    public static final String PENDING_ENGINE_ID_PREFIX = "pending-";
}
