package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 授权码Mapper接口
 *
 * @author FBSir
 * @date 2026-04-08
 */
@Mapper
public interface FbsAuthCodeMapper {

    /**
     * 根据ID查询授权码（含悲观锁，用于激活流程）
     *
     * @param id 主键
     * @return 授权码
     */
    FbsAuthCode selectById(@Param("id") Long id);

    /**
     * 根据授权码字符串查询（用于校验和激活，带 FOR UPDATE 行锁防并发）
     *
     * @param authCode 授权码字符串
     * @return 授权码记录
     */
    FbsAuthCode selectByAuthCodeForUpdate(@Param("authCode") String authCode);

    /**
     * 根据授权码字符串查询（只读，用于校验）
     *
     * @param authCode 授权码字符串
     * @return 授权码记录
     */
    FbsAuthCode selectByAuthCode(@Param("authCode") String authCode);

    /**
     * 列表查询（支持按targetId/available/status筛选，Mapper不带Page参数）
     *
     * @param filter 过滤条件
     * @return 授权码列表
     */
    List<FbsAuthCode> selectAuthCodeList(@Param("filter") FbsAuthCode filter);

    /**
     * 更新available字段（管理层面开关，用于disable/enable操作）
     *
     * @param id        授权码ID
     * @param available 0=禁用, 1=启用
     * @return 影响行数
     */
    int updateAvailable(@Param("id") Long id, @Param("available") Integer available);

    /**
     * 更新status字段（用于revoke操作，将status改为4）
     *
     * @param id     授权码ID
     * @param status 新状态（4=已撤销）
     * @return 影响行数
     */
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 新增授权码
     *
     * @param fbsAuthCode 授权码
     * @return 影响行数
     */
    int insertAuthCode(FbsAuthCode fbsAuthCode);

    /**
     * 激活时更新：activated_count + 1，按需更新 status
     *
     * @param id             授权码ID
     * @param newStatus      新状态（1=已激活, 2=已用尽）
     * @param activatedCount 更新后的激活次数（乐观锁校验用）
     * @return 影响行数
     */
    int updateActivated(@Param("id") Long id,
                        @Param("newStatus") Integer newStatus,
                        @Param("activatedCount") Integer activatedCount);
}
