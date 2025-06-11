package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_external_info_archives_history
 * @author
 */
@Data
public class RmExternalInfoArchivesHistory implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 字段id
     */
    private String infoId;
    /**
     * 数据来源
     */
    private Integer sourceType;

    /**
     * 字段值
     */
    private String infoValue;

    /**
     * 居民id
     */
    private String externalUserId;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 操作时间
     */
    private Date date;

    /**
     * 操作人
     */
    private String userId;

    private static final long serialVersionUID = 1L;
}
