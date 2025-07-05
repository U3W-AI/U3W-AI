package com.cube.wechat.selfapp.app.controller;

import com.alibaba.fastjson.JSONObject;
import com.cube.common.constant.Constants;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.utils.StringUtils;
import com.cube.common.utils.sign.Base64;
import com.cube.wechat.selfapp.app.domain.OfficeLoginBody;
import com.cube.wechat.selfapp.app.domain.WxLoginBody;
import com.cube.wechat.selfapp.app.service.impl.AppLoginServiceImpl;
import com.cube.wechat.selfapp.corpchat.util.RedisUtil;
import com.cube.wechat.selfapp.corpchat.util.WeChatApiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Arrays;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月03日 10:53
 */
@RestController
@RequestMapping("/mini")
public class AppLoginController {



    @Autowired
    private AppLoginServiceImpl sysLoginService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private RedisUtil redisUtil;


    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @PostMapping("/wxLogin")
    public AjaxResult wxLogin(@RequestBody WxLoginBody wxLoginBody) {
        String code = wxLoginBody.getCode();
        //秘钥
        String encryptedIv = wxLoginBody.getEncryptedIv();
        //加密数据
        String encryptedData = wxLoginBody.getEncryptedData();
        //向微信服务器发送请求获取用户信息
        String url = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wxLoginBody.getAppId() + "&secret=" + wxLoginBody.getAppSecret() + "&js_code=" + code + "&grant_type=authorization_code";
        String res = restTemplate.getForObject(url, String.class);
        JSONObject jsonObject = JSONObject.parseObject(res);

        //获取session_key和openid
        String sessionKey = jsonObject.getString("session_key");
        String openid = jsonObject.getString("openid");

        //解密
        String decryptResult = "";
        try {
            //如果没有绑定微信开放平台，解析结果是没有unionid的。
            decryptResult = decrypt(sessionKey, encryptedIv, encryptedData);
        } catch (Exception e) {
            e.printStackTrace();
            return AjaxResult.error("微信登录失败！");
        }

        if (StringUtils.hasText(decryptResult)) {
            //如果解析成功,获取token
            String token = sysLoginService.wxLogin(decryptResult,wxLoginBody);
            AjaxResult ajax = AjaxResult.success();
            ajax.put(Constants.TOKEN, token);
            return ajax;
        } else {
            return AjaxResult.error("微信登录失败！");
        }
    }

    @PostMapping("/qywxLogin")
    public AjaxResult qywxLogin(@RequestBody WxLoginBody wxLoginBody) {
        String code = wxLoginBody.getCode();
        String qwcode = wxLoginBody.getQwcode();
        String wxurl = "https://api.weixin.qq.com/sns/jscode2session?appid=" + wxLoginBody.getAppId() + "&secret=" + wxLoginBody.getAppSecret() + "&js_code=" + code + "&grant_type=authorization_code";
        String wxres = restTemplate.getForObject(wxurl, String.class);
        JSONObject wxjsonObject = JSONObject.parseObject(wxres);

        String assessToken = weChatApiUtils.getAccessToken();
        //向微信服务器发送请求获取用户信息
        String url = "https://qyapi.weixin.qq.com/cgi-bin/miniprogram/jscode2session?access_token=" + assessToken + "&js_code=" + qwcode + "&grant_type=authorization_code";
        String res = restTemplate.getForObject(url, String.class);
        JSONObject jsonObject = JSONObject.parseObject(res);


        //获取session_key和openid
        String sessionKey = jsonObject.getString("session_key");
        String qwid = jsonObject.getString("userid");
        String openid = wxjsonObject.getString("openid");
        String unionid = wxjsonObject.getString("unionid");
        String corpid = jsonObject.getString("corpid");

         if(openid == null || openid ==""){
             return AjaxResult.error("企微登录未获取到openid！");
         }
            //如果解析成功,获取token
            String token = sysLoginService.qywxLogin(qwid,openid,unionid,corpid);
            AjaxResult ajax = AjaxResult.success();
            ajax.put(Constants.TOKEN, token);
            return ajax;
    }

    @PostMapping("/officeLogin")
    public AjaxResult officeLogin(@RequestBody OfficeLoginBody officeLoginBody) {
            Object unionId = redisUtil.get(officeLoginBody.getTicket()+"_unionid");
            Object openId = redisUtil.get(officeLoginBody.getTicket()+"_openid");
            if(unionId!=null){
                //如果解析成功,获取token
                String token = sysLoginService.officeLogin(unionId.toString(),openId.toString());
                AjaxResult ajax = AjaxResult.success();
                ajax.put(Constants.TOKEN, token);
                return ajax;
            }
            AjaxResult ajax = AjaxResult.error(218,"未扫码");
            return ajax;
    }

    /**
     * AES解密
     */
    private String decrypt(String sessionKey,String encryptedIv,String encryptedData) throws Exception{
        // 转化为字节数组
        byte[] key = Base64.decode(sessionKey);
        byte[] iv = Base64.decode(encryptedIv);
        byte[] encData = Base64.decode(encryptedData);
        // 如果密钥不足16位，那么就补足
        int base =16;
        if (key.length % base !=0) {
            int groups = key.length / base +(key.length % base != 0 ? 1 : 0);
            byte[] temp = new byte[groups * base];
            Arrays.fill(temp,(byte) 0);
            System.arraycopy(key,0,temp,0,key.length);
            key = temp;
        }
        // 如果初始向量不足16位，也补足
        if (iv.length % base !=0) {
            int groups = iv.length / base +(iv.length % base != 0 ? 1 : 0);
            byte[] temp = new byte[groups * base];
            Arrays.fill(temp,(byte) 0);
            System.arraycopy(iv,0,temp,0,iv.length);
            iv = temp;
        }

        AlgorithmParameterSpec ivSpec = new IvParameterSpec(iv);
        String resultStr = null;

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key,"AES");
            cipher.init(Cipher.DECRYPT_MODE,keySpec,ivSpec);
            resultStr = new String(cipher.doFinal(encData),"UTF-8");
        } catch (Exception e){
            //            logger.info("解析错误");
            e.printStackTrace();
        }

        // 解析加密后的字符串
        return resultStr;
    }
}
