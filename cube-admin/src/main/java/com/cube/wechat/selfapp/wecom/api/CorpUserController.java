package com.cube.wechat.selfapp.wecom.api;

import cn.felord.AgentDetails;
import cn.felord.DefaultAgent;
import cn.felord.api.ContactBookManager;
import cn.felord.api.WorkWeChatApi;
import cn.felord.domain.contactbook.department.DeptInfo;
import cn.felord.domain.contactbook.user.SimpleUser;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.domain.R;
import com.cube.common.core.domain.model.LoginBody;
import com.cube.web.controller.system.SysLoginController;
import com.cube.wechat.selfapp.wecom.entity.UserInfo;
import com.cube.wechat.selfapp.wecom.mapper.CorpUserMapper;
import com.cube.wechat.selfapp.wecom.mapper.WeComMapper;
import com.cube.wechat.selfapp.wecom.util.HttpClientUtil;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年08月29日 13:25
 */

@RestController
@RequestMapping("/wecom/corp")
@Slf4j
public class CorpUserController {

    @Autowired
    private SysLoginController sysLoginController;

    @Autowired
    private WorkWeChatApi workWeChatApi;

    @Autowired
    private CorpUserMapper corpUserMapper;

    @Autowired
    private WeChatApiUtils weChatApiUtils;


    @Autowired
    private WeComMapper weComMapper;


    @GetMapping("/getWeChatUserList")
    public ResultBody getWeChatUserList(){


        // 同步助手应用  也可以使用拥有通讯录能力的自建应用
        AgentDetails agent=new DefaultAgent("ww722362817b3c466a", "xO04FAPWFLeTun3SulRyEdTIMrcAgQfiwV8Kk4ipNrQ","1000008");

        // 通讯录相关API
        ContactBookManager contactBookManager = workWeChatApi.contactBookManager(agent);
        Map resMap = new HashMap();
        // 企业成员相关API
        List<DeptInfo> deptInfos = contactBookManager.departmentApi()
                // 获取所有部门
                .deptList()
                .getData();
        List <UserInfo> userInfoList = new ArrayList<>();
//        List <UserInfoResponse>  userInfoList = new ArrayList<>();
        for (DeptInfo deptInfo : deptInfos) {
            List<SimpleUser> userList  = contactBookManager.userApi().getDeptUsers(deptInfo.getId()).getData();
            for (SimpleUser simpleUser : userList) {
                UserInfo userInfo = new UserInfo();
                userInfo.setUserid(simpleUser.getUserid());
                userInfo.setName(simpleUser.getName());
                userInfo.setDepartment(simpleUser.getDepartment().get(0).toString());
                userInfo.setOpenUserid(simpleUser.getOpenUserid());
                userInfoList.add(userInfo);
            }
        }

        resMap.put("deptList",deptInfos);
        resMap.put("userList",userInfoList);

//        corpUserMapper.deleteDept();
//        corpUserMapper.deleteUser();
//        corpUserMapper.deleteUserRole();
//        corpUserMapper.deleteUserPost();
        corpUserMapper.saveDept(deptInfos);
        corpUserMapper.saveUser(userInfoList);
        corpUserMapper.initRole();
        corpUserMapper.initPost();
        return ResultBody.success(resMap);
    };



    @PostMapping(value = "/wechatUserLogin")
    public AjaxResult wechatUserLogin(@RequestBody LoginBody loginBody){
        //企业微信-用户登录
        System.out.println(loginBody.toString());
        String auth_code = loginBody.getCode();
        R<Map> userInfoR=null;
        try {
                //内部登录
                log.info("开始-----------------------------企业微信内部登录");
            String getUserInfoUrl = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token="+weChatApiUtils.getPcAccessToken()+"&code="+auth_code;
            System.out.println(getUserInfoUrl);
            String userInfoResult = HttpUtil.get(getUserInfoUrl);
            log.info("获取用户信息:"+ userInfoResult);
            Map userInfoMap = JSON.parseObject(userInfoResult, Map.class);

            String userid = MapUtils.getString(userInfoMap, "userid");
            loginBody.setUsername(userid);
            loginBody.setPassword("admin123");
            return sysLoginController.login(loginBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new AjaxResult();
    }


    @GetMapping("/initGroupChat")
    public ResultBody initGroupChat(){
        weComMapper.delRoomData();
        List<String> roomList = weComMapper.getRoomList();
        String accessToken = weChatApiUtils.getChatAccessToken();
        String requestUrl = "https://qyapi.weixin.qq.com/cgi-bin/msgaudit/groupchat/get?access_token=" + accessToken;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (String s : roomList) {

            JSONObject requestBody = new JSONObject();
            requestBody.put("roomid", s);

            String requestBodyStr = requestBody.toJSONString();

            String result = HttpClientUtil.doPost(requestUrl, requestBodyStr);
            org.json.JSONObject jsonObject = new org.json.JSONObject(result);
            if(jsonObject.get("errcode").equals(0)){
                Map map = new HashMap();
                map.put("roomId",s);
                map.put("roomname",jsonObject.get("roomname"));
                if(jsonObject.get("creator")!=null){
                    String userId = weComMapper.getUserId(jsonObject.get("creator")+"");
                    if(userId!=null){
                        map.put("creator",userId);
                    }else{
                        map.put("creator",5);
                    }
                }else{
                    map.put("creator",5);
                }

                map.put("notice",jsonObject.get("notice"));
                map.put("room_create_time", sdf.format(jsonObject.get("room_create_time")));
                weComMapper.saveRoomData(map);
            }
        }
        return ResultBody.success("成功");
    }



    public static void main(String[] args) {
        //参数
        Map<String, Object> map = new HashMap<>();
        map.put("msgtype", "template_card");

        Map templateCard = new HashMap<>();
        templateCard.put("card_type", "news_notice");

        //小logo
        Map source = new HashMap();
        source.put("icon_url", "");
        source.put("desc", "许都之行");
        source.put("desc_color", 0);

        //一级标题
        Map mainTitle = new HashMap();
        mainTitle.put("title", "根本改变源自许都之行");
        mainTitle.put("desc", "");
        Map cardImage = new HashMap();
        cardImage.put("url", "");
        cardImage.put("aspect_ratio", 2.25);



        Map pagePath = new HashMap();
        pagePath.put("type", 2);
        pagePath.put("appid", "wx9812ae7b613f2e49");
        pagePath.put("pagepath", "/pages/index");
        pagePath.put("title", "点击跳转小程序");

        List<Map> jumpList = new ArrayList<>();
        jumpList.add(pagePath);

        Map cardAction = new HashMap();
        cardAction.put("type", 2);
        cardAction.put("appid", "wx9812ae7b613f2e49");
        cardAction.put("pagepath", "/pages/index");


        templateCard.put("source", source);
//                            templateCard.put("sub_title_text", "根本改变源自许都之行");
        templateCard.put("jump_list", jumpList);
        templateCard.put("main_title", mainTitle);
        templateCard.put("card_image", cardImage);
        templateCard.put("card_action", cardAction);


        map.put("template_card", templateCard);
        String jsonString = JSON.toJSONString(map);
        String result = HttpRequest.post("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=cc4d7c79-2c1e-4e49-9263-5b837012fa52")
                .header("Content-Type", "application/json")
                .body(jsonString)
                .execute().body();
        System.out.println(result);
    }

}
