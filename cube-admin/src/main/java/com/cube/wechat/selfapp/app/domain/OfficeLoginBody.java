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
@ApiModel(value = "OfficeLoginBody", description = "登录信息")
public class OfficeLoginBody {


        @ApiModelProperty(value = "unionId", required = true)
        private String unionId;

        private String ticket;




}
