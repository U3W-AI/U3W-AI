package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author 张云龙
 * rm_company_label_relation
 */
@Data
public class RmCompanyLabelRelation implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 主键id
     */
    private String id;

    /**
     * 存rm_company的id
     */
    private String companyId;

    /**
     * 存rm_company_label的id
     */
    private String labelId;

    /**
     * 存rm_company_label的group_id，也是rm_company_label_group的id
     */
    private String groupId;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 创建时间
     */
    private Date createTime;
}
