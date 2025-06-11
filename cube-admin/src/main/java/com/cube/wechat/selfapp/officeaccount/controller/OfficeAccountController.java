package com.cube.wechat.selfapp.officeaccount.controller;

import com.alibaba.fastjson.JSONObject;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.officeaccount.domain.OfficeParam;
import com.cube.wechat.selfapp.officeaccount.mapper.OfficeAccountMapper;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年08月13日 16:15
 */
@RestController
@RequestMapping("/office")
public class OfficeAccountController {

    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @Autowired
    private OfficeAccountMapper officeAccountMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Value("${office.appId}")
    private String appId;

    @Value("${office.appSecret}")
    private String appSecret;


    @GetMapping("/pullOfficeFans")
    public String pullOfficeFans(){

        String assessToken = weChatApiUtils.getOfficeAccessToken(appId,appSecret);

        // 拉取所有openId
        String fansUrl = "https://api.weixin.qq.com/cgi-bin/user/get?access_token="+assessToken;
        String nextOpenid = (String) redisUtil.get("nextID");
        if(nextOpenid==null){
            nextOpenid="";
        }
        int i =0;
        do {
            List<JSONObject> paramList = new ArrayList<>();
            JSONObject jsonObject = RestUtils.get(fansUrl+"&next_openid="+nextOpenid);
            System.out.println(fansUrl+"&next_openid="+nextOpenid);
            Map data = (Map) jsonObject.get("data");
            nextOpenid = (String) jsonObject.get("next_openid");
            System.out.println("nextID>>>>"+nextOpenid);
            redisUtil.set("nextID",nextOpenid);
            if(data!=null&&data.get("openid")!=null){
                List<String> openids = (List) data.get("openid");
                for (String openid : openids) {
                    System.out.println(i);
                    String fansInfoUrl = "https://api.weixin.qq.com/cgi-bin/user/info?access_token="+assessToken+"&openid="+openid+"&lang=zh_CN";
                    JSONObject fansInfoRes = RestUtils.get(fansInfoUrl);
                    fansInfoRes.put("openid",openid);
                    OfficeParam.updateSubscribeScene(fansInfoRes);
                    paramList.add(fansInfoRes);
                    i++;
                }
                officeAccountMapper.saveOfficeAccount(paramList);
            }

        }while (nextOpenid!=null && !nextOpenid.equals(""));

        return "拉取完成";
    }

    @GetMapping("/changeExUserId")
    public String changeExUserId(){

        String assessToken = weChatApiUtils.getAccessToken();
        List<JSONObject> resList = new ArrayList<>();
        List<JSONObject> pendList = new ArrayList<>();

        List<Map> openids = officeAccountMapper.getOpenIdList();
        int i =0;
        for (Map openidMap : openids) {
            String toExUserIdUrl = "https://qyapi.weixin.qq.com/cgi-bin/corpgroup/unionid_to_external_userid?access_token="+assessToken;
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("unionid", openidMap.get("unionid"));
            jsonObject.put("openid", openidMap.get("openid"));
            JSONObject resultObject = RestUtils.post(toExUserIdUrl, jsonObject);
            List<Map> external_userid_info = (List<Map>) resultObject.get("external_userid_info");
            if(external_userid_info.size()>0){
                jsonObject.put("external_userid_info",resultObject.get("external_userid_info").toString());
                System.out.println(resultObject.get("external_userid_info").toString());
                resList.add(jsonObject);
                officeAccountMapper.updateOfficeAccount(resList);
            }else{
                String toPendIdUrl = "https://qyapi.weixin.qq.com/cgi-bin/corpgroup/unionid_to_pending_id?access_token="+assessToken;
                JSONObject pendJsonObject = new JSONObject();
                pendJsonObject.put("unionid", openidMap.get("unionid"));
                pendJsonObject.put("openid", openidMap.get("openid"));
                JSONObject pendObject = RestUtils.post(toPendIdUrl, jsonObject);
                pendJsonObject.put("pending_id", pendObject.get("pending_id").toString());
                pendList.add(pendJsonObject);
                officeAccountMapper.updateOfficeAccountTwo(pendList);
            }
            System.out.println(i);
            i++;
        }

        System.out.println("已添加客户"+resList.size());
        System.out.println("未添加客户"+resList.size());

        return "拉取完成";
    }


}
