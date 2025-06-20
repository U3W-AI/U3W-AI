package com.cube.wechat.selfapp.app.mapper;

import com.cube.wechat.selfapp.app.domain.WcChromeData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AIGCMapper {


    int delLink(String id);

    List<Map> getChromeDataList(WcChromeData wcChromeData);

    List<Map> getHotKeyWordList(WcChromeData wcChromeData);

    List<Map> getPlayWrightDraftList(@Param("userId") Long userId,@Param("keyWord") String keyWord);

    List<Map> getPlayWrightDraftAiList(String taskId);

    List<Map> getNodeLogList();

    List<Map> getPlayWrightNodeList(String userPrompt);

    Map getHotKeyWordById(String id);

    List<Map> getHotKeyWordLog(String id);

    List<Map> getChromeLinkList(WcChromeData wcChromeData);

    List<String> getChromeLinkIds(WcChromeData wcChromeData);

    int delRepeatLink(@Param("username") String username,@Param("list") List<String> list);

    List<Map> getChromeLinkListFor(WcChromeData wcChromeData);

    List<Map> getChromeLinkListByTaskId(String taskId);

    List<Map> getChromeKeyWordFor(WcChromeData wcChromeData);

    Map getChromeKeyWordByTaskId(String taskId);


    int updateHotKeyWord(Map map);

    int saveHotKeyWordLog(Map map);

    int saveHotKeyWord(Map map);

    int updateArticleLink(Map map);

    int saveDraftContent(Map map);

    String getDraftContent(@Param("taskId") String taskId,@Param("aiName") String aiName);
    List<Map> getDraftContentList(@Param("taskId") String taskId,@Param("aiName") String aiName);

    void delBatchLink(List<String> list);

    int savePlayWrightTaskData(@Param("list")List<Map> list);

    Map getUserInfoByYqId(@Param("userId") String userId);

    int saveUserChatData(Map map);

    List<Map> getChatHistory(@Param("userId") String userId,@Param("isAll") int isAll);

}
