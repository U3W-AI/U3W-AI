package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Persistent routing and credential references for one WeCom smart bot. */
@Data
public class WecomBotBinding {
    private Long id;
    private String callbackKey;
    private String aibotId;
    private Long enterpriseId;
    private String mode;
    private String tokenSecretRef;
    private String aesKeySecretRef;
    private Integer credentialVersion;
    private Integer status;
    private String createdBy;
    private Date createTime;
    private String updatedBy;
    private Date updateTime;
    private String delFlag;
}
