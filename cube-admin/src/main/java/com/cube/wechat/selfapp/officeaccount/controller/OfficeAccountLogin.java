package com.cube.wechat.selfapp.officeaccount.controller;

import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.app.util.XmlUtil;
import com.cube.wechat.selfapp.corpchat.util.RedisUtil;
import com.cube.wechat.selfapp.corpchat.util.WeChatApiUtils;
import com.cube.wechat.selfapp.officeaccount.entity.ApiResponse;
import com.cube.wechat.selfapp.officeaccount.entity.WeChatMessage;
import com.cube.wechat.selfapp.officeaccount.service.WeChatMessageService;
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
    
    @Autowired
    private WeChatMessageService weChatMessageService;

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
            String toUserName = (String) map.get("ToUserName");
            String msgType = (String) map.get("MsgType");
            String msgId = (String) map.get("MsgId");
            String createTimeStr = (String) map.get("CreateTime");
            
            Long createTime = null;
            if (createTimeStr != null) {
                try {
                    createTime = Long.parseLong(createTimeStr);
                    // 转换为毫秒时间戳
                    createTime = createTime * 1000;
                } catch (NumberFormatException e) {
                    System.err.println("解析创建时间失败: " + createTimeStr);
                }
            }
            
            String assessToken = weChatApiUtils.getOfficeAccessToken(appId,appSecret);
            System.out.println("token：：："+assessToken);
            String fansInfoUrl = "https://api.weixin.qq.com/cgi-bin/user/info?access_token="+assessToken+"&openid="+userOpenId+"&lang=zh_CN";
            JSONObject fansInfoRes = RestUtils.get(fansInfoUrl);
            String unionId = (String) fansInfoRes.get("unionid");
            
            String event = (String) map.get("Event");
            if("subscribe".equals(event)){
                redisUtil.set(map.get("Ticket")+"_unionid",unionId,300);
                redisUtil.set(map.get("Ticket")+"_openid",userOpenId,300);
            }else if("SCAN".equals(event)){
                redisUtil.set(map.get("Ticket")+"_unionid",unionId,300);
                redisUtil.set(map.get("Ticket")+"_openid",userOpenId,300);
            }
            
            // 处理普通消息（文本、图片、语音、视频、小视频、地理位置、链接）
            if ("text".equals(msgType) || "image".equals(msgType) || "voice".equals(msgType) || 
                "video".equals(msgType) || "shortvideo".equals(msgType) || "location".equals(msgType) || 
                "link".equals(msgType)) {
                
                String content = "";
                // 根据消息类型提取内容
                switch (msgType) {
                    case "text":
                        content = (String) map.get("Content");
                        break;
                    case "image":
                        content = "图片消息:" + map.get("PicUrl");
                        break;
                    case "voice":
                        content = "语音消息:" + map.get("MediaId");
                        break;
                    case "video":
                        content = "视频消息:" + map.get("MediaId");
                        break;
                    case "shortvideo":
                        content = "小视频消息:" + map.get("MediaId");
                        break;
                    case "location":
                        content = "位置消息:" + map.get("Label") + " (" + map.get("Location_X") + "," + map.get("Location_Y") + ")";
                        break;
                    case "link":
                        content = "链接消息:" + map.get("Title") + " - " + map.get("Url");
                        break;
                }
                
                // 创建消息对象并缓存
                if (content != null && !content.isEmpty() && createTime != null && unionId != null) {
                    WeChatMessage message = new WeChatMessage(
                        msgId, userOpenId, toUserName, createTime, msgType, content, unionId
                    );
                    weChatMessageService.cacheMessage(message);
                }
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
    
    /**
     * 根据消息查询用户UnionID
     * @param timestamp 消息发送的时间戳（10位数字）或时间格式字符串
     * @param content 用户发送的消息内容，必须完全匹配（区分大小写）
     * @return ApiResponse 包含查询结果和状态码
     */
    @GetMapping("/getUserByMessage")
    @ResponseBody
    public ApiResponse getUserByMessage(
            @RequestParam("timestamp") String timestamp,
            @RequestParam("content") String content) {
        
        return weChatMessageService.getUserByMessage(timestamp, content);
    }

}
