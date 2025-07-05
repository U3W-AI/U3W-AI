package com.cube.wechat.selfapp.officeaccount.controller;

import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.app.util.XmlUtil;
import com.cube.wechat.selfapp.corpchat.util.RedisUtil;
import com.cube.wechat.selfapp.corpchat.util.WeChatApiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月06日 14:33
 */
@RestController
@RequestMapping("/mini")
public class OfficeAccountLogin {

    @Value("${office.appId}")
    private String appId;

    @Value("${office.appSecret}")
    private String appSecret;


    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @Autowired
    private RedisUtil redisUtil;

    /***
     * 微信服务器触发get请求用于检测签名
     * @return
     */
    @GetMapping("/handleWxCheckSignature")
    public String handleWxCheckSignature(HttpServletRequest request){


        //todo 严格来说这里需要做签名验证,我这里为了方便就不做了
        String echostr = request.getParameter("echostr");

        System.out.println(echostr);
        return echostr;

    }
    /**
     * 接收微信推送事件
     * @param request
     * @return
     */
    @PostMapping("/handleWxCheckSignature")
    @ResponseBody
    public String handleWxEvent(HttpServletRequest request){

        try {
            InputStream inputStream = request.getInputStream();

            Map<String, Object> map = XmlUtil.parseXML(inputStream);

            String userOpenId = (String) map.get("FromUserName");
            String assessToken = weChatApiUtils.getOfficeAccessToken(appId,appSecret);
            System.out.println("token：：："+assessToken);
            String fansInfoUrl = "https://api.weixin.qq.com/cgi-bin/user/info?access_token="+assessToken+"&openid="+userOpenId+"&lang=zh_CN";
            JSONObject fansInfoRes = RestUtils.get(fansInfoUrl);
            String event = (String) map.get("Event");
            if("subscribe".equals(event)){
                redisUtil.set(map.get("Ticket")+"_unionid",fansInfoRes.get("unionid"),300);
                redisUtil.set(map.get("Ticket")+"_openid",userOpenId,300);
            }else if("SCAN".equals(event)){
                redisUtil.set(map.get("Ticket")+"_unionid",fansInfoRes.get("unionid"),300);
                redisUtil.set(map.get("Ticket")+"_openid",userOpenId,300);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        return "success";

    }

    @GetMapping("/getQrCode")
    public Map<String, Object> getQrCode() {
        //获取临时二维码
        String url = String.format("https://api.weixin.qq.com/cgi-bin/qrcode/create?access_token=%s",weChatApiUtils.getOfficeAccessToken(appId,appSecret));

        JSONObject param =JSONObject.parseObject("{\"expire_seconds\": 300, \"action_name\": \"QR_STR_SCENE\", \"action_info\": {\"scene\": {\"scene_str\": \"test\"}}}");

        Map tabMap = RestUtils.post(url, param);
        return tabMap;
    }

}
