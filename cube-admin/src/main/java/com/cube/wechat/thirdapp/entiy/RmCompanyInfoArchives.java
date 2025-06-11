package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_external_info_archives
 * @author
 */
@Data
public class RmCompanyInfoArchives implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 字段id
     */
    private String infoId;
    /**
     * 字段值
     */
    private String infoValue;

    /**
     * 公司id
     */
    private String companyId;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 创建人id
     */
    private String createUserId;

    /**
     * 修改人id
     */
    private String updateUserId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 修改时间
     */
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}
