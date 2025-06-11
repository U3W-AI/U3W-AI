package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * qywx_corp_department
 * @author
 */
@Data
public class WeChatCorpDepartment implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 部门Id
     */
    private String departmentId;

    /**
     * 部门名称
     */
    private String departmentName;

    /**
     * 部门上级ID
     */
    private String departmentParentId;

    /**
     * 排序号
     */
    private String departmentOrder;

    /**
     * 部门状态，0：无效1：有效
     */
    private Integer departmentStatus;

    /**
     * 应用id
     */
    private String suiteId;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 更新时间
     */
    private Date updateDate;

    private static final long serialVersionUID = 1L;
}
