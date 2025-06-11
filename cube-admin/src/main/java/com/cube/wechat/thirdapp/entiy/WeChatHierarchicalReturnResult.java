package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

/**
 @author sjl
  * @Created date 2024/4/18 13:33
 */
@Data
public class WeChatHierarchicalReturnResult {
    /**
     * id
     */
    private String id;
    /**
     * 名称
     */
    private String name;
    /**
     * 数据类型 0：企业 1：部门 2：人员
     */
    private Integer dataType;
    /**
     * 上级id
     */
    private String parentId;
    /**
     * 企业id
     */
    private String corpId;
}
