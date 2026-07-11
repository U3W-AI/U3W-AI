package com.wx.fbsir.business.gitee.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.common.annotation.Excel;
import com.wx.fbsir.common.core.domain.BaseEntity;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Gitee使用统计对象 gitee_usage_report
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeUsageReport extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 报表ID */
    private Long reportId;

    /** 统计日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "统计日期", dateFormat = "yyyy-MM-dd")
    private Date reportDate;

    /** 当日新增绑定用户数 */
    @Excel(name = "当日新增绑定用户数")
    private Integer newBindCount;

    /** 当日评测总次数 */
    @Excel(name = "当日评测总次数")
    private Integer dailyEvaluationCount;

    /** 当日活跃评测用户数 */
    @Excel(name = "当日活跃评测用户数")
    private Integer dailyActiveUserCount;

    /** 累计绑定用户数 */
    @Excel(name = "累计绑定用户数")
    private Integer totalBindCount;

    /** 评分区间分布（JSON） */
    @Excel(name = "评分区间分布")
    private String scoreDistribution;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Date getReportDate() {
        return reportDate;
    }

    public void setReportDate(Date reportDate) {
        this.reportDate = reportDate;
    }

    public Integer getNewBindCount() {
        return newBindCount;
    }

    public void setNewBindCount(Integer newBindCount) {
        this.newBindCount = newBindCount;
    }

    public Integer getDailyEvaluationCount() {
        return dailyEvaluationCount;
    }

    public void setDailyEvaluationCount(Integer dailyEvaluationCount) {
        this.dailyEvaluationCount = dailyEvaluationCount;
    }

    public Integer getDailyActiveUserCount() {
        return dailyActiveUserCount;
    }

    public void setDailyActiveUserCount(Integer dailyActiveUserCount) {
        this.dailyActiveUserCount = dailyActiveUserCount;
    }

    public Integer getTotalBindCount() {
        return totalBindCount;
    }

    public void setTotalBindCount(Integer totalBindCount) {
        this.totalBindCount = totalBindCount;
    }

    public String getScoreDistribution() {
        return scoreDistribution;
    }

    public void setScoreDistribution(String scoreDistribution) {
        this.scoreDistribution = scoreDistribution;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("reportId", getReportId())
            .append("reportDate", getReportDate())
            .append("newBindCount", getNewBindCount())
            .append("dailyEvaluationCount", getDailyEvaluationCount())
            .append("dailyActiveUserCount", getDailyActiveUserCount())
            .append("totalBindCount", getTotalBindCount())
            .append("scoreDistribution", getScoreDistribution())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}
