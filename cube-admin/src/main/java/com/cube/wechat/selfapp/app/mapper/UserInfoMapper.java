package com.cube.wechat.selfapp.app.mapper;


import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserInfoMapper {

    Map getUserCount(String userId);

    List<Map> getUserPointsRecord(Map map);

    List<Map> getUserDownReportList(Map map);

    List<Map> getUserBrowseReportList(Map map);

    List<Map> getUserBrowseStraList(Map map);

    List<Map> getUserCollectionReportList(Map map);

    List<Map> getUserCollectionStraList(Map map);

    List<Map> getReportList(Map map);

    String getUserTag(String userId);

    List<Map> getUserLike(String tag);

    Map getReportDetail(String id);


    int saveCollection(@Param("resId") String resId, @Param("userId") String userId);

    Integer getBrowseCount(@Param("resId") String resId, @Param("userId") String userId);

    Integer getDownCount(@Param("resId") String resId, @Param("userId") String userId);

    Integer getShareCount(@Param("bizId") String bizId, @Param("userId") String userId);

    int saveBrowseRecord(@Param("resId") String resId, @Param("userId") String userId);

    int saveDownRecord(@Param("resId") String resId, @Param("userId") String userId);

    int saveShareRecord(@Param("bizId") String bizId, @Param("userId") String userId,@Param("path") String path);

    int delCollection(@Param("resId") String resId, @Param("userId") String userId);

    int addResCollectionNum(String resId);

    int delResCollectionNum(String resId);

    int addResDownNum(String resId);

    int delResDownNum(String resId);

    int addResBrowseNum(String resId);

    int addStraBrowseNum(String resId);

    int delResBrowseNum(String resId);

    List<String> getSubscribe(String userId);

    int delSubscribe(String userId);

    int saveSubscribe(@Param("userId") String userId,@Param("list")List<String> list);

    List<Map> getResComment(String resId);

    int saveUserLike(Map map);

    int delUserLike(Map map);

    int addCommentLike(String comId);

    int delCommentLike(String comId);

    int saveUserComment(Map map);

    int saveAIChatHistory(AIParam aiParam);

    int saveAINodeLog(AINodeLog aiNodeLog);

    List<String> getUserTask(String userId);


    int saveUserChat(Map map);

    int updateUserChat(Map map);

    List<Map> getUserChatHistoryList(@Param("userId") String userId,@Param("title")String title);

    String getChatHistoryDetail(String conversationId);

    int deleteUserChatHistory(String conversationId);

    int saveChromeData(Map map);

    int saveChromeKeyWord(Map map);

    int saveChromeKeyWordLink(@Param("list") List<Map> list);


    int updateHotWordStatus(String id);

    int updateChromeKeyWordLink(Map map);

    int updateChatTitle(Map map);

    List<Map> getPushOfficeData(@Param("ids") List<String> ids,@Param("userName") String userName);

    List<Map> getPushAutoOfficeData(@Param("taskId") String taskId,@Param("userName") String userName);

    List<Map> getPushViewOfficeData(@Param("taskId") String taskId);

    int updateLinkStatus(@Param("list") List<Map> list);

    WcOfficeAccount getOfficeAccountByUserId(Long user_id);


    void saveOfficeAccount(WcOfficeAccount wcOfficeAccount);

    void updateOfficeAccount(WcOfficeAccount wcOfficeAccount);

    WcOfficeAccount getOfficeAccountByUserName(String userName);

    int saveChromeTaskData(@Param("list")List<Map> list);

    List<Map> getTaskStatus(String taskId);

    String getUserPromptTem(@Param("userId") String userId,@Param("agentId") String agentId);

    List<Map> getPromptTem();

    String getUserLikeSet(String userId);

    Map getUserHotWordByTaskId(String taskId);

    String getUserPromptTemByUnionid(@Param("username") String username,@Param("taskName") String taskName);

    int updateTaskStatus(@Param("taskId") String taskId,@Param("taskName") String taskName,@Param("status") String status);

    int updateUserPromptTem(Map map);

    String getCorpIdByUserId(String userId);

    List<String> getUserIdsByCorpId(String corpId);

    Integer getIsChangeByCorpId(@Param("corpId") String corpId,@Param("currentTime") String currentTime,@Param("timeTenMinutesBefore") String timeTenMinutesBefore);


    int delTaskPromptByUserId(String userId);

    int saveAllTaskPromptByUserId(@Param("promptTemplate") String promptTemplate,@Param("userId") String userId);

    String getTaskPromptById(@Param("agentId") String agentId,@Param("userId") String userId);

    int saveTaskPromptByUserId(@Param("agentId") String agentId,@Param("promptTemplate") String promptTemplate,@Param("userId") String userId);

    int updateTaskPromptByUserId(@Param("agentId") String agentId,@Param("promptTemplate") String promptTemplate,@Param("userId") String userId);

    Map getAgentTokenByUserId(String userId);

    Map getSpaceInfoByUserId(String userId);

    Map getJsPromptByName(String templateName);

    int saveAgentBind(Map map);

    int saveSpaceBind(Map map);

    int saveUserFlowId(Map map);

    Integer getUserCountByUserName(String userName);
}

