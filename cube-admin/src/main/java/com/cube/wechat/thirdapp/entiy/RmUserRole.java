package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_user_role
 * @author
 */
@Data
public class RmUserRole implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 系统人员id
     */
    private String systemUserId;
    /**
     * 用户id
     */
    private String userId;

    /**
     * 角色id
     */
    private String roleId;
    /**
     * 企业id
     */
    private String corpId;

    /**
     * 创建时间
     */
    private Date createDate;

    private static final long serialVersionUID = 1L;
}
