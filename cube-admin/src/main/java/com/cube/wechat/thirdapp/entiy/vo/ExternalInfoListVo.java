package com.cube.wechat.thirdapp.entiy.vo;

import lombok.Data;

@Data
public class ExternalInfoListVo {

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
    private String controlName;

    /**
     * 是否启用
     */
    private Integer isStatus;

    /**
     * 控件排序
     */
    private Integer sort;



}
