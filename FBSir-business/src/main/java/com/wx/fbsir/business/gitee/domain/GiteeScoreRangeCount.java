package com.wx.fbsir.business.gitee.domain;

import java.io.Serializable;

/**
 * Gitee评分区间统计对象
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeScoreRangeCount implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 评分区间 */
    private String scoreRange;
    /** 用户数量 */
    private Integer userCount;

    public String getScoreRange() {
        return scoreRange;
    }

    public void setScoreRange(String scoreRange) {
        this.scoreRange = scoreRange;
    }

    public Integer getUserCount() {
        return userCount;
    }

    public void setUserCount(Integer userCount) {
        this.userCount = userCount;
    }
}
