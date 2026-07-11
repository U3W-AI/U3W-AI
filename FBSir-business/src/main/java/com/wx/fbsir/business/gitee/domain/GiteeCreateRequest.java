package com.wx.fbsir.business.gitee.domain;

/**
 * Gitee账号创建请求对象
 *
 * @author FBSir
 * @date 2026-01-03
 */
public class GiteeCreateRequest {
    /** 绑定令牌 */
    private String bindToken;

    public String getBindToken() {
        return bindToken;
    }

    public void setBindToken(String bindToken) {
        this.bindToken = bindToken;
    }
}
