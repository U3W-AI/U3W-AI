package com.cube.wechat.selfapp.app.service.impl;

import com.cube.common.entity.UserLogInfo;
import com.cube.wechat.selfapp.app.domain.WcChromeData;
import com.cube.wechat.selfapp.app.mapper.AIGCMapper;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import com.cube.wechat.selfapp.app.service.AIGCService;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月17日 09:20
 */
@Service
public class AIGCServiceImpl implements AIGCService {

    @Autowired
    private AIGCMapper aigcMapper;

    @Autowired
    private UserInfoMapper userInfoMapper;


    @Override
    public String getUserIdByUnionId(String unionId) {
        return userInfoMapper.getUserIdByUnionId(unionId);
    }

    @Override
    public ResultBody getPlayWrighDrafts(WcChromeData wcChromeData) {
        PageHelper.startPage(wcChromeData.getPage(),wcChromeData.getLimit());
        List<Map> list = aigcMapper.getPlayWrightDraftList(wcChromeData.getUserId(),wcChromeData.getKeyWord());
        PageInfo pageInfo = new PageInfo(list);
        List<Map> resList = pageInfo.getList();
        for (Map map : resList) {
            List<Map> draftList = aigcMapper.getPlayWrightDraftAiList(map.get("taskId")+"");
            map.put("aiResponses",draftList);
        }
        return ResultBody.success(pageInfo);
    }
    @Override
    public ResultBody getNodeLog(WcChromeData wcChromeData) {
        PageHelper.startPage(wcChromeData.getPage(),wcChromeData.getLimit());
        List<Map> list = aigcMapper.getNodeLogList();
        PageInfo pageInfo = new PageInfo(list);
        List<Map> resList = pageInfo.getList();
        for (Map map : resList) {
            List<Map> draftList = aigcMapper.getPlayWrightNodeList(map.get("question")+"");
            map.put("aiResponses",draftList);
        }
        return ResultBody.success(pageInfo);
    }





    @Override
    public ResultBody saveDraftContent(Map map) {
        aigcMapper.saveDraftContent(map);
        return ResultBody.success("保存成功");
    }

    @Override
    public ResultBody saveLogInfo(UserLogInfo userLogInfo) {
        aigcMapper.saveLogInfo(userLogInfo);
        return ResultBody.success("保存成功");
    }

    @Override
    public String getDraftContent(String taskId,String aiName) {
        return aigcMapper.getDraftContent(taskId,aiName);
    }
    @Override
    public List<Map> getDraftContentList(String taskId,String aiName) {
        return aigcMapper.getDraftContentList(taskId,aiName);
    }


    @Override
    public ResultBody savePlayWrightTaskData(String taskId,String userid,String corpId) {
        List<Map> list =new ArrayList<>();

        Map oneMap = new HashMap();
        oneMap.put("taskId",taskId);
        oneMap.put("taskName","福帮手智能体");
        oneMap.put("status","running");
        oneMap.put("planTime","1分钟");
        oneMap.put("userid",userid);
        oneMap.put("corpId",corpId);
        list.add(oneMap);

        Map twoMap = new HashMap();
        twoMap.put("taskId",taskId);
        twoMap.put("taskName","腾讯元宝");
        twoMap.put("status","running");
        twoMap.put("planTime","1分钟");
        twoMap.put("userid",userid);
        twoMap.put("corpId",corpId);
        list.add(twoMap);

        Map threeMap = new HashMap();
        threeMap.put("taskId",taskId);
        threeMap.put("taskName","秘塔AI");
        threeMap.put("status","running");
        threeMap.put("planTime","1分钟");
        threeMap.put("userid",userid);
        threeMap.put("corpId",corpId);
        list.add(threeMap);


        aigcMapper.savePlayWrightTaskData(list);
        return ResultBody.success("成功");
    }

    @Override
    public String getUnionIdByUserId(String userId) {
        return userInfoMapper.getUnionIdByUserId(userId);
    }
}
