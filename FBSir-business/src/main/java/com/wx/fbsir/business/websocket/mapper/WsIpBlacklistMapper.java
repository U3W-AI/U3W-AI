package com.wx.fbsir.business.websocket.mapper;

import com.wx.fbsir.business.websocket.domain.WsIpBlacklist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * WebSocket IP黑名单Mapper接口
 *
 * @author FBSir
 * @date 2025-12-15
 */
@Mapper
public interface WsIpBlacklistMapper {

    /**
     * 根据ID查询黑名单
     *
     * @param id 主键ID
     * @return 黑名单记录
     */
    WsIpBlacklist selectById(@Param("id") Long id);

    /**
     * 根据IP地址查询黑名单
     *
     * @param ipAddress IP地址
     * @return 黑名单记录
     */
    WsIpBlacklist selectByIpAddress(@Param("ipAddress") String ipAddress);

    /**
     * 查询有效的黑名单记录
     *
     * @param ipAddress IP地址
     * @return 黑名单记录
     */
    WsIpBlacklist selectActiveByIpAddress(@Param("ipAddress") String ipAddress);

    /**
     * 查询黑名单列表
     *
     * @param blacklist 查询条件
     * @return 黑名单列表
     */
    List<WsIpBlacklist> selectList(WsIpBlacklist blacklist);

    /**
     * 新增黑名单
     *
     * @param blacklist 黑名单信息
     * @return 影响行数
     */
    int insert(WsIpBlacklist blacklist);

    /**
     * 更新黑名单
     *
     * @param blacklist 黑名单信息
     * @return 影响行数
     */
    int update(WsIpBlacklist blacklist);

    /**
     * 删除黑名单（逻辑删除）
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 更新命中次数
     *
     * @param id 主键
     * @return 影响行数
     */
    int updateHitCount(@Param("id") Long id);

    /**
     * 根据IP地址解除封禁
     *
     * @param ipAddress IP地址
     * @return 影响行数
     */
    int unblockByIpAddress(@Param("ipAddress") String ipAddress);
}
