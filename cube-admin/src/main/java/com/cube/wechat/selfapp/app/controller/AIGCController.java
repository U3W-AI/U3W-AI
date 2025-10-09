package com.cube.wechat.selfapp.app.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.annotation.Log;
import com.cube.common.core.controller.BaseController;
import com.cube.common.entity.UserLogInfo;
import com.cube.point.controller.PointsSystem;
import com.cube.wechat.selfapp.app.config.MyWebSocketHandler;
import com.cube.wechat.selfapp.app.domain.JsonRpcRequest;
import com.cube.wechat.selfapp.app.domain.UserInfoReq;
import com.cube.wechat.selfapp.app.domain.WcChromeData;
import com.cube.wechat.selfapp.app.mapper.AIGCMapper;
import com.cube.wechat.selfapp.app.service.AIGCService;
import com.cube.wechat.selfapp.app.util.AESEncryptor;
import com.cube.wechat.selfapp.app.util.HttpClientUtil;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.corpchat.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月17日 09:18
 */
@RestController
@RequestMapping("/aigc")
public class AIGCController extends BaseController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private MyWebSocketHandler myWebSocketHandler;

    @Autowired
    private WeChatApiUtils weChatApiUtils;

    @Autowired
    private AIGCService aigcService;

    @Value("${TencentDoc.url}")
    private String docUrl;

    @Value("${TencentDoc.clientId}")
    private String clientId;

    @Value("${TencentDoc.clientSecret}")
    private String clientSecret;

    @Value("${TencentDoc.redirectUri}")
    private String redirectUri;

    @Autowired
    private AIGCMapper aigcMapper;

    @Autowired
    private PointsSystem pointsSystem;

    @GetMapping("/getPlayWrighDrafts")
    public ResultBody getPlayWrighDrafts(WcChromeData wcChromeData){
        wcChromeData.setUsername(getUsername());
        wcChromeData.setUserId(getUserId());
        return aigcService.getPlayWrighDrafts(wcChromeData);
    }
    @GetMapping("/getNodeLog")
    public ResultBody getNodeLog(WcChromeData wcChromeData){
        wcChromeData.setUsername(getUsername());
        wcChromeData.setUserId(getUserId());
        return aigcService.getNodeLog(wcChromeData);
    }



    @PostMapping("/sendUserPrompt")
    public ResultBody sendUserPrompt(@RequestBody UserInfoReq userInfoReq) {

        try {
            aigcService.savePlayWrightTaskData(userInfoReq.getTaskId(),userInfoReq.getUserId(),userInfoReq.getCorpId());

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("发送成功");
    }

    @PostMapping("/sendAgentPrompt")
    public ResultBody sendAgentPrompt(@RequestBody Map<String, String> map) {

        try {
            UserInfoReq userInfoReq = new UserInfoReq();
            userInfoReq.setUserId(map.get("userId"));
            userInfoReq.setCorpId(map.get("corpId"));
            userInfoReq.setKeyword(map.get("keyWord"));
            userInfoReq.setUserPrompt(map.get("userPrompt"));
            userInfoReq.setTaskId(map.get("taskId"));
            userInfoReq.setRoles(map.get("roles"));
            userInfoReq.setType("START_AGENT");
//            myWebSocketHandler.sendMsgToClient(userInfoReq.getUserId(),userInfoReq.getUserPrompt(),userInfoReq);

            return ResultBody.success("成功");

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("网络出现异常，请重新尝试一次");
    }

    @PostMapping("/sendYBPrompt")
    public ResultBody sendYBPrompt(@RequestBody Map<String, String> map) {

        try {
            Thread.sleep(1500);
            UserInfoReq userInfoReq = new UserInfoReq();
            userInfoReq.setUserId(map.get("userId"));
            userInfoReq.setCorpId(map.get("corpId"));
            userInfoReq.setKeyword(map.get("keyWord"));
            userInfoReq.setUserPrompt(map.get("userPrompt"));
            userInfoReq.setTaskId(map.get("taskId"));
            userInfoReq.setRoles(map.get("roles"));
            userInfoReq.setType("START_YB");
//            myWebSocketHandler.sendMsgToClient(userInfoReq.getUserId(),userInfoReq.getUserPrompt(),userInfoReq);

            return ResultBody.success("成功");

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("网络出现异常，请重新尝试一次");
    }

    @PostMapping("/message")
    public ResultBody sendMessage(@RequestBody JsonRpcRequest jsonRpcRequest){

        try {

            String jsonstr = jsonRpcRequest.getParams().toString();
            JSONObject jsonObject = JSONObject.parseObject(jsonstr);

//            Integer points = pointsSystem.getUserPoints(jsonObject.get("userId")+"");
//            if(points < 1){
//                return ResultBody.error(201,"积分余额不足，请明日再来或者联系客服充值   ");
//            }

            jsonObject.put("type",jsonRpcRequest.getMethod());

            myWebSocketHandler.sendMsgToClient("mini-"+jsonObject.get("userId"),jsonObject.toJSONString(),jsonObject);

            pointsSystem.setUserPoint(jsonObject.get("userId")+"",jsonRpcRequest.getMethod(),null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");


            return ResultBody.success("发送成功");

        } catch (Exception e) {
            e.printStackTrace();
        }

        return ResultBody.success("发送成功");
    }

    @PostMapping("/saveUserChatData")
    public ResultBody saveUserChatData(@RequestBody Map map){
        aigcMapper.saveUserChatData(map);
        return ResultBody.success("成功");
    }
    @GetMapping("/getChatHistory")
    public ResultBody getChatHistory(String userId,int isAll){
        return ResultBody.success(aigcMapper.getChatHistory(userId,isAll));
    }

    @GetMapping("/getAIResult")
    public String getAIResult(String userPrompt,String userId,String type){

        try {
            //先通过userId获取内部ID
            Map map = aigcMapper.getUserInfoByYqId(userId);
            if(map == null){
                return "用户不存在";
            }
            String requestId = UUID.randomUUID().toString();
            CompletableFuture<String> future = new CompletableFuture<>();
            MyWebSocketHandler.registerFuture(requestId, future);



            userId = map.get("userId") + "";
            String corpId = map.get("corpId") + "";
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("userId",userId);
            jsonObject.put("userPrompt",userPrompt);
            jsonObject.put("type",type);
            jsonObject.put("corpId",corpId);
            jsonObject.put("requestId",requestId);
            myWebSocketHandler.sendMsgToClient("mini-"+jsonObject.get("userId"),jsonObject.toJSONString(),jsonObject);

            try {
                return future.get(250, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
               e.printStackTrace();
            } catch (Exception e) {
               e.printStackTrace();
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

        return "发送成功";
    }



    @GetMapping("/getYBDraft")
    public ResultBody getYBDraft(String taskId){

        List<Map> list = aigcService.getDraftContentList(taskId,"腾讯元宝");
        if(list.size() >0){
            return ResultBody.success(list);
        }else{
            return ResultBody.success(null);
        }


//        String str = aigcService.getDraftContent(taskId,"腾讯元宝");
//        if(str!=null){
//            List<Map> list = aigcService.getDraftContentList(taskId,"腾讯元宝");
//            return ResultBody.success(list);
//        }else{
//            return ResultBody.success(null);
//        }
    }
    @GetMapping("/getAgentDraft")
    public ResultBody getAgentDraft(String taskId){
        List<Map> list = aigcService.getDraftContentList(taskId,"Agent");
        if(list.size() >0){
            return ResultBody.success(list);
        }else{
            return ResultBody.success(null);
        }
//        String str = aigcService.getDraftContent(taskId,"Agent");
//        if(str!=null){
//            List<Map> list = aigcService.getDraftContentList(taskId,"Agent");
//            return ResultBody.success(list);
//        }else{
//            return ResultBody.success(null);
//        }
    }
    
    @GetMapping("/getBaiduDraft")
    public ResultBody getBaiduDraft(String taskId){
        try {
            List<Map> list = aigcService.getDraftContentList(taskId, "百度AI");
            if(list.size() > 0) {
                return ResultBody.success(list);
            } else {
                return ResultBody.success(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResultBody.success("获取百度AI稿库失败");
        }
    }


    @PostMapping("/sendDOCPrompt")
    public ResultBody sendDOCPrompt(@RequestBody Map<String, String> map) {

        try {
            String jsonstr = map.get("input")+"";
            JSONObject jsonObject = JSONObject.parseObject(jsonstr);
            UUID uuid = UUID.randomUUID();
            UserInfoReq userInfoReq = new UserInfoReq();
            userInfoReq.setCorpId(jsonObject.get("corpId")+"");
            userInfoReq.setUserId(jsonObject.get("userId")+"");
            userInfoReq.setUserPrompt(jsonObject.get("userPrompt")+"");
            userInfoReq.setTaskId(uuid.toString());
            userInfoReq.setType("START_DOC");

            long startTime = System.currentTimeMillis();
            long timeout = 600000;
            boolean isCompleted = false;
            Object str = null;
            while (!isCompleted && (System.currentTimeMillis() - startTime) < timeout) {
                str = aigcService.getDraftContent(userInfoReq.getTaskId(),"腾讯文档");
                if(str!=null){
                    isCompleted =true;
                }else {
                    Thread.sleep(2000);
                }
            }

            return ResultBody.success(str);

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("网络出现异常，请重新尝试一次");
    }

    @GetMapping("/sendNMPrompt")
    public ResultBody sendNMPrompt(String ciphertext) throws InterruptedException {
        try {

           Thread.sleep(70000);
           return ResultBody.success("返回结果");

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("成功");
    }

    @GetMapping("/sendDSPrompt")
    public ResultBody sendDSPrompt(String ciphertext) throws InterruptedException {
        try {
            Thread.sleep(1000);
            String jsonStr = AESEncryptor.decrypt(ciphertext);
            JSONObject jsonObject = JSONObject.parseObject(jsonStr);
            UserInfoReq userInfoReq = new UserInfoReq();
            userInfoReq.setUserId(jsonObject.get("userId")+"");
            userInfoReq.setUsername(jsonObject.get("username")+"");
            userInfoReq.setCorpId(jsonObject.get("corpId")+"");
            userInfoReq.setKeyword(jsonObject.get("keyword")+"");
            userInfoReq.setUserPrompt(jsonObject.get("userPrompt")+"");
            userInfoReq.setTaskId(jsonObject.get("taskId")+"");
            userInfoReq.setType("START_DS");
//            myWebSocketHandler.sendMsgToClient(userInfoReq.getUserId(),userInfoReq.getUserPrompt(),userInfoReq);
            long startTime = System.currentTimeMillis();
            long timeout = 600000;
            boolean isCompleted = false;
            Object str = null;
            while (!isCompleted && (System.currentTimeMillis() - startTime) < timeout) {
                str = redisUtil.get(jsonObject.get("taskId")+"_DS");
                if(str!=null){
                    isCompleted =true;
                }else {
                    Thread.sleep(1200);
                }
            }

            return ResultBody.success(str.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("成功");
    }

    @PostMapping("/sendMTPrompt")
    public ResultBody sendMTPrompt(@RequestBody Map<String, String> map) throws InterruptedException {
        try {
            Thread.sleep(500);
            UserInfoReq userInfoReq = new UserInfoReq();
            userInfoReq.setUserId(map.get("userId"));
            userInfoReq.setCorpId(map.get("corpId"));
            userInfoReq.setKeyword(map.get("userPrompt"));
            userInfoReq.setUserPrompt(map.get("userPrompt"));
            userInfoReq.setTaskId(map.get("taskId"));
            userInfoReq.setRoles(map.get("roles"));
            userInfoReq.setType("START_MT");
//            myWebSocketHandler.sendMsgToClient(userInfoReq.getUserId(),userInfoReq.getUserPrompt(),userInfoReq);
            long startTime = System.currentTimeMillis();
            long timeout = 600000;
            boolean isCompleted = false;
            String str = null;
            while (!isCompleted && (System.currentTimeMillis() - startTime) < timeout) {
                str = aigcService.getDraftContent(map.get("taskId"),"秘塔AI");
                if(str!=null){
                    isCompleted =true;
                }else {
                    Thread.sleep(2000);
                }
            }

            return ResultBody.success(str);

        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("网络出现异常，请重新尝试一次");
    }

    @PostMapping("/sendBaiduPrompt")
    public ResultBody sendBaiduPrompt(@RequestBody Map<String, String> map) throws InterruptedException {
        try {
            Thread.sleep(500);
            UserInfoReq userInfoReq = new UserInfoReq();
            userInfoReq.setUserId(map.get("userId"));
            userInfoReq.setCorpId(map.get("corpId"));
            userInfoReq.setKeyword(map.get("userPrompt"));
            userInfoReq.setUserPrompt(map.get("userPrompt"));
            userInfoReq.setTaskId(map.get("taskId"));
            userInfoReq.setRoles(map.get("roles"));
            userInfoReq.setType("START_BAIDU");
            long startTime = System.currentTimeMillis();
            long timeout = 600000;
            boolean isCompleted = false;
            String str = null;
            while (!isCompleted && (System.currentTimeMillis() - startTime) < timeout) {
                str = aigcService.getDraftContent(map.get("taskId"), "百度AI");
                if(str != null) {
                    isCompleted = true;
                } else {
                    Thread.sleep(2000);
                }
            }

            return ResultBody.success(str);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResultBody.success("网络出现异常，请重新尝试一次");
    }

    @PostMapping("/saveDraftContent")
    public ResultBody saveDraftContent(@RequestBody Map map){
//        if(map.get("aiName").equals("腾讯元宝")){
//            redisUtil.set(map.get("taskId")+"_YB",map.get("draftContent")+"",10);
//        }else if(map.get("aiName").equals("纳米搜索")){
//            redisUtil.set(map.get("taskId")+"_NM",map.get("draftContent")+"",10);
//        }else if(map.get("aiName").equals("DeepSeek")){
//            redisUtil.set(map.get("taskId")+"_DS",map.get("draftContent")+"",10);
//        }else if(map.get("aiName").equals("秘塔AI")){
//            redisUtil.set(map.get("taskId")+"_MT",map.get("draftContent")+"",10);
//        }else if(map.get("aiName").equals("福帮手智能体")){
//            redisUtil.set(map.get("taskId")+"_AGENT",map.get("draftContent")+"",10);
//        }
        return aigcService.saveDraftContent(map);
    }
    @PostMapping("/saveLogInfo")
    public ResultBody saveLogInfo(@RequestBody UserLogInfo userLogInfo){
        return aigcService.saveLogInfo(userLogInfo);
    }


    /**
    * 获取腾讯文档token
    * */
    @GetMapping("/getDocToken")
    public ResultBody getDocToken(String code,String userId){

        String url = String.format("%s?client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code&code=%s",
                docUrl,
                clientId,
                clientSecret,
                redirectUri,
                code);
        JSONObject jsonObject = RestUtils.get(url);

        if(jsonObject !=null){
            String accessToken = jsonObject.get("access_token")+"";
            String refreshToken = jsonObject.get("refresh_token")+"";
            String user_id = jsonObject.get("user_id")+"";
            redisUtil.set("doc_access_token_"+userId,accessToken,259200);
            redisUtil.set("doc_refresh_token_"+userId,refreshToken);
            redisUtil.set("doc_open_id_"+userId,user_id);
        }
        return ResultBody.success("获取授权成功");
    }

    /**
     *获取文档内容
     * */
    @GetMapping("/getDocContent")
    public ResultBody getDocContent(String userId,String docUrl){
        //首先截取URL
        String encodedID = null;
        if(docUrl !=null){
            String[] parts = docUrl.split("doc/");
            encodedID = parts[1];
        }

        //进行fileID 转换
        String apiUrl = "https://docs.qq.com/openapi/";

        String fileIDCovertUrl = String.format("%s?type=%s&value=%s", apiUrl+"drive/v2/util/converter", 2, encodedID);
        Object openId = redisUtil.get("doc_open_id_"+userId);
        String IdData = HttpClientUtil.sendGet(fileIDCovertUrl,null,weChatApiUtils.getDocAccessToken(userId),openId+"");
        Map fileIdMap = JSON.parseObject(IdData, Map.class);
        Map fileData = (Map) fileIdMap.get("data");
        String fileId = fileData.get("fileID")+"";
        //获取文档内容
        String docData = HttpClientUtil.sendGet("/openapi/doc/v3/"+fileId,null,weChatApiUtils.getDocAccessToken(userId),openId+"");


        return ResultBody.success(docData);
    }

    /**
     *更新文档内容
     * */
    @GetMapping("/updateDocContent")
    public ResultBody updateDocContent(String userId,String docUrl){
        //首先截取URL
        String encodedID = null;
        if(docUrl !=null){
            String[] parts = docUrl.split("doc/");
            encodedID = parts[1];
        }

        //进行fileID 转换
        String apiUrl = "https://docs.qq.com/openapi/";

        String fileIDCovertUrl = String.format("%s?type=%s&value=%s", apiUrl+"drive/v2/util/converter", 2, encodedID);
        Object openId = redisUtil.get("doc_open_id_"+userId);
        String IdData = HttpClientUtil.sendGet(fileIDCovertUrl,null,weChatApiUtils.getDocAccessToken(userId),openId+"");
        Map fileIdMap = JSON.parseObject(IdData, Map.class);
        Map fileData = (Map) fileIdMap.get("data");
        String fileId = fileData.get("fileID")+"";
        //获取文档内容
        String docData = HttpClientUtil.sendGet("/openapi/doc/v3/"+fileId,null,weChatApiUtils.getDocAccessToken(userId),openId+"");


        return ResultBody.success(docData);
    }

    @GetMapping("/getUserId")
    public String getUserIdByUnionId(String unionId) {
        return aigcService.getUserIdByUnionId(unionId);
    }

    @GetMapping("getUnionId")
    public String getUnionIdByUserId(String userId)  {
        return aigcService.getUnionIdByUserId(userId);
    }

}
