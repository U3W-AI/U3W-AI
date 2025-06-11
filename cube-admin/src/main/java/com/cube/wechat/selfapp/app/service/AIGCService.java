package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.WcChromeData;
import com.cube.wechat.selfapp.wecom.util.ResultBody;

import java.util.List;
import java.util.Map;

public interface AIGCService {

    /*
     * 查询爆文
     * */
    ResultBody getChromeDataList(WcChromeData wcChromeData);

    ResultBody getHotKeyWordList(WcChromeData wcChromeData);

    ResultBody getPlayWrighDrafts(WcChromeData wcChromeData);
    ResultBody getNodeLog(WcChromeData wcChromeData);

    ResultBody getHotKeyWordById(String id);

    ResultBody getHotKeyWordLog(String id);

    ResultBody updateHotKeyWord(Map map);

    ResultBody saveHotKeyWord(Map map);

    /*
     * 查询文章链接
     * */
    ResultBody getChromeLinkList(WcChromeData wcChromeData);

    ResultBody getChromeLinkListFor(WcChromeData wcChromeData);

    ResultBody getChromeKeyWordFor(WcChromeData wcChromeData);

    ResultBody getChromeKeyWordByTaskId(String taskId);

    ResultBody getChromeLinkListByTaskId(String taskId,String username,String taskName);

    /*
     * 删除文章链接
     * */
    ResultBody delLink(Map map);

    /**
     * 修改文章链接内容
     * @param map
     * @return
     */
    ResultBody updateArticleLink(Map map);

    /**
     * 批量删除文章链接内容
     * @param list
     * @return
     */
    ResultBody delBatchLink(List<String> list);

    /**
    * 保存playwright草稿
    * */
    ResultBody saveDraftContent(Map map);

    String getDraftContent(String taskId,String aiName);
    List<Map> getDraftContentList(String taskId,String aiName);


    ResultBody savePlayWrightTaskData(String taskId,String userid,String corpId);
}
