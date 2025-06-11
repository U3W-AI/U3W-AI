package com.cube.wechat.thirdapp.param;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author 张云龙
 */
@Data
public class LabelParam implements Serializable {
    private static final long serialVersionUID = 1L;


    /**
     * 标签组id
     */
    private String groupId;

    /**
     * 标签组id
     */
    private List<String> groupIds;

    /**
     * 标签组名称
     */
    private String groupName;

    /**
     * 标签名称集合
     */
    private List<String> labelNames;


    /**
     * 标签 ids
     */
    private List<String> labelIds;

    /**
     *  true 是置顶  false 否
     */
    private Boolean isTop;




    /**
     * 标签 id
     */
    private String labelId;
    /**
     * 标签名称
     */
    private String labelName;


    /**
     * 企业id
     */
    private String corpId;
    /**
     * 是否有系统内置
     */
    private Integer isSystem;
}
