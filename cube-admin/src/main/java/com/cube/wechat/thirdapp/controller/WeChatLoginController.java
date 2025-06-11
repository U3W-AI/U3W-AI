package com.cube.wechat.thirdapp.controller;

import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.domain.model.LoginBody;
import com.cube.wechat.thirdapp.entiy.WeChatLoginUrl;
import com.cube.wechat.thirdapp.service.WeChatLoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 @author sjl
  * @Created date 2024/3/4 14:41
 */
@RequestMapping("/weChatLogin")
@RestController
public class WeChatLoginController {
    @Autowired
    private WeChatLoginService weChatLoginService;
    /**
     * 获取扫码登录地址
     */
    @GetMapping(value = "getScanCodeLogin")
    public WeChatLoginUrl scanCodeLoginUrl(@RequestParam(value = "paramUrl", required = false) String paramUrl) {
        //获取扫码登录地址
        return weChatLoginService.scanCodeLoginUrl(paramUrl);
    }
    /**
     * 获取企业微信内登录地址
     */
    @GetMapping(value = "getLoginUrl")
    public WeChatLoginUrl wechatLoginUrl(@RequestParam(value = "paramUrl", required = false) String paramUrl){
        //获取企业微信内登录地址
        return weChatLoginService.wechatLoginUrl(paramUrl);
    }

    @PostMapping(value = "wechatUserLogin")
    public AjaxResult wechatUserLogin(@RequestBody LoginBody loginBody){
        //企业微信-用户登录
        return weChatLoginService.wechatLogin(loginBody.getCode(),"WeChat");
    }


}
