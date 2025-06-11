package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

@Data
public class WcOfficeAccount {
    // 主键

    private Long id;
    // 公众号appid
    private String appId;
    // 公众号secret
    private String appSecret;
    // 公众号名称
    private String officeAccountName;
    //登录用户id
    private Long userId;
    //登录用户名
    private String userName;
    //公众号素材id
    private String mediaId;
    //图片路径
    private String picUrl;
}
