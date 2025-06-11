package com.cube.wechat.thirdapp.param;

import lombok.Data;

/**
 @author sjl
  * @Created date 2024/3/7 09:27
 */
@Data
public class MemberChangeParam {
    //应用id
    private String suiteId;
    //变更信息的成员UserID
    private String userId;
    //全局唯一。对于同一个服务商，不同应用获取到企业内同一个成员的OpenUserID是相同的，最多64个字节。
    private String openUserId;
    //授权企业的CorpID
    private String corpId;
    //变更类型  create_user新增成员 update_user 更新成员 delete 删除成员
    private String changeType;
}
