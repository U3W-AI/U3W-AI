package com.wx.fbsir.business.gitee.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Gitee评测报告对象 gitee_analysis_report
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeAnalysisReport implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 报告ID */
    private Long reportId;
    /** 用户ID */
    private Long userId;
    /** 资料维度评分 */
    private Integer profileScore;
    /** 资料维度等级 */
    private String profileLevel;
    /** 社区维度评分 */
    private Integer communityScore;
    /** 社区维度等级 */
    private String communityLevel;
    /** 技术维度评分 */
    private Integer techScore;
    /** 技术维度等级 */
    private String techLevel;
    /** 总评分 */
    private Integer totalScore;
    /** 总等级 */
    private String totalLevel;
    /** 报告时间 */
    private Date reportTime;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getProfileScore() {
        return profileScore;
    }

    public void setProfileScore(Integer profileScore) {
        this.profileScore = profileScore;
    }

    public String getProfileLevel() {
        return profileLevel;
    }

    public void setProfileLevel(String profileLevel) {
        this.profileLevel = profileLevel;
    }

    public Integer getCommunityScore() {
        return communityScore;
    }

    public void setCommunityScore(Integer communityScore) {
        this.communityScore = communityScore;
    }

    public String getCommunityLevel() {
        return communityLevel;
    }

    public void setCommunityLevel(String communityLevel) {
        this.communityLevel = communityLevel;
    }

    public Integer getTechScore() {
        return techScore;
    }

    public void setTechScore(Integer techScore) {
        this.techScore = techScore;
    }

    public String getTechLevel() {
        return techLevel;
    }

    public void setTechLevel(String techLevel) {
        this.techLevel = techLevel;
    }

    public Integer getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Integer totalScore) {
        this.totalScore = totalScore;
    }

    public String getTotalLevel() {
        return totalLevel;
    }

    public void setTotalLevel(String totalLevel) {
        this.totalLevel = totalLevel;
    }

    public Date getReportTime() {
        return reportTime;
    }

    public void setReportTime(Date reportTime) {
        this.reportTime = reportTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("reportId", getReportId())
            .append("userId", getUserId())
            .append("profileScore", getProfileScore())
            .append("profileLevel", getProfileLevel())
            .append("communityScore", getCommunityScore())
            .append("communityLevel", getCommunityLevel())
            .append("techScore", getTechScore())
            .append("techLevel", getTechLevel())
            .append("totalScore", getTotalScore())
            .append("totalLevel", getTotalLevel())
            .append("reportTime", getReportTime())
            .toString();
    }
}
