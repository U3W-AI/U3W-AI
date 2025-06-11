package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * rm_label
 * @author
 */
@Data
public class RmLabel implements Serializable {

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
     * 企业微信标签id
     */
    private String labelId;

    /**
     * 标签组id   存放label_group表的主键
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
     * 标签来源【1：手动创建   2：微信】
     */
    private Integer type;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 是否删除1：删除0:未删除
     */
    private Integer isDelete;

    /**
     * 是否系统1：是   0：否
     */
    private Integer isSystem;


}
