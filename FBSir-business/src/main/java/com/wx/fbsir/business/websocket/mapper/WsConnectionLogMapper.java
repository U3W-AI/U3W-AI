package com.wx.fbsir.business.websocket.mapper;

import com.wx.fbsir.business.websocket.domain.WsConnectionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * WebSocket连接记录Mapper接口
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Mapper
public interface WsConnectionLogMapper {

    /**
     * 根据会话ID查询连接记录
     *
     * @param sessionId 会话ID
     * @return 连接记录
     */
    WsConnectionLog selectBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询连接记录列表
     *
     * @param connectionLog 查询条件
     * @return 连接记录列表
     */
    List<WsConnectionLog> selectList(WsConnectionLog connectionLog);

    /**
     * 新增连接记录
     *
     * @param connectionLog 连接记录
     * @return 影响行数
     */
    int insert(WsConnectionLog connectionLog);

    /**
     * 更新连接记录
     *
     * @param connectionLog 连接记录
     * @return 影响行数
     */
    int update(WsConnectionLog connectionLog);

    /**
     * 根据会话ID更新注册信息
     *
     * @param sessionId     会话ID
     * @param hostId        主机ID
     * @param deviceId      设备ID
     * @param engineVersion 引擎版本
     * @param hostname      主机名
     * @param osName        操作系统名称
     * @param osVersion     操作系统版本
     * @param javaVersion   Java版本
     * @param macAddress    MAC地址
     * @param status        状态
     * @return 影响行数
     */
    int updateRegistered(@Param("sessionId") String sessionId,
                         @Param("hostId") String hostId,
                         @Param("deviceId") String deviceId,
                         @Param("engineVersion") String engineVersion,
                         @Param("hostname") String hostname,
                         @Param("osName") String osName,
                         @Param("osVersion") String osVersion,
                         @Param("javaVersion") String javaVersion,
                         @Param("macAddress") String macAddress,
                         @Param("status") Integer status);

    /**
     * 根据会话ID更新断开信息
     *
     * @param sessionId       会话ID
     * @param status          状态
     * @param closeCode       关闭状态码
     * @param closeReason     关闭原因
     * @param messageSent     发送消息数
     * @param messageReceived 接收消息数
     * @param heartbeatCount  心跳次数
     * @param errorCount      错误次数
     * @param lastError       最后错误信息
     * @return 影响行数
     */
    int updateDisconnected(@Param("sessionId") String sessionId,
                           @Param("status") Integer status,
                           @Param("closeCode") Integer closeCode,
                           @Param("closeReason") String closeReason,
                           @Param("messageSent") Long messageSent,
                           @Param("messageReceived") Long messageReceived,
                           @Param("heartbeatCount") Integer heartbeatCount,
                           @Param("errorCount") Integer errorCount,
                           @Param("lastError") String lastError);

    /**
     * 更新连接记录的IP地址
     *
     * @param sessionId 会话ID
     * @param observedIp 服务端观察到的连接 IP
     * @return 影响行数
     */
    int updateConnectionIp(@Param("sessionId") String sessionId,
                           @Param("observedIp") String observedIp);

    /**
     * 根据会话ID更新拒绝信息
     *
     * @param sessionId    会话ID
     * @param hostId       主机ID
     * @param status       状态
     * @param rejectReason 拒绝原因
     * @param errorCode    错误码（E1001-E9999）
     * @return 影响行数
     */
    int updateRejected(@Param("sessionId") String sessionId,
                       @Param("hostId") String hostId,
                       @Param("status") Integer status,
                       @Param("rejectReason") String rejectReason,
                       @Param("errorCode") String errorCode);

    /**
     * 查询连接统计信息
     *
     * @param startDate 开始日期
     * @param endDate   结束日期
     * @return 统计信息列表
     */
    List<Map<String, Object>> selectConnectionStats(@Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    /**
     * 查询主机连接历史
     *
     * @param hostId 主机ID
     * @param limit  限制数量
     * @return 连接历史列表
     */
    List<WsConnectionLog> selectByHostId(@Param("hostId") String hostId,
                                          @Param("limit") Integer limit);
}
