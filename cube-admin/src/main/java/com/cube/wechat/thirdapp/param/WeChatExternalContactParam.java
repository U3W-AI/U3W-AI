package com.cube.wechat.thirdapp.param;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 张云龙
 */

@Data
public class WeChatExternalContactParam implements Serializable {
    /**
     * 外部联系人id
     */
    private String externalUserId;

    /**
     * 调用接口凭证
     */
    private String accessToken;

    /**
     * 授权企业的CorpID
     */
    private String corpId;
    /**
     * suiteId
     */
    private String  suiteId;
    /**
     * 变更类型
     */
    private String changeType;
    /**
     * 企业人员id
     */
    private String userId;
    /**
     * 分页参数
     */
    private Integer next_cursor;
}
