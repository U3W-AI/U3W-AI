package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * @author 张云龙
 * rm_company_label_group
 */
@Data
public class RmCompanyLabelGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private String id;

    /**
     * 标签组名称
     */
    private String groupName;

    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 创建人
     */
    private String createBy;

    /**
     * 是否删除【0：正常    1：删除】
     */
    private Integer isDelete;

    /**
     * 企业id
     */
    private String corpId;




    /**
     * 企业标签集
     */
    private List<RmCompanyLabel> companyLabels;
}
