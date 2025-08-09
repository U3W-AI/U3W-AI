package com.cube.wechat.selfapp.app.mapper;

import com.cube.wechat.selfapp.app.domain.WcChromeData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AIGCMapper {



    List<Map> getPlayWrightDraftList(@Param("userId") Long userId,@Param("keyWord") String keyWord);

    List<Map> getPlayWrightDraftAiList(String taskId);

    List<Map> getNodeLogList();

    List<Map> getPlayWrightNodeList(String userPrompt);

    int saveDraftContent(Map map);

    String getDraftContent(@Param("taskId") String taskId,@Param("aiName") String aiName);
    List<Map> getDraftContentList(@Param("taskId") String taskId,@Param("aiName") String aiName);


    int savePlayWrightTaskData(@Param("list")List<Map> list);

    Map getUserInfoByYqId(@Param("userId") String userId);

    int saveUserChatData(Map map);

    List<Map> getChatHistory(@Param("userId") String userId,@Param("isAll") int isAll);

    int saveLogInfo(Map map);

}
