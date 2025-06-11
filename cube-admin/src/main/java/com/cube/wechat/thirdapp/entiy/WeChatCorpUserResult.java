package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * qywx_corp_user
 * @author
 */
@Data
public class WeChatCorpUserResult implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 企业微信user_id
     */
    private String userId;

    /**
     * 企业微信user_name
     */
    private String userName;

    /**
     * 头像缩略图
     */
    private String thumbAvatar;

    /**
     * 用户唯一标识
     */
    private String openUserid;

    /**
     * 用户职位
     */
    private String position;

    /**
     * 激活状态: 0 无效 1=已激活，2=已禁用，4=未激活，5=退出企业
     */
    private Integer status;

    /**
     * 所属主部门id
     */
    private String departmentId;

    /**
     * 应用id
     */
    private String suiteId;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 部门内排序号
     */
    private Integer order;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 更新时间
     */
    private Date updateDate;
    /**
     * 部门集
     */
    private List<WeChatUserDepartment> userDepartmentList;

    private static final long serialVersionUID = 1L;
}
