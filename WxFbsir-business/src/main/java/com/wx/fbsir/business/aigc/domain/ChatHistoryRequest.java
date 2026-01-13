package com.wx.fbsir.business.aigc.domain;

import com.wx.fbsir.common.core.domain.BaseEntity;

/**
 * 聊天历史查询请求对象
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
public class ChatHistoryRequest extends BaseEntity {
    
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private String userId;

    /** 关键词搜索 */
    private String keyword;

    /** AI名称筛选 */
    private String aiName;

    /** 是否获取全部记录（0:最新1条, 1:全部记录） */
    private Integer isAll = 1;

    public ChatHistoryRequest() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getAiName() {
        return aiName;
    }

    public void setAiName(String aiName) {
        this.aiName = aiName;
    }

    public Integer getIsAll() {
        return isAll;
    }

    public void setIsAll(Integer isAll) {
        this.isAll = isAll;
    }

    @Override
    public String toString() {
        return "ChatHistoryRequest{" +
                "userId='" + userId + '\'' +
                ", keyword='" + keyword + '\'' +
                ", aiName='" + aiName + '\'' +
                ", isAll=" + isAll +
                '}';
    }
}
