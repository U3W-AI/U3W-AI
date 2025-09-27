package com.cube.wechat.selfapp.app.controller;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.annotation.RateLimiter;
import com.cube.common.core.controller.BaseController;
import com.cube.common.core.page.TableDataInfo;
import com.cube.common.utils.StringUtils;
import com.cube.mcp.entities.ImgInfo;
import com.cube.mcp.entities.Item;
import com.cube.wechat.selfapp.app.config.MyWebSocketHandler;
import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.PromptTemplate;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.app.domain.query.ScorePromptQuery;
import com.cube.wechat.selfapp.app.service.AIGCService;
import com.cube.wechat.selfapp.app.service.UserInfoService;
import com.cube.wechat.selfapp.app.service.WechatMpService;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年09月04日 09:26
 */

@RestController
@RequestMapping("/mini")
public class UserInfoController extends BaseController {


    @Autowired
    private UserInfoService userInfoService;


    @Autowired
    private MyWebSocketHandler myWebSocketHandler;
    @Autowired
    private WechatMpService wechatMpService;
    @Autowired
    private AIGCService aigcService;

    @GetMapping("/getOfficeAccount")
    public ResultBody getOfficeAccount(){
        return userInfoService.getOfficeAccount(getUserId());
    }

    @GetMapping("/getUserCount")
    public ResultBody getUserCount(@ApiParam("用户ID") String userId){
        return userInfoService.getUserCount(userId);
    };

    @PostMapping("/getUserPointsRecord")
    public ResultBody getUserPointsRecord(@RequestBody Map map){
        return userInfoService.getUserPointsRecord(map);
    };

    @PostMapping("/saveAIChatHistory")
    public ResultBody saveAIChatHistory(@RequestBody AIParam aiParam){
        return userInfoService.saveAIChatHistory(aiParam);
    }

    @PostMapping("/saveAINodeLog")
    public ResultBody saveAINodeLog(@RequestBody AINodeLog AINodeLog){
        return userInfoService.saveAINodeLog(AINodeLog);
    }



    @GetMapping("/getUserChatHistory")
    public ResultBody getUserChatHistory(String userId,String title){
        return userInfoService.getUserChatHistoryList(userId,title);
    }

    @GetMapping("/getChatHistoryDetail")
    public ResultBody getChatHistoryDetail(String conversationId){
        return userInfoService.getChatHistoryDetail(conversationId);
    }

    @PostMapping("/updateChatTitle")
    public ResultBody updateChatTitle(@RequestBody Map map){
        return userInfoService.updateChatTitle(map);
    }

    @PostMapping("/deleteUserChatHistory")
    public ResultBody deleteUserChatHistory(@RequestBody List<String> list){
        return userInfoService.deleteUserChatHistory(list);
    }

    @PostMapping("/saveChromeData")
    public ResultBody saveChromeData(@RequestBody Map map){
        if(map!=null){
            if(map.get("answer")!=null && !map.get("answer").equals("")){
                map.put("answer",map.get("answer").toString().replaceAll("<[^>]+>", "").trim());
            }
            map.put("promptNum",map.get("prompt").toString().length());
            map.put("answerNum",map.get("answer").toString().length());
            map.put("username",map.get("username").toString().trim());
            userInfoService.saveChromeData(map);
        }

        return ResultBody.success("");
    }


    @PostMapping("/pushOffice")
    public ResultBody pushOffice(@RequestBody List<String> ids){
        String userName = getUsername();
//        String userName = "o3lds67b1zyFvifHTC_32epnmzqM";
        return userInfoService.pushOffice(ids,userName);
    }

    @RateLimiter(time = 60, count = 5)
    @GetMapping("/authChecker")
    public ResultBody checkAuth(String username){
        return userInfoService.authChecker(username);
    }

    @RateLimiter(time = 60, count = 10)
    @GetMapping("/changePoint")
    public ResultBody changePoint(String userId,String method){
        return userInfoService.changePoint(userId,method);
    }


    @PostMapping("/pushAutoOffice")
    public ResultBody pushAutoOffice(@RequestBody Map map){
//        return userInfoService.pushAutoOneOffice(map);
        try {
            String userId = map.get("userId") + "";
            String contentText = map.get("contentText") + "";
            String unionId = aigcService.getUnionIdByUserId(userId);
            WcOfficeAccount woa = (WcOfficeAccount) userInfoService.getOfficeAccountByUserId(userId).getData();
//            获取素材信息
            int first = contentText.indexOf("《");
            int second = contentText.indexOf("》", first + 1);
            String title = contentText.substring(first + 1, second);
            contentText = contentText.substring(second + 1, contentText.lastIndexOf(">") + 1);
            contentText = contentText.replaceAll("\r\n\r\n", "");
            map.put("userId",userId);
            map.put("title",title);
            map.put("contentText", contentText);
            map.put("unionId", unionId);
            map.put("thumbMediaId", woa.getMediaId());
            return wechatMpService.publishToOffice(map);
        } catch (Exception e) {
            throw new RuntimeException("内容解析失败");
        }
    }

    @GetMapping("/getViewAutoOffice")
    public ResultBody getViewAutoOffice(String taskId){
        return userInfoService.pushViewAutoOffice(taskId);
    }

    @GetMapping("/getAgentBind")
    public ResultBody getAgentBind(){
        return userInfoService.getAgentBind(getUserId());
    }

    @RateLimiter(time = 60, count = 10)
    @GetMapping("/getSpaceInfoByUserId")
    public ResultBody getSpaceInfoByUserId(String userId){
        if(StringUtils.isNotEmpty(userId)){
            return userInfoService.getSpaceInfoByUserId(Long.valueOf(userId));
        }else{
            return userInfoService.getSpaceInfoByUserId(getUserId());
        }
    }
    @RateLimiter(time = 60, count = 10)
    @GetMapping("/getJsPromptByName")
    public ResultBody getJsPromptByName(String templateName){
        return userInfoService.getJsPromptByName(templateName);
    }
    @RateLimiter(time = 60, count = 10)
    @PostMapping("/bindUserFlowId")
    public ResultBody bindUserFlowId(@RequestBody Map map){
        return userInfoService.saveUserFlowId(map);
    }

    @PostMapping("/saveAgentBind")
    public ResultBody saveAgentBind(@RequestBody Map map){
        map.put("userId",getUserId());
        return userInfoService.saveAgentBind(map);
    }
    @PostMapping("/saveSpaceBind")
    public ResultBody saveSpaceBind(@RequestBody Map map){
        map.put("userId",getUserId());
        return userInfoService.saveSpaceBind(map);
    }


    @PostMapping("/saveWcOfficeAccount")
    public ResultBody saveWcOfficeAccount(@RequestBody WcOfficeAccount wcOfficeAccount){
        if (wcOfficeAccount == null) {
            return ResultBody.error(201, "绑定失败：参数为空");
        }

        wcOfficeAccount.setUserId(getUserId());
        wcOfficeAccount.setUserName(getUsername());
        return userInfoService.saveOfficeAccount(wcOfficeAccount);
    }

    @PostMapping("/receiveKeyword")
    public ResultBody receiveKeyword(@RequestBody Map<String, String> request) {
        String keyword = request.get("keyword");
        String userid = request.get("userid");
        String corpId = request.get("corpId");
        String taskId = request.get("taskId");
        String username = request.get("username");
        try {
            userInfoService.saveChromeTaskData(taskId,userid,corpId);
            myWebSocketHandler.sendMessageToClient(userid,keyword,taskId,corpId,username);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("发送成功");
    }

    @GetMapping("/checkClentStatus")
    public ResultBody checkClentStatus(String corpId) {
        try {


           String status = myWebSocketHandler.sendMessageToClient(corpId, "heartbeat","taskId",null,null);
           return ResultBody.success(status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 将关键词发送给 WebSocket 服务
        return ResultBody.success("发送成功");
    }


    @GetMapping("/getUserPromptTem")
    public ResultBody getUserPromptTem(String userId,String agentId) {
            return userInfoService.getUserPromptTem(userId,agentId);
    }

    @GetMapping("/getPromptTem")
    public ResultBody getPromptTem(Integer type,String userId){
        return userInfoService.getPromptTem(type,userId);
    }

    @PostMapping("/updateUserPromptTem")
    public ResultBody updateUserPromptTem(@RequestBody Map map) {


        return userInfoService.updateUserPromptTem(map);
    }



    @GetMapping("/getTaskStatus")
    public ResultBody getTaskStatus(String taskId){
        return userInfoService.getTaskStatus(taskId);
    };

    @GetMapping("/getIsChangeByCorpId")
    public ResultBody getIsChangeByCorpId(String corpId){ return userInfoService.getIsChangeByCorpId(corpId);
    };

    @GetMapping("/getScorePrompt/{id}")
    public ResultBody getScorePrompt(@PathVariable Long id){
        return userInfoService.getScorePrompt(id);
    }

    @GetMapping("/getScorePromptList")
    public TableDataInfo getScorePromptList(ScorePromptQuery scorePromptQuery){
        startPage();
        List<PromptTemplate> list = userInfoService.getScorePromptList(scorePromptQuery);
        return getDataTable(list);
    }

    //获取当前用户的所有评分提示词
    @GetMapping("/getAllScorePrompt")
    public ResultBody getAllScorePrompt(){
        return userInfoService.getAllScorePrompt();
    }

    @PostMapping("/saveScorePrompt")
    public ResultBody saveScorePrompt(@RequestBody PromptTemplate promptTemplate){
        return userInfoService.saveScorePrompt(promptTemplate);
    }

    @PutMapping("/updateScorePrompt")
    public ResultBody updateScorePrompt(@RequestBody PromptTemplate promptTemplate){
        return userInfoService.updateScorePrompt(promptTemplate);
    }

    @DeleteMapping("/deleteScorePrompt")
    public ResultBody deleteScorePrompt(@RequestBody Long[] ids){
        return userInfoService.deleteScorePrompt(ids);
    }
}

