package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_external_statistics_num
 * @author
 */
@Data
public class RmExternalStatisticsNum implements Serializable {
    /**
     * 企业人员ID
     */
    private String userId;

    /**
     * 外部联系人id
     */
    private String externalUserId;

    /**
     * 数据时间
     */
    private Date dataTime;
    /**
     * 添加时间
     */
    private Date addTime;

    /**
     * 外部联系人是否被该人员删除（1表示被删除，0表示未被删除）
     */
    private Integer isDeletedByPerson;

    /**
     * 该人员是否被外部联系人删除（1表示被删除，0表示未被删除）
     */
    private Integer isDeletedByContact;

    /**
     * 是否流失
     */
    private Integer isLost;

    /**
     * 企业id
     */
    private String corpId;

    private static final long serialVersionUID = 1L;
}
