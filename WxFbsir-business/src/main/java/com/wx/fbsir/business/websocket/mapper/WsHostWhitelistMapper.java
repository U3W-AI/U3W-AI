package com.wx.fbsir.business.websocket.mapper;

import com.wx.fbsir.business.websocket.domain.WsHostWhitelist;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * WebSocket主机白名单Mapper接口
 *
 * @author wxfbsir
 * @date 2025-12-15
 */
@Mapper
public interface WsHostWhitelistMapper {

    /**
     * 根据ID查询白名单
     *
     * @param id 主键ID
     * @return 白名单记录
     */
    WsHostWhitelist selectById(@Param("id") Long id);

    /**
     * 根据主机ID查询白名单
     *
     * @param hostId 主机ID
     * @return 白名单记录
     */
    WsHostWhitelist selectByHostId(@Param("hostId") String hostId);

    /**
     * 查询白名单列表
     *
     * @param whitelist 查询条件
     * @return 白名单列表
     */
    List<WsHostWhitelist> selectList(WsHostWhitelist whitelist);

    /**
     * 新增白名单
     *
     * @param whitelist 白名单信息
     * @return 影响行数
     */
    int insert(WsHostWhitelist whitelist);

    /**
     * 更新白名单
     *
     * @param whitelist 白名单信息
     * @return 影响行数
     */
    int update(WsHostWhitelist whitelist);

    /**
     * 删除白名单（逻辑删除）
     *
     * @param id 主键
     * @return 影响行数
     */
    int deleteById(@Param("id") Long id);

    /**
     * 批量删除白名单（逻辑删除）
     *
     * @param ids 主键数组
     * @return 影响行数
     */
    int deleteByIds(@Param("ids") Long[] ids);

    /**
     * 根据主机ID更新状态
     *
     * @param hostId 主机ID
     * @param status 状态
     * @return 影响行数
     */
    int updateStatus(@Param("hostId") String hostId, @Param("status") Integer status);

    /**
     * 查询所有启用的OpenClaw主机
     *
     * @return OpenClaw主机列表
     */
    List<WsHostWhitelist> selectOpenclawHosts();
}
