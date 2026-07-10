package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Fail-closed mapping from a bot-scoped external identity to a U3W member. */
@Data
public class WecomBotMemberBinding {
    private Long id;
    private Long botBindingId;
    private Long enterpriseId;
    private Long enterpriseMemberId;
    private Long userId;
    private String externalUserHash;
    private Integer status;
    private String createdBy;
    private Date createTime;
    private String updatedBy;
    private Date updateTime;
    private String delFlag;
}
