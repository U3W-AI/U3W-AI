package com.cube.wechat.selfapp.app.service.impl;

import com.cube.wechat.selfapp.app.domain.WcChromeData;
import com.cube.wechat.selfapp.app.mapper.AIGCMapper;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import com.cube.wechat.selfapp.app.service.AIGCService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
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


    /*
     * 删除链接
     * */
    @Override
    public ResultBody delLink(Map map) {
        aigcMapper.delLink(map.get("id")+"");

        return ResultBody.success("删除成功");
    }

    @Override
    public ResultBody getChromeDataList(WcChromeData wcChromeData) {
        PageHelper.startPage(wcChromeData.getPage(),wcChromeData.getLimit());
        List<Map> list = aigcMapper.getChromeDataList(wcChromeData);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }

    @Override
    public ResultBody getHotKeyWordList(WcChromeData wcChromeData) {
        PageHelper.startPage(wcChromeData.getPage(),wcChromeData.getLimit());
        List<Map> list = aigcMapper.getHotKeyWordList(wcChromeData);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
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
    public ResultBody getHotKeyWordById(String id) {
        Map map = aigcMapper.getHotKeyWordById(id);
        return ResultBody.success(map);
    }
    @Override
    public ResultBody getHotKeyWordLog(String  id) {
        List<Map> list = aigcMapper.getHotKeyWordLog(id);
        return ResultBody.success(list);
    }

    @Override
    public ResultBody updateHotKeyWord(Map map){
        //修改数据
        aigcMapper.updateHotKeyWord(map);
        //保存日志
        aigcMapper.saveHotKeyWordLog(map);
        return ResultBody.success("修改成功");
    }
    @Override
    public ResultBody saveHotKeyWord(Map map){
        //保存
        aigcMapper.saveHotKeyWord(map);
        return ResultBody.success("修改成功");
    }


    @Override
    public ResultBody getChromeLinkList(WcChromeData wcChromeData) {
        int page = wcChromeData.getPage();
        if (wcChromeData.getId() != null){
            wcChromeData.setPage(1);
        }
        PageHelper.startPage(wcChromeData.getPage(),wcChromeData.getLimit());
        List<Map> list = aigcMapper.getChromeLinkList(wcChromeData);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }
    @Override
    public ResultBody getChromeLinkListFor(WcChromeData wcChromeData) {
        List<Map> list = aigcMapper.getChromeLinkListFor(wcChromeData);
        if(wcChromeData.getSelVal() == 4){
            // 删除当前登录人重复的
          List<String> ids = aigcMapper.getChromeLinkIds(wcChromeData);
          if(ids.size()>0){
              aigcMapper.delRepeatLink(wcChromeData.getUsername(),ids);
          }
        }


        return ResultBody.success(list);
    }
    @Override
    public ResultBody getChromeLinkListByTaskId(String taskId,String username,String taskName) {
        List<Map> list = aigcMapper.getChromeLinkListByTaskId(taskId);

        String promptTem = userInfoMapper.getUserPromptTemByUnionid(username,taskName);

        userInfoMapper.updateTaskStatus(taskId,"素材搜集","success");
        userInfoMapper.updateTaskStatus(taskId,"内容生成","running");

        Map keyword = userInfoMapper.getUserHotWordByTaskId(taskId);

        Map map = new HashMap();
        map.put("list",list);
        map.put("promptTem",promptTem);
        map.put("theme",keyword.get("prompt"));
        map.put("keyword",keyword.get("answer"));
        return ResultBody.success(map);
    }
    @Override
    public ResultBody getChromeKeyWordFor(WcChromeData wcChromeData) {
        List<Map> resList = new ArrayList<>();
        List<Map> list = aigcMapper.getChromeKeyWordFor(wcChromeData);
        for (Map map : list) {

            String wordStr = map.get("hotword").toString().replace("，",",");
            String[] words = wordStr.split(",");
            for (String word : words) {
                Map resMap = new HashMap();
                resMap.put("id",map.get("id"));
                resMap.put("answer",word);
                resList.add(resMap);
            }

        }
        return ResultBody.success(resList);
    }
    @Override
    public ResultBody getChromeKeyWordByTaskId(String taskId) {
        List<Map> resList = new ArrayList<>();
        Map map = aigcMapper.getChromeKeyWordByTaskId(taskId);
            String wordStr = map.get("hotword").toString().replace("，",",");
            String[] words = wordStr.split(",");
            for (String word : words) {
                Map resMap = new HashMap();
                resMap.put("id",map.get("id"));
                resMap.put("answer",word);
                resList.add(resMap);
            }
        return ResultBody.success(resList);
    }

    @Override
    public ResultBody updateArticleLink(Map map) {
        aigcMapper.updateArticleLink(map);
        return ResultBody.success("修改成功");
    }


    @Override
    public ResultBody delBatchLink(List<String> list) {
        aigcMapper.delBatchLink(list);
        return ResultBody.success("删除成功");
    }

    @Override
    public ResultBody saveDraftContent(Map map) {
        aigcMapper.saveDraftContent(map);
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
}
