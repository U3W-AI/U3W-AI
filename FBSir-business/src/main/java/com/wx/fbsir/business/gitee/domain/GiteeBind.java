package com.wx.fbsir.business.gitee.domain;

import java.io.Serializable;
import java.util.Date;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/**
 * Gitee绑定关系对象 gitee_bind
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeBind implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 绑定ID */
    private Long bindId;
    /** 用户ID */
    private Long userId;
    /** Gitee用户ID */
    private String giteeUserId;
    /** Gitee用户名 */
    private String giteeUsername;
    /** Gitee头像 */
    private String giteeAvatar;
    /** 绑定时间 */
    private Date bindTime;

    public Long getBindId() {
        return bindId;
    }

    public void setBindId(Long bindId) {
        this.bindId = bindId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGiteeUserId() {
        return giteeUserId;
    }

    public void setGiteeUserId(String giteeUserId) {
        this.giteeUserId = giteeUserId;
    }

    public String getGiteeUsername() {
        return giteeUsername;
    }

    public void setGiteeUsername(String giteeUsername) {
        this.giteeUsername = giteeUsername;
    }

    public String getGiteeAvatar() {
        return giteeAvatar;
    }

    public void setGiteeAvatar(String giteeAvatar) {
        this.giteeAvatar = giteeAvatar;
    }

    public Date getBindTime() {
        return bindTime;
    }

    public void setBindTime(Date bindTime) {
        this.bindTime = bindTime;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("bindId", getBindId())
            .append("userId", getUserId())
            .append("giteeUserId", getGiteeUserId())
            .append("giteeUsername", getGiteeUsername())
            .append("giteeAvatar", getGiteeAvatar())
            .append("bindTime", getBindTime())
            .toString();
    }
}
