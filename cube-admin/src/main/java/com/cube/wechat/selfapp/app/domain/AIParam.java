package com.cube.wechat.selfapp.app.domain;

import lombok.Data;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月31日 11:07
 */
@Data
public class AIParam {

    private String userPrompt;

    private String userId;

    private String conversationId;

    private String response;

    private String fileUrl;


    /*
    * 1.text  2.file
    * */
    private Integer chatType;

    private String chatHistory;

    private String assistantId ;

    private String token ;

}
