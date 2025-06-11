package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;

/**
 * qywx_user_department
 * @author
 */
@Data
public class WeChatUserDepartment implements Serializable {
    /**
     * 系统内用户id
     */
    private String systemUserId;

    /**
     * 企业微信userId
     */
    private String userId;

    /**
     * 部门id
     */
    private String departmentId;

    /**
     * 企业ID
     */
    private String corpId;

    /**
     * 应用ID
     */
    private String suiteId;

    private static final long serialVersionUID = 1L;
}
