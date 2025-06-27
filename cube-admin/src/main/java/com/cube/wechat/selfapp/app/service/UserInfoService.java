package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.wecom.util.ResultBody;

import java.util.List;
import java.util.Map;

public interface UserInfoService {

    /**
     * 查询个人中心统计数据
     * */
    ResultBody getUserCount(String userId);

    /**
     * 获取积分明细
     * */
    ResultBody getUserPointsRecord(Map map);

    /**
     * 获取用户收藏、下载、浏览研报列表
     * */
    ResultBody getUserReportList(Map map);

    /**
     * 获取首页研报列表
     * */
    ResultBody getReportList(Map map);

    /**
     * 获取猜你喜欢
     * */
    ResultBody getUserLike(String userId);

    /**
     * 获取研报详情
     * */
    ResultBody getReporttDeail(String id);

    /**
     * 收藏/取消收藏研报
     * */
    ResultBody changeResColStatus(Map map);

    /**
     * 保存用户浏览记录
     * */
    ResultBody saveUserBrowse(Map map);

    /**
     * 保存用户下载记录
     * */
    ResultBody saveUserDown(Map map);

    /**
     * 保存用户分享记录
     * */
    ResultBody saveUserShare(Map map);

    /**
     * 获取行业订阅列表
     * */
    ResultBody getSubscribe(String userId);

    /**
     * 保存用户订阅
     * */
    ResultBody saveSubscribe(Map map);

    /**
     * 获取用户评论
     * */
    ResultBody getResComment(String resId);

    /**
     * 评论点赞/取消点赞
     * */
    ResultBody changeCommentStatus(Map map);

    /**
     * 保存用户评论
     * */
    ResultBody saveUserComment(Map map);



    /**
     * 获取用户每日任务状态
     * */
    ResultBody getUserTask(String userId);

    ResultBody saveAIChatHistory(AIParam aiParam);

    ResultBody saveAINodeLog(AINodeLog aiNodeLog);

    /*
    * 查询用户AI历史会话列表
    * */
    ResultBody getUserChatHistoryList(String userId,String title);

    ResultBody getChatHistoryDetail(String conversationId);

    ResultBody deleteUserChatHistory(List<String> list);

    ResultBody saveChromeData(Map map);

    ResultBody saveChromeKeyWord(Map map);

    ResultBody saveChromeKeyWordLink(Map map);

    ResultBody updateChromeKeyWordLink(Map map);

    ResultBody updateChatTitle(Map map);

    ResultBody pushOffice(List<String> ids, String userName);

    ResultBody authChecker(String userName);

    ResultBody changePoint(String userId,String method);

    ResultBody pushAutoOffice(String taskId, String userName);

    ResultBody pushAutoOneOffice(Map map);

    ResultBody pushViewAutoOffice(String taskId);

    ResultBody saveOfficeAccount(WcOfficeAccount wcOfficeAccount);


    ResultBody getOfficeAccount(Long userId);
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
