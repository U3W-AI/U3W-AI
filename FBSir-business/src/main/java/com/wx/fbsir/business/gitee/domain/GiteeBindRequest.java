package com.wx.fbsir.business.gitee.domain;

/**
 * Gitee账号绑定请求对象
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeBindRequest {
    /** 绑定令牌 */
    private String bindToken;
    /** 账号用户名 */
    private String username;
    /** 账号密码 */
    private String password;

    public String getBindToken() {
        return bindToken;
    }

    public void setBindToken(String bindToken) {
        this.bindToken = bindToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
