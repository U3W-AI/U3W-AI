package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * qywx_corp_relationship
 * @author
 */
@Data
public class WeChatCorpRelationship implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 上级企业id
     */
    private String corpParentId;

    /**
     * 服务商应用id
     */
    private String suiteId;

    /**
     * 企业应用id
     */
    private String agentId;

    /**
     * 状态1：有效0:无效
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
