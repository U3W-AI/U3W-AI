package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月12日 16:25
 */
@Data
public class Comment {


    /*
    * 评论ID
    * */
    private String id;

    /*
    * 研报ID
    * */
    private String resId;

    /*
    * 评论人ID
    * */
    private String userId;

    /*
    * 评论内容
    * */
    private String comment;

    /*
    * 创建时间
    * */
    private String createTime;

    /*
    * 评论状态
    * */
    private Integer flowStatus;

    /*
    *
    *点赞数
    * */
    private Integer userlike;

    /*
    * 研报标题
    * */
    private String resTitle;

    private String keyWord;



    private Integer limit;

    private Integer page;
}
