package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author 张云龙
 * rm_company
 */
@Data
public class RmCompany implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private String id;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建人
     */
    private String createBy;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 更新人
     */
    private String updateBy;

    /**
     * 企业简称
     */
    private String companyShortName;

    /**
     * 企业全称
     */
    private String companyFullName;
    /**
     * 企业logo
     */
    private String companyLogo;

    /**
     * 统一社会信用代码
     */
    private String creditCode;
}
