package com.wx.fbsir.business.gitee.mapper;

import com.wx.fbsir.business.gitee.domain.GiteeAnalysisReport;
import com.wx.fbsir.business.gitee.domain.GiteeScoreRangeCount;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Gitee评测报告Mapper接口
 *
 * @author FBSir
 * @date 2026-01-03
 */
public interface GiteeAnalysisReportMapper {
    /**
     * 新增评测报告
     *
     * @param report 评测报告
     * @return 结果
     */
    int insertGiteeAnalysisReport(GiteeAnalysisReport report);

    /**
     * 查询用户最新评测报告
     *
     * @param userIds 用户ID列表
     * @return 评测报告列表
     */
    List<GiteeAnalysisReport> selectLatestByUserIds(@Param("userIds") List<Long> userIds);

    /**
     * 统计指定时间范围内的评测次数
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 次数
     */
    int countByRange(@Param("startTime") java.util.Date startTime, @Param("endTime") java.util.Date endTime);

    /**
     * 统计指定时间范围内评测用户数
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 用户数
     */
    int countDistinctUserByRange(@Param("startTime") java.util.Date startTime, @Param("endTime") java.util.Date endTime);

    /**
     * 统计评测分数分布
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分布统计
     */
    List<GiteeScoreRangeCount> selectScoreDistributionByRange(@Param("startTime") java.util.Date startTime, @Param("endTime") java.util.Date endTime);
}
