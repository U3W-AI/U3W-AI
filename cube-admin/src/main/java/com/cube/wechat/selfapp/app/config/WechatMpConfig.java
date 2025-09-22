package com.cube.wechat.selfapp.app.config;

import com.cube.wechat.selfapp.app.constants.WxExceptionConstants;
import com.cube.wechat.selfapp.app.domain.WechatInfo;
import com.cube.wechat.selfapp.app.util.WeChatUtils;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信公众号工具注册
 */
@Component
public class WechatMpConfig {
    private static final ConcurrentHashMap<String, WxMpService> map = new ConcurrentHashMap<>();
    @Autowired
    private WeChatUtils weChatUtils;

    /**
     * 获取工具对象并保存在map,方便下次获取
     * @param wechatInfo 微信公众号信息
     */
    public WxMpService wxMpService(WechatInfo wechatInfo) {
        try {
            WxMpService wxMpService = new WxMpServiceImpl();
            WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
            config.setAppId(wechatInfo.getAppId());
            config.setSecret(wechatInfo.getSecret());
            config.setToken(wechatInfo.getToken());
            wxMpService.setWxMpConfigStorage(config);
            return wxMpService;
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
        }
    }

    /**
     * 获取公众号服务
     * @param unionId 用户唯一标识
     */
    public String setWxMpService(String unionId) throws Exception{
        try {
            WechatInfo wechatInfoByUnionId = weChatUtils.getWechatInfoByUnionId(unionId);
            WxMpService wxMpService = wxMpService(wechatInfoByUnionId);
            if(wxMpService == null) {
                return "false";
            }
            map.put(unionId, wxMpService);
            return "true";
        } catch (URISyntaxException e) {
            throw e;
        }
    }

    /**
     * 获取公众号工具
     */
    public WxMpService getWxMpService(String unionId) {
        try {
            WxMpService wxMpService = map.get(unionId);
            if(wxMpService == null) {
                setWxMpService(unionId);
                return map.get(unionId);
            }
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_AUTH_EXCEPTION);
        }
        return map.get(unionId);
    }

}