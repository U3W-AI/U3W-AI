package com.wx.fbsir.business.gitee.domain;

import java.io.Serializable;

/**
 * Gitee授权会话用户对象
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeSessionUser implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 显示名称 */
    private String name;
    /** 授权码 */
    private String authCode;
    /** 访问令牌 */
    private String accessToken;
    /** 令牌类型 */
    private String tokenType;
    /** 刷新令牌 */
    private String refreshToken;
    /** 过期时间（秒） */
    private Long expiresIn;
    /** 授权范围 */
    private String scope;
    /** 创建时间戳 */
    private Long createdAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAuthCode() {
        return authCode;
    }

    public void setAuthCode(String authCode) {
        this.authCode = authCode;
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

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
