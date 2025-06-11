package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

/**
 * @author keke
 * @version JDK 1.8
 * @date 2024年11月12日
 */
@Data
public class Strategy {

    /** 攻略表id */
    private String id;

    /** 攻略标题 */
    private String strategyTitle;

    /** 内容 */
    private String strategyContent;

    /** 作者 */
    private String author;

    private String desc;


    /** 收藏id */
    private Long collectionsId;

    /** 攻略图 */
    private String picUrl;


    /** 标签id */
    private String tag;

    private Integer page;

    private Integer limit;
}
