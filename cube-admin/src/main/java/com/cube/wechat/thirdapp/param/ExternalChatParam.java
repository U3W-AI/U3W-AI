package com.cube.wechat.thirdapp.param;

import lombok.Data;

import java.util.List;

/**
 @author sjl
  * @Created date 2024/5/16 09:58
 */
@Data
public class ExternalChatParam {
    private String infoType;
    private String corpId;
    private String suiteId;
    private String chatId;
    private String changeType;
    private String updateDetail;
    //入群方式
    private Integer joinScene;
    //退群方式
    private Integer quitScene;
    //版本号
    private String curMemVer;
    /**
     * 加入人
     */
    private List<String> memChangeList;
}
