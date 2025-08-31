package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;

import java.util.List;
import java.util.Map;

public interface UserInfoService {
    ResultBody getOfficeAccountByUserId(String userId);

    /**
     * 查询个人中心统计数据
     * */
    ResultBody getUserCount(String userId);

    /**
     * 获取积分明细
     * */
    ResultBody getUserPointsRecord(Map map);


    ResultBody saveAIChatHistory(AIParam aiParam);

    ResultBody saveAINodeLog(AINodeLog aiNodeLog);

    /*
    * 查询用户AI历史会话列表
    * */
    ResultBody getUserChatHistoryList(String userId,String title);

    ResultBody getChatHistoryDetail(String conversationId);

    ResultBody deleteUserChatHistory(List<String> list);

    ResultBody saveChromeData(Map map);



    ResultBody updateChatTitle(Map map);

    ResultBody pushOffice(List<String> ids, String userName);

    ResultBody authChecker(String userName);

    ResultBody changePoint(String userId,String method);


    ResultBody pushAutoOneOffice(Map map);

    ResultBody pushViewAutoOffice(String taskId);

    ResultBody saveOfficeAccount(WcOfficeAccount wcOfficeAccount);


    ResultBody getAgentBind(Long userId);
    ResultBody getSpaceInfoByUserId(Long userId);

    ResultBody getJsPromptByName(String templateName);


    ResultBody saveAgentBind(Map map);
    ResultBody saveUserFlowId(Map map);

    ResultBody saveSpaceBind(Map map);



    ResultBody saveChromeTaskData(String taskId,String userid,String corpId);

    ResultBody getTaskStatus(String taskId);

    ResultBody getUserPromptTem(String userId,String taskId);

    ResultBody getPromptTem(Integer type,String userId);

    ResultBody updateUserPromptTem(Map map);

    ResultBody getIsChangeByCorpId(String corpId);



}
