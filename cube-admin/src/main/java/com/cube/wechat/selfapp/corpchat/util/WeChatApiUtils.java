package com.cube.wechat.selfapp.corpchat.util;

import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.thirdapp.util.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * @author: yangpengwei
 * @time: 2020/12/11 4:29 下午
 * @description 微信 api 调用工具类
 */
@Slf4j
@Component
public class WeChatApiUtils {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${wechat.corpId}")
    private String corpId;

    @Value("${wechat.agentSecret}")
    private String agentSecret;

    @Value("${wechat.chatSecret}")
    private String chatSecret;


    @Value("${wechat.pcAgentSecret}")
    private String pcAgentSecret;


    @Value("${TencentDoc.url}")
    private String docUrl;

    @Value("${TencentDoc.clientId}")
    private String clientId;

    @Value("${TencentDoc.clientSecret}")
    private String clientSecret;

    @Value("${TencentDoc.redirectUri}")
    private String redirectUri;

    /**
     * 获取 企微自建应用accessToken
     *
     * @return accessToken
     */
    public String getAccessToken() {
        if (redisUtil == null) {
            redisUtil = new RedisUtil();
        }
        Object access_token = redisUtil.get("access_token");
        if (access_token != null) {
            return access_token + "";
        } else {
            String accessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + agentSecret;
            JSONObject jsonObject = RestUtils.get(accessTokenUrl);
            int errCode = jsonObject.getIntValue("errcode");
            if (errCode == 0) {
                redisUtil.set("access_token", jsonObject.getString("access_token"), 7000);
                return jsonObject.getString("access_token");
            }
        }
        return null;
    }

    public String getChatAccessToken() {
        if (redisUtil == null) {
            redisUtil = new RedisUtil();
        }
        Object access_token = redisUtil.get("chat_access_token");
        if (access_token != null) {
            return access_token + "";
        } else {
            String accessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + chatSecret;
            JSONObject jsonObject = RestUtils.get(accessTokenUrl);
            int errCode = jsonObject.getIntValue("errcode");
            if (errCode == 0) {
                redisUtil.set("chat_access_token", jsonObject.getString("access_token"), 7000);
                return jsonObject.getString("access_token");
            }
        }
        return null;
    }


    public String getPcAccessToken() {
        if (redisUtil == null) {
            redisUtil = new RedisUtil();
        }
        Object access_token = redisUtil.get("pc_access_token");

        if (access_token != null) {
            return access_token + "";
        } else {
            String accessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=" + corpId + "&corpsecret=" + pcAgentSecret;
            JSONObject jsonObject = RestUtils.get(accessTokenUrl);

            int errCode = jsonObject.getIntValue("errcode");
            if (errCode == 0) {
                String newAccessToken = jsonObject.getString("access_token");
                redisUtil.set("pc_access_token", newAccessToken, 7000);
                return newAccessToken;
            } else {
                log.error("获取企业微信AccessToken失败，请联系客服处理相关配置问题");
            }
        }

        return null;
    }

    public String getSelAccessToken() {
        if (redisUtil == null) {
            redisUtil = new RedisUtil();
        }
        Object access_token = redisUtil.get("sel_access_token");
        if (access_token != null) {
            return access_token + "";
        } else {
            String suiteAccessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/service/get_suite_token";
            JSONObject ticJson = new JSONObject();
            ticJson.put("suite_id", "wwc7714d9508debd8c");
            ticJson.put("suite_secret", "xLFPtRLAx0ic9cl-mPn31l4NlCKh2YYYM6KmjPmLpvQ");
            ticJson.put("suite_ticket", redisService.getCacheObject("test-external-suite-ticket"));
            JSONObject jsonObject = RestUtils.post(suiteAccessTokenUrl, ticJson);

            String suiteAccessToken = jsonObject.getString("suite_access_token");

            String accessTokenUrl = "https://qyapi.weixin.qq.com/cgi-bin/service/get_corp_token?suite_access_token=" + suiteAccessToken;
            JSONObject perJson = new JSONObject();
//            授权企业ID
            perJson.put("auth_corpid", "ww722362817b3c466a");
//            永久授权码
            perJson.put("permanent_code", "iout261WhUIaKE1y0fIzNgAWpYao_2tJXdTncAeyU2U");

            JSONObject jsonObjectTwo = RestUtils.post(accessTokenUrl, perJson);
            int errCode = jsonObject.getIntValue("errcode");
            if (errCode == 0) {
                redisUtil.set("sel_access_token", jsonObjectTwo.getString("access_token"), 7000);
                return jsonObjectTwo.getString("access_token");
            }
        }

        return null;
    }

    /**
     * 获取 公众号accessToken
     *
     * @return accessToken
     */
    public String getOfficeAccessToken(String appId, String secret) {


//        Object office_access_token = redisUtil.get(appId + "_office_access_token");
//        if(office_access_token!=null){
//            return office_access_token+"";
//        }else{

        String accessTokenUrl = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=" + appId + "&secret=" + secret;
        JSONObject jsonObject = RestUtils.get(accessTokenUrl);
        int errCode = jsonObject.getIntValue("errcode");
        if (errCode == 0) {
            redisUtil.set(appId + "_office_access_token", jsonObject.getString("access_token"), 7000);
            return jsonObject.getString("access_token");
        }
//        }

        return null;
    }

    /**
     * 获取 微信小店accessToken
     *
     * @return accessToken
     */
    public String getOrderAccessToken() {
        Object order_access_token = redisUtil.get("order_access_token");
        if (order_access_token != null) {
            return order_access_token + "";
        } else {
            String accessTokenUrl = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=&secret=";
            JSONObject jsonObject = RestUtils.get(accessTokenUrl);
            int errCode = jsonObject.getIntValue("errcode");
            if (errCode == 0) {
                redisUtil.set("order_access_token", jsonObject.getString("access_token"), 7000);
                return jsonObject.getString("access_token");
            }
        }

        return null;
    }

    /**
     * 获取腾讯文档token
     */
    public String getDocAccessToken(String userId) {
        Object access_token = redisUtil.get("doc_access_token_" + userId);
        if (access_token != null) {
            return access_token + "";
        } else {
            Object refresh_token = redisUtil.get("doc_refresh_token_" + userId);
            String url = String.format("%s?client_id=%s&client_secret=%s&grant_type=refresh_token&refresh_token%s",
                    docUrl,
                    clientId,
                    clientSecret,
                    refresh_token);
            JSONObject jsonObject = RestUtils.get(url);
            if (jsonObject != null) {
                String accessToken = jsonObject.get("access_token") + "";
                redisUtil.set("doc_access_token_" + userId, accessToken, 259200);
                return accessToken;
            }
        }
        return "";
    }


//    public  String getXDOfficeAccessToken() {
//
//
//        Object office_access_token = redisUtil.get("office_xudu_access_token");
//        if(office_access_token!=null){
//            return office_access_token+"";
//        }else{
//            String accessTokenUrl ="https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=&secret=";
//            JSONObject jsonObject = RestUtils.get(accessTokenUrl);
//            log.error("getAccessToken: " + jsonObject.get("access_token"));
//            int errCode = jsonObject.getIntValue("errcode");
//            if (errCode == 0) {
//                redisUtil.set("office_xudu_access_token",jsonObject.getString("access_token"),5000);
//                return jsonObject.getString("access_token");
//            }
//        }
//
//        return null;
//    }


}
