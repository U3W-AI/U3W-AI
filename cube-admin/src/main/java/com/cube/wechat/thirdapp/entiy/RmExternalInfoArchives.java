package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_external_info_archives
 * @author
 */
@Data
public class RmExternalInfoArchives implements Serializable {
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
     * 外部联系人id
     */
    private String externalUserId;

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
    /**
     * 是否可以填写多个
     */
    private Integer isThereMultiple;
    /**
     * 控件名称
     */
    private String controlName;
    /**
     * 数据来源
     */
    private Integer sourceType;

    private static final long serialVersionUID = 1L;
}
