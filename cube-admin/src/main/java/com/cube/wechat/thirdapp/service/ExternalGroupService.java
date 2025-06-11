package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;

import java.util.Map;

/**
 @author sjl
  * @Created date 2024/5/15 21:33
 */
public interface ExternalGroupService {
    public R<Map> synCorpExternalGroup(String corpId, String userId);

    public R<Map> synExternalGrpupListByUserId(Map map);

    public R<Map> createExternalGroup(Map map);
    public R<Map> updateExternalGroup(Map map);

    public R<Map> addGrouChatMember(Map map);
    public R<Map> delGroupChatMember(Map map);

    public R<Map> updateGroupChatOwner(Map map);

    public R<Map> dismissGroupChat(Map map);

    public R<Map> updateGroupChatMemberName();
}
