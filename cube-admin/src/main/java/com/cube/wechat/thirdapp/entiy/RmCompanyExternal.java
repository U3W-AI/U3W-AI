package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author 张云龙
 */
@Data
public class RmCompanyExternal implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键id
     */
    private String id;

    /**
     * 外部联系人id
     */
    private String externalUserId;

    /**
     * 公司id
     */
    private String companyId;

    /**
     * 用户id
     */
    private String userId;
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
     * 1:员工 2：负责人
     */
    private Integer type;

}
