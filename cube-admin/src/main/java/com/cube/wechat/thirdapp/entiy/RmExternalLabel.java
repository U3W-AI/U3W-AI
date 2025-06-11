package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_external_label
 * @author
 */
@Data
public class RmExternalLabel implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 外部联系人id
     */
    private String externalUserId;

    /**
     * 标签组ID
     */
    private String labelGroupId;

    /**
     * 标签id
     */
    private String labelId;

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

    private static final long serialVersionUID = 1L;
}
