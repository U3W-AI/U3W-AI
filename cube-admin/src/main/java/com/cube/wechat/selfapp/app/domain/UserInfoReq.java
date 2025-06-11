package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年01月22日 09:56
 */

@Data
public class UserInfoReq {

    private String taskId;

    private String keyword;

    private String userPrompt;

    private String username;

    private String userId;

    private String corpId;

    private String message;

    private String type;

    private String roles;


}
