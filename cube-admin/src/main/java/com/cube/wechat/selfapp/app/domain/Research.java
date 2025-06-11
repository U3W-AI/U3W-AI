package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月13日 09:11
 */
@Data
public class Research {

    /*
    * 主键ID
    * */
    private String id;

    /*
    * 标题
    * */
    private String title;

    /*
    * 关键词
    * */
    private String keyWord;

    /*
    * 下载链接
    * */
    private String resUrl;


    /*
    * 研报状态
    * */
    private Integer flowStatus;

    /*
    * 所属行业
    * */
    private String industry;

    /*
    * 标签
    * */
    private String tag;

    /*
    * 研报原作者
    * */
    private String resource;

    /*
    * 驳回原因
    * */
    private String reason;

    /*
    * 是否置顶
    * */
    private String istop;

    /*
    * 上传人ID
    * */
    private Long userId;

    /*
    * 上传人用户名
    * */
    private String userName;

    /*
    * 上传时间
    * */
    private String createTime;

    private Integer dataType;

    /*
    * 消耗积分
    * */
    private Integer changeAmount;

    private Integer page;

    private Integer limit;
}
