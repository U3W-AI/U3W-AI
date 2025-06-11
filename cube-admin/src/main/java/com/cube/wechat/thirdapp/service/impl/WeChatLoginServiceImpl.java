package com.cube.wechat.thirdapp.service.impl;

import com.alibaba.fastjson.JSON;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.domain.R;
import com.cube.common.core.domain.model.LoginBody;
import com.cube.common.utils.StringUtils;
import com.cube.web.controller.system.SysLoginController;
import com.cube.wechat.thirdapp.constant.BasicConstant;
import com.cube.wechat.thirdapp.entiy.WeChatCorpUser;
import com.cube.wechat.thirdapp.entiy.WeChatLoginUrl;
import com.cube.wechat.thirdapp.mapper.RmRoleMapper;
import com.cube.wechat.thirdapp.mapper.RmUserRoleMapper;
import com.cube.wechat.thirdapp.service.WeChatCorpUserService;
import com.cube.wechat.thirdapp.service.WeChatDataService;
import com.cube.wechat.thirdapp.service.WeChatLoginService;
import com.cube.wechat.thirdapp.util.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

/**
 @author sjl
  * @Created date 2024/3/4 14:42
 */
@Service
@Slf4j
public class WeChatLoginServiceImpl implements WeChatLoginService {
    @Autowired
    private RedisService redisService;
    @Autowired
    private WeChatDataService weChatDataService;
    @Autowired
    private WeChatCorpUserService qywxCorpUserService;
    @Autowired
    private RmUserRoleMapper rmUserRoleMapper;
    @Autowired
    private RmRoleMapper rmRoleMapper;

    @Autowired
    private SysLoginController sysLoginController;
    @Autowired
    private BasicConstant constant;
    @Override
    public WeChatLoginUrl scanCodeLoginUrl(String paramUrl) {
        WeChatLoginUrl login = new WeChatLoginUrl();
        // 企业微信的CorpID
        String loginSuiteId = constant.getLoginSuiteId();
        // 重定向url
        String redirectUrl = constant.getREDIRECT_URL();
        if(StringUtils.isNotEmpty(paramUrl)){
            redirectUrl+=paramUrl;
        }
        log.info("登录地址url:" + redirectUrl + "企业微信登录授权id->" + loginSuiteId);
        // 重定向地址

        try {
            redirectUrl = URLEncoder.encode((redirectUrl), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        // 获取扫码登录链接
        String getWechatLogin = constant.getTHIRD_BUS_WECHAT_LOGIN();
        // 转换成登录地址
        String wechatLoginUrl = String.format(getWechatLogin, loginSuiteId, redirectUrl);
        login.setLoginUrl(wechatLoginUrl);
        log.info("重定向后登录地址url:" + login);
        return login;
    }

    @Override
    public WeChatLoginUrl wechatLoginUrl(String paramUrl) {
        log.info("wechatLogin->start");
        WeChatLoginUrl login = new WeChatLoginUrl();
        // 	第三方应用id（即ww或wx开头的suite_id）。
        String suiteId = constant.getSuiteID();
        // 重定向地址
        String redirectUrl = constant.getREDIRECT_URL();
        if(StringUtils.isNotEmpty(paramUrl)){
            redirectUrl+=paramUrl;
        }
        log.info("suiteId:" + suiteId + "==redirectUrl:" + redirectUrl);
        // 重定向地址
        try {
            redirectUrl = URLEncoder.encode(redirectUrl, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        //第三方 构造网页授权链接
        String getWechatLogin = constant.getTHIRD_BUS_WECHAT_AUTHORIZE_URL();
        // 转换成登录地址
        String loginUrl = String.format(getWechatLogin, constant.getSuiteID(), URLEncoder.encode(redirectUrl));
        login.setLoginUrl(loginUrl);
        log.info("企业微信内登录重定向url:" + loginUrl);
        return login;
    }

    @Override
    public AjaxResult wechatLogin(String auth_code, String loginType) {
        log.info("用户auth_code：" + auth_code);
        log.info("登录类型：" + loginType);
        R<java.util.Map> userInfoR=null;
        try {
            if (loginType.equals("QrCode")) {
                //二维码登录
                log.info("开始-----------------------------web扫码登录");
                userInfoR = weChatDataService.queryScanCoeUserIdentity(auth_code);
            } else if(loginType.equals("WeChat")){
                //内部登录
                log.info("开始-----------------------------企业微信内部登录");
                userInfoR = weChatDataService.queryUserIdentity(auth_code);
            }else{
                log.info("---------登录失败，未识别到登录环境，登录失败---------------");
            }
            log.info("获取用户信息:"+JSON.toJSONString(userInfoR));
            if (userInfoR!=null&&userInfoR.getCode() == R.SUCCESS) {
                java.util.Map userInfoMap = userInfoR.getData();
                String avatar = "";
                String userid = MapUtils.getString(userInfoMap, "userid");

                LoginBody loginBody = new LoginBody();
                loginBody.setCode("");
                loginBody.setUsername(userid);
                loginBody.setPassword("admin123");
                return sysLoginController.login(loginBody);
            } else {
                log.info("---------登录失败，未获取到用户信息，无权限或不存在---------------");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new AjaxResult();
    }


    /**
     * 更新用户头像
     * @param qywxCorpUser
     * @param
     * @param userTicket
     */
    public boolean updateUserAvatar(WeChatCorpUser qywxCorpUser, String userTicket) {
        //一天更新一次，如果已经更新过，不再更新
        Object cacheObject = redisService.getCacheObject("updateAvater_" + qywxCorpUser.getOpenUserid());
        if (cacheObject != null) {
            log.info("=================暂不更新头像==================");
            return true;
        } else {
            R<java.util.Map> sensitiveInformationR = weChatDataService.queryUserSensitiveInformation(userTicket);
            if (sensitiveInformationR.getCode() == R.SUCCESS) {
                log.info("=================更新用户头像==================");
                //获取用户头像
                java.util.Map userDetailResult = sensitiveInformationR.getData();
                String getAvatar = (String) userDetailResult.get("avatar");
                if (StringUtils.isNotEmpty(getAvatar)) {
                    qywxCorpUser.setThumbAvatar(getAvatar);
                    //更新用户头像
                    qywxCorpUserService.updateCorpUserAvatar(qywxCorpUser);
                    redisService.setCacheObject("updateAvater_" + qywxCorpUser.getOpenUserid(), 1, 86400L, TimeUnit.SECONDS);
                }
                return true;
            } else {
                return false;
            }
        }

    }
}
