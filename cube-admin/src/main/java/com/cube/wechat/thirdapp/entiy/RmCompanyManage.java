package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 张云龙
 * rm_company_manage
 */
@Data
public class RmCompanyManage implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键id
     */
    private String id;

    /**
     * 作为rm_company表的外键
     */
    private String companyId;

    /**
     * 管理部门/人员id
     */
    private String rangeId;
    /**
     * 上级id
     */
    private String parentId;

    /**
     * 管理类型1：部门2：人员
     */
    private Integer dataType;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * range_id拼接parent_id
     */
    private String uniqueId;
}
