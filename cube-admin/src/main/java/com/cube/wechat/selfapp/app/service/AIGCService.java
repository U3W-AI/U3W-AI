package com.cube.wechat.selfapp.app.service;

import com.cube.common.entity.UserLogInfo;
import com.cube.wechat.selfapp.app.domain.WcChromeData;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import org.apache.catalina.User;

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

    ResultBody saveLogInfo(UserLogInfo userLogInfo);

    String getUserIdByUnionId(String unionId);

    String getUnionIdByUserId(String userId);

}
