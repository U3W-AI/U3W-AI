package com.wx.fbsir.business.gitee.domain;

import java.io.Serializable;

/**
 * Gitee授权绑定信息对象
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeBindInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Gitee用户ID */
    private String giteeId;
    /** 登录名 */
    private String login;
    /** 昵称 */
    private String name;
    /** 头像地址 */
    private String avatarUrl;
    /** 邮箱 */
    private String email;
    /** 访问令牌 */
    private String accessToken;
    /** 令牌类型 */
    private String tokenType;
    /** 令牌创建时间 */
    private Long createdAt;

    public String getGiteeId() {
        return giteeId;
    }

    public void setGiteeId(String giteeId) {
        this.giteeId = giteeId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
