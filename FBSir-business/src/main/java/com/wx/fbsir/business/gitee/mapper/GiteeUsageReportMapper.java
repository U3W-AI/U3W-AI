package com.wx.fbsir.business.gitee.mapper;

import com.wx.fbsir.business.gitee.domain.GiteeUsageReport;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Gitee使用统计Mapper接口
 *
 * @author FBSir
 * @date 2026-01-03
 */
public interface GiteeUsageReportMapper {
    /**
     * 按统计日期查询报表
     *
     * @param reportDate 统计日期
     * @return 统计报表
     */
    GiteeUsageReport selectByReportDate(@Param("reportDate") Date reportDate);

    /**
     * 查询统计报表列表
     *
     * @param report 查询条件
     * @return 统计报表列表
     */
    List<GiteeUsageReport> selectGiteeUsageReportList(GiteeUsageReport report);

    /**
     * 新增统计报表
     *
     * @param report 统计报表
     * @return 结果
     */
    int insertGiteeUsageReport(GiteeUsageReport report);

    /**
     * 更新统计报表
     *
     * @param report 统计报表
     * @return 结果
     */
    int updateGiteeUsageReport(GiteeUsageReport report);
}
