package com.cube.wechat.selfapp.app.domain;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月03日 10:57
 */
@Data
@ApiModel(value = "WxLoginBody", description = "登录信息")
public class WxLoginBody {

        @ApiModelProperty(value = "微信临时code，只可使用一次", required = true)
        private String code;

        @ApiModelProperty(value = "企业微信临时code，只可使用一次", required = true)
        private String qwcode;

        @ApiModelProperty(value = "小程序APPID", required = true)
        private String appId;

        @ApiModelProperty(value = "小程序秘钥secret", required = true)
        private String appSecret;

        @ApiModelProperty(value = "加密算法的初始向量", required = false)
        private String encryptedIv;

        @ApiModelProperty(value = "加密算法的初始向量", required = false)
        private String encryptedData;


        private String nickName;
        private String avatar;



}
