package com.cube.wechat.selfapp.app.mapper;


import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import io.github.novacrypto.bip44.M;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserInfoMapper {

    Map getUserCount(String userId);

    List<Map> getUserPointsRecord(Map map);






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

    List<Map> getAllUserInfo();

    int updateUserInfo(@Param("userId") String userId,@Param("point") BigDecimal point);

    String getUserIdByUnionId(String unionId);

    String getUnionIdByUserId(String userId);
}

