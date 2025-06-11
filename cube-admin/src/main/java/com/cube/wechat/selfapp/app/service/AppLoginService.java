package com.cube.wechat.selfapp.app.service;


import com.cube.wechat.selfapp.app.domain.WxLoginBody;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月03日 11:00
 */
public interface AppLoginService {

    public String wxLogin(String decryptResult, WxLoginBody wxLoginBody);

    public String officeLogin(String unionId,String openId);

    public String qywxLogin(String qwId,String openId,String unionId,String corpId);
}
