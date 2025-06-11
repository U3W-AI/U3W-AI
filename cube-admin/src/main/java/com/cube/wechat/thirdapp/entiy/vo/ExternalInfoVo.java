package com.cube.wechat.thirdapp.entiy.vo;

import lombok.Data;

import java.util.List;

@Data

public class ExternalInfoVo {
    /**
     * 信息id
     */
    private String id;

    /**
     * 信息名称
     */
    private String infoName;
    /**
     * 控件名称
     */
    private String controlType;

    /**
     * 是否启用
     */
    private Integer isStatus;

    private List<String> selectValue;

    private Integer isRemind;

    private String remindTime;

    private Integer isDefault;




}
