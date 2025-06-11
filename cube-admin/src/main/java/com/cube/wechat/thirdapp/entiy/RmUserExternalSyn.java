package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 成员同步外部联系人记录
 * rm_user_external_syn
 */
@Data
public class RmUserExternalSyn implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 企业ID
     */
    private String corpId;

    /**
     * 1：已同步2：未同步
     */
    private Integer isSynContacts;

    /**
     * 同步时间
     */
    private Date synDate;
    /**
     * 同步类型：1：群聊0：外部联系人
     */
    private Integer synType;

    private static final long serialVersionUID = 1L;
}
