package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author 张云龙
 * rm_company_label
 */
@Data
public class RmCompanyLabel implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private String id;

    /**
     * 标签名称
     */
    private String labelName;


    /**
     * 标签组id   存放company_label_group表的主键
     */
    private String groupId;

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
     * 企业id
     */
    private String corpId;

    /**
     * 是否删除1：删除0:未删除
     */
    private Integer isDelete;
}
