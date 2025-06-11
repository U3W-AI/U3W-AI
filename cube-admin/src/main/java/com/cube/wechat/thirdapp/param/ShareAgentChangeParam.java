package com.cube.wechat.thirdapp.param;

import lombok.Data;

/**
 @author sjl
  * @Created date 2024/4/1 09:43
 */
@Data
public class ShareAgentChangeParam {
    //第三方应用的SuiteId
    private String suiteId;
    //上游企业id
    private String corpId;
    //上游企业应用id
    private String agentId;
}
