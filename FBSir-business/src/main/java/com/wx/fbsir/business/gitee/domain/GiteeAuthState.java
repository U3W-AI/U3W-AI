package com.wx.fbsir.business.gitee.domain;

import java.io.Serializable;

/**
 * Gitee授权状态对象
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeAuthState implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;
    /** 授权完成后跳转地址 */
    private String redirect;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getRedirect() {
        return redirect;
    }

    public void setRedirect(String redirect) {
        this.redirect = redirect;
    }
}
