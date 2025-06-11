package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatThirdUser;

/**
 * @author sjl
 * @Created date 2024/2/28 15:22
 */
public interface WeChatThirdUserService {

    public R saveQywxThirdAuthUser(WeChatThirdUser qywxThirdUser);

    public R deleteQywxThirdAuthUser(String corpId,String agentId);
}
