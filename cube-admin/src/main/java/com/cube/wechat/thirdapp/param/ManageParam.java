package com.cube.wechat.thirdapp.param;

import lombok.Data;

/**
 * @author 张云龙
 */
@Data
public class ManageParam {
    /**
     * 管理部门/人员id
     */
    private String rangeId;

    /**
     * 上级部门id
     */
    private String parentId;
    /**
     * 管理类型1：部门2：人员
     */
    private Integer dataType;
}
