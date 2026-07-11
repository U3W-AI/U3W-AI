package com.wx.fbsir.business.fbs.dto.self;

import java.io.Serializable;

/**
 * 授权码激活请求DTO
 *
 * @author FBSir
 * @date 2026-04-10
 */
public class AuthCodeActivateRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 授权码（由授权码内部含 packId，无需传 packCode） */
    private String authCode;

    // ========== getter / setter ==========

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
}
