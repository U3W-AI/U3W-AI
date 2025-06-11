package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * rm_label_group
 * @author
 */
@Data
public class RmLabelGroup implements Serializable {
    /**
     * 主键
     */
    private String id;

    /**
     * 标签组名称
     */
    private String groupName;

    /**
     * 1：创建2：微信导入
     */
    private Integer groupType;

    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 创建人
     */
    private String createUser;


    /**
     * 更新人
     */
    private String updateUser;

    /**
     * 创建时间
     */
    private Date updateTime;

    /**
     * 是否删除【0：正常    1：删除】
     */
    private Integer isDelete;


    /**
     * 是否置顶1：置顶0:不置顶
     */
    private Integer isTop;

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 企业微信给的标签组id
     */
    private String groupId;



    /**
     * 标签集
     */
    private List<RmLabel> labelList;


    /**
     * 是否系统1：是   0：否
     */
    private Integer isSystem;

    private static final long serialVersionUID = 1L;
}
