package com.cube.wechat.thirdapp.constant;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @author sjl
 * @Created date 2024/2/23 16:53
 */
@Component
@Data
public class BasicConstant {
    // 服务商相关
    /**
     * 服务商CorpID
     */
    @Value("${serviceProviders.CorpID}")
    private String CorpID;
    /**
     * 服务商身份的调用凭证
     */
    @Value("${serviceProviders.ProviderSecret}")
    private String ProviderSecret;

    // 应用相关
    /**
     * 应用的唯一身份标识
     */
    @Value("${serviceProviders.SuiteID}")
    private String SuiteID;
    /**
     * 登录授权应用
     */
    @Value("${serviceProviders.loginSuiteId}")
    private String loginSuiteId;
    /**
     * 应用的调用身份密钥
     */
    @Value("${serviceProviders.SuiteSecret}")
    private String SuiteSecret;
    /**
     * 登录授权应用的调用身份密钥
     */
    @Value("${serviceProviders.loginSuiteSecret}")
    private String loginSuiteSecret;
    /**
     * 登录授权token
     */
    @Value("${serviceProviders.loginTOKEN}")
    private String loginTOKEN;
    /**
     * 登录授权EncodingAESKey
     */
    @Value("${serviceProviders.loginEncodingAESKey}")
    private String loginEncodingAESKey;


    // 回调相关
    /**
     * 回调/通用开发参数Token, 两者解密算法一样
     */
    @Value("${serviceProviders.TOKEN}")
    private String TOKEN;

    /**
     * 回调/通用开发参数EncodingAESKey, 两者解密算法一样
     */
    @Value("${serviceProviders.EncodingAESKey}")
    private String EncodingAESKey;

    /**
     * 居民管理-suitTicket-redisKey
     */
    @Value("${serviceProviders.SuitTicket}")
    private String SuitTicket;

    /**
     * 居民管理-login-suitTicket-redisKey
     */
    @Value("${serviceProviders.loginSuitTicket}")
    private String loginSuitTicket;
    /**
     * 居民管理- /**
     *   suitTicket-redisKey
     */
    @Value("${serviceProviders.SuiteAccessToken}")
    private String SuiteAccessToken;
    /**
     * 居民管理- /**
     *   suitTicket-redisKey
     */
    @Value("${serviceProviders.loginSuiteAccessToken}")
    private String loginSuiteAccessToken;

    /**
     * 居民管理-企业凭证 redisKey
     */
    @Value("${serviceProviders.CorpAccessToken}")
    private String CorpAccessToken;
    /**
     * 重定向地址
     */
    @Value("${serviceProviders.REDIRECT_URL}")
    private String REDIRECT_URL;
    @Value("${serviceProviders.THIRD_BUS_WECHAT_LOGIN}")
    private String THIRD_BUS_WECHAT_LOGIN;
    @Value("${serviceProviders.THIRD_BUS_WECHAT_AUTHORIZE_URL}")
    private String THIRD_BUS_WECHAT_AUTHORIZE_URL;
    @Value("${file.url}")
    private String fileUrl;
    private String WechatAdministrator = "WechatAdministrator";

    /**
     * 通用开发参数
     */
    @Value("${generalDevelopmentParameters.Token}")
    private String generalDevelopmentParameters_Token;
    @Value("${generalDevelopmentParameters.EncodingAESKey}")
    private String generalDevelopmentParameters_EncodingAESKey;
}
