package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.AjaxResult;
import com.cube.wechat.thirdapp.entiy.WeChatLoginUrl;

/**
 @author sjl
  * @Created date 2024/3/4 14:42
 */
public interface WeChatLoginService {
    /**
     * 获取扫码登录地址
     */
    WeChatLoginUrl scanCodeLoginUrl(String paramUrl);

    /**
     * 获取企业微信内登录地址
     */
    WeChatLoginUrl wechatLoginUrl(String paramUrl);

    /**
     * 企业微信登录
     */
    AjaxResult wechatLogin(String auth_code, String loginType);
}
