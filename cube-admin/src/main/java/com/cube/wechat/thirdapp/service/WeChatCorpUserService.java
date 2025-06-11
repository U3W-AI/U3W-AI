package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatCorpUser;

import java.util.List;
import java.util.Map;

public interface WeChatCorpUserService {

    public R<List<WeChatCorpUser>> saveCorpUser(List<WeChatCorpUser> WeChatCorpUserList);

    public R updateCorpUserStatus(WeChatCorpUser WeChatCorpUser);

    public R selectCorpUserInfo(WeChatCorpUser corpUser);
    public R updateCorpUserAvatar(WeChatCorpUser corpUser);
    public R deleteCorpUser(WeChatCorpUser WeChatCorpUser);

    public R<WeChatCorpUser> selectCorpUserId(WeChatCorpUser WeChatCorpUser);


    public R<List<WeChatCorpUser>> selectAllUserByCorpId(WeChatCorpUser WeChatCorpUser);

    public R<WeChatCorpUser> selectUserBasicInformation(WeChatCorpUser WeChatCorpUser);

    R selectUserNameByUserIds(Map map);
}
