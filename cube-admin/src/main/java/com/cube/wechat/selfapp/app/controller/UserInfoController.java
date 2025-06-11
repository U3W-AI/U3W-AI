package com.cube.wechat.selfapp.app.controller;

import com.cube.common.annotation.RateLimiter;
import com.cube.common.core.controller.BaseController;
import com.cube.common.utils.StringUtils;
import com.cube.wechat.selfapp.app.config.MyWebSocketHandler;
import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.app.service.UserInfoService;
import com.cube.wechat.selfapp.app.util.CommonAssistantService;
import com.cube.wechat.selfapp.app.util.StreamAssistantService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

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
    private StreamAssistantService streamAssistantService;

    @Autowired
    private CommonAssistantService commonAssistantService;

    @Autowired
    private MyWebSocketHandler myWebSocketHandler;

    @GetMapping("/getUserCount")
    public ResultBody getUserCount(@ApiParam("用户ID") String userId){
        return userInfoService.getUserCount(userId);
    };

    @PostMapping("/getUserPointsRecord")
    public ResultBody getUserPointsRecord(@RequestBody Map map){
        return userInfoService.getUserPointsRecord(map);
    };

    @PostMapping("/getUserReportList")
    public ResultBody getUserReportList(@RequestBody Map map){
        return userInfoService.getUserReportList(map);
    };

    @PostMapping("/getReportList")
    public ResultBody getReportList(@RequestBody Map map){
        return userInfoService.getReportList(map);
    };
    @GetMapping("/getUserLike")
    public ResultBody getUserLike(String userId){
        return userInfoService.getUserLike(userId);
    };

    @GetMapping("/getReporttDeail")
    public ResultBody getReporttDeail(String id){
        return userInfoService.getReporttDeail(id);
    }

    @PostMapping("/changeResColStatus")
    public ResultBody changeResColStatus(@RequestBody Map map){
       return userInfoService.changeResColStatus(map);
    };

    @PostMapping("/saveUserBrowse")
    public ResultBody saveUserBrowse(@RequestBody Map map){
       return userInfoService.saveUserBrowse(map);
    };

    @PostMapping("/saveUserDown")
    public ResultBody saveUserDown(@RequestBody Map map){
       return userInfoService.saveUserDown(map);
    };
    @PostMapping("/saveUserShare")
    public ResultBody saveUserShare(@RequestBody Map map){
       return userInfoService.saveUserShare(map);
    };

    @GetMapping("/getSubscribe")
    public ResultBody getSubscribet(String userId){
       return userInfoService.getSubscribe(userId);
    };

    @PostMapping("/saveSubscribe")
    public ResultBody saveSubscribe(@RequestBody Map map){
     return userInfoService.saveSubscribe(map);
    };

    @GetMapping("/getResComment")
    public ResultBody getResComment(String resId){
        return userInfoService.getResComment(resId);
    };


    @PostMapping("/changeCommentStatus")
    public ResultBody changeCommentStatus(@RequestBody Map map){
       return userInfoService.changeCommentStatus(map);
    }

    @PostMapping("/saveUserComment")
    public ResultBody saveUserComment(@RequestBody Map map){
       return userInfoService.saveUserComment(map);
    }

    @GetMapping("/getUserTask")
    public ResultBody getUserTask(String userId){
       return userInfoService.getUserTask(userId);
    };

    @PostMapping("/saveAIChatHistory")
    public ResultBody saveAIChatHistory(@RequestBody AIParam aiParam){
        return userInfoService.saveAIChatHistory(aiParam);
    }

    @PostMapping("/saveAINodeLog")
    public ResultBody saveAINodeLog(@RequestBody AINodeLog AINodeLog){
        return userInfoService.saveAINodeLog(AINodeLog);
    }




    @GetMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatCompletions(AIParam aiParam) {


        if(StringUtils.isNotEmpty(aiParam.getConversationId())){
            Flux<String> flux = streamAssistantService.fetchStreamData(aiParam);
            return flux;
        }
        return null;
    }

    @GetMapping(value = "/chat/v2/completions")
    public ResultBody completionsV2(AIParam aiParam) {
        if(StringUtils.isNotEmpty(aiParam.getConversationId())){
            String res = commonAssistantService.getCommonModelRes(aiParam);
            return ResultBody.success(res);
        }
        return ResultBody.success("抱歉，我暂时无法回答这个问题");
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
    @PostMapping("/saveChromeKeyWord")
    public ResultBody saveChromeKeyWord(@RequestBody Map map){
        if(map!=null){
            map.put("promptNum",map.get("prompt").toString().length());
            map.put("answerNum",map.get("answer").toString().length());
            map.put("username",map.get("username").toString().trim());
            return userInfoService.saveChromeKeyWord(map);
        }

        return ResultBody.success("成功");
    }

    @PostMapping("/updateChromeKeyWordLink")
    public ResultBody updateChromeKeyWordLink(@RequestBody Map map){
        if(map!=null){
            map.put("username",map.get("username").toString().trim());
            if(map.get("answer")!=null && !map.get("answer").equals("")){
//                map.put("answer",map.get("answer").toString().replaceAll("<[^>]+>", "").trim());
                String str = map.get("answer").toString().replaceAll("\n", "<br>").trim();
                str =str.replace("<ol><br>","<ol>");
                str =str.replace("<li><br>","<li>");
                map.put("answer",str);
            }
           userInfoService.updateChromeKeyWordLink(map);
        }
        return ResultBody.success("");
    }

    @PostMapping("/saveChromeKeyWordLink")
    public ResultBody saveChromeKeyWordLink(@RequestBody Map map){
        if(map!=null){
            userInfoService.saveChromeKeyWordLink(map);
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


    @GetMapping("/pushAutoOffice")
    public ResultBody pushAutoOffice(String taskId,String username){
        return userInfoService.pushAutoOneOffice(taskId,username.trim());
    }
    @GetMapping("/getViewAutoOffice")
    public ResultBody getViewAutoOffice(String taskId){
        return userInfoService.pushViewAutoOffice(taskId);
    }

    @GetMapping("/getOfficeAccount")
    public ResultBody getOfficeAccount(){
        return userInfoService.getOfficeAccount(getUserId());
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



//    public static void main(String[] args) {
//
//        String content=".Render-markdown { white-space: normal; text-align: justify; } .Render-markdown > *:last-child { margin-bottom: 0!important; } .Render-markdown ol { list-style-type: decimal; } .Render-markdown ul { list-style-type: disc; } .Render-markdown ul ul { list-style-type: circle; } .Render-markdown li { list-style-type: inherit; } .Render-markdown li:not(.Render-markdown > ul > li:first-child, .Render-markdown > ol > li:first-child) { margin-top: 6px; } .Render-markdown li * { margin-bottom: 0!important; } .Render-markdown pre { white-space: pre-wrap; } 深圳游客的广州周末游记：探索羊城的古今交融 【第一天：抵达广州，古迹寻踪】 清晨，我从深圳北站搭乘高铁，仅一个多小时便踏入了广州这片热土。广州，这座历史悠久与现代繁华并存的城市，总是能以它独特的魅力吸引着我。如何根据java正则去除style标签和里面的内容";
//        String result = content.replaceAll("", "").trim();
//        System.out.println(result);
//    }
}

