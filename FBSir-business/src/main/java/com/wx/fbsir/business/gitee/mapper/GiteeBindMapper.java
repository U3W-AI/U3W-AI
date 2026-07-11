package com.wx.fbsir.business.gitee.mapper;

import com.wx.fbsir.business.gitee.domain.GiteeBind;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Gitee绑定信息Mapper接口
 *
 * @author FBSir
 * @date 2026-01-03
 */
public interface GiteeBindMapper {
    /**
     * 根据Gitee用户ID查询绑定信息
     *
     * @param giteeUserId Gitee用户ID
     * @return 绑定信息
     */
    GiteeBind selectByGiteeUserId(@Param("giteeUserId") String giteeUserId);

    /**
     * 根据系统用户ID查询绑定信息
     *
     * @param userId 用户ID
     * @return 绑定信息
     */
    GiteeBind selectByUserId(@Param("userId") Long userId);

    /**
     * 批量查询用户绑定信息
     *
     * @param userIds 用户ID列表
     * @return 绑定信息列表
     */
    List<GiteeBind> selectByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 统计全部绑定用户数
     *
     * @return 总数
     */
    int countAll();

    /**
     * 按时间范围统计新增绑定数
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 新增绑定数
     */
    int countNewBindByRange(@Param("startTime") java.util.Date startTime, @Param("endTime") java.util.Date endTime);

    /**
     * 新增绑定记录
     *
     * @param bind 绑定信息
     * @return 结果
     */
    int insertGiteeBind(GiteeBind bind);

    /**
     * 更新绑定记录
     *
     * @param bind 绑定信息
     * @return 结果
     */
    int updateGiteeBind(GiteeBind bind);

    /**
     * 按用户ID删除绑定信息
     *
     * @param userId 用户ID
     * @return 结果
     */
    int deleteByUserId(@Param("userId") Long userId);
}
