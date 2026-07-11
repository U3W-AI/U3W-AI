package com.wx.fbsir.business.gitee.domain;

import java.util.List;

/**
 * Gitee用户摘要查询请求
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeUserSummaryRequest {
    /** 用户ID列表 */
    private List<Long> userIds;

    public List<Long> getUserIds() {
        return userIds;
    }

    public void setUserIds(List<Long> userIds) {
        this.userIds = userIds;
    }
}
