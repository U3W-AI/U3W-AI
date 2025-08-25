package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.WcChromeData;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;

import java.util.List;
import java.util.Map;

public interface AIGCService {


    ResultBody getPlayWrighDrafts(WcChromeData wcChromeData);
    ResultBody getNodeLog(WcChromeData wcChromeData);


    /**
    * 保存playwright草稿
    * */
    ResultBody saveDraftContent(Map map);

    String getDraftContent(String taskId,String aiName);
    List<Map> getDraftContentList(String taskId,String aiName);


    ResultBody savePlayWrightTaskData(String taskId,String userid,String corpId);

    ResultBody saveLogInfo(Map map);

    String getUserIdByUnionId(String unionId);

}
