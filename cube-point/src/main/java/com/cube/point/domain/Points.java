package com.cube.point.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月12日 14:16
 */
@Data
public class Points {

    /*
    * 用户ID
    * */
    private String userId;

    /*
    * 用户昵称
    * */
    private String nickName;

    /*
    * 修改后
    * */
    private Integer balanceAfter;

    /*
    * 积分数量
    * */
    private Integer changeAmount;

    /*
    * 操作人
    * */
    private Long createId;

    /*
    * 操作人姓名
    * */
    private String createName;


    /*
    * 备注
    * */
    private String remark;

    private Integer limit;

    private Integer page;

    private Integer type;

    private String mainAddress;

    private String mainPrivateKey;

}
