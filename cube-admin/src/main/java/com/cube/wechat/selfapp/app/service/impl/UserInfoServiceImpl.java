package com.cube.wechat.selfapp.app.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.utils.StringUtils;
import com.cube.point.controller.PointsSystem;
import com.cube.wechat.selfapp.app.domain.AINodeLog;
import com.cube.wechat.selfapp.app.domain.AIParam;
import com.cube.wechat.selfapp.app.domain.WcOfficeAccount;
import com.cube.wechat.selfapp.app.mapper.UserInfoMapper;
import com.cube.wechat.selfapp.app.service.UserInfoService;
import com.cube.wechat.selfapp.app.util.RestUtils;
import com.cube.wechat.selfapp.wecom.util.RedisUtil;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import com.cube.wechat.selfapp.wecom.util.WeChatApiUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月23日 10:28
 */
@Service
public class UserInfoServiceImpl implements UserInfoService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private PointsSystem pointsSystem;

    @Autowired
    private WeChatApiUtils weChatApiUtils;


    @Autowired
    private RedisUtil redisUtil;

    /**
     * 查询个人中心统计数据
     * */
    @Override
    public ResultBody getUserCount(String userId) {
        return ResultBody.success(userInfoMapper.getUserCount(userId));
    }

    /**
     * 获取积分明细
     * */
    @Override
    public ResultBody getUserPointsRecord(Map map) {
        PageHelper.startPage((int)map.get("pageIndex"),(int)map.get("pageSize"));
        List<Map> list = userInfoMapper.getUserPointsRecord(map);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }

    /**
     * 获取用户收藏、下载、浏览研报列表
     * */
    @Override
    public ResultBody getUserReportList(Map map) {
        PageHelper.startPage((int)map.get("pageIndex"),(int)map.get("pageSize"));
        List<Map> list = new ArrayList<>();
        if(map.get("type").equals(1) || map.get("type").equals("1")){
            // 查询我的下载
            list = userInfoMapper.getUserDownReportList(map);

        }else if(map.get("type").equals(2) || map.get("type").equals("2")){
            // 查询我的浏览
            if(map.get("contentType").equals(1) || map.get("contentType").equals("1")){
                list = userInfoMapper.getUserBrowseReportList(map);
            }else if(map.get("contentType").equals(2) || map.get("contentType").equals("2")){
                list = userInfoMapper.getUserBrowseStraList(map);
            }

        }else if(map.get("type").equals(3) || map.get("type").equals("3")){
            //查询我的收藏
            if(map.get("contentType").equals(1) || map.get("contentType").equals("1")){
                list = userInfoMapper.getUserCollectionReportList(map);
            }else if(map.get("contentType").equals(2) || map.get("contentType").equals("2")){
                list = userInfoMapper.getUserCollectionStraList(map);
            }

        }
        PageInfo pageInfo = new PageInfo(list);

        return ResultBody.success(pageInfo);
    }

    /**
     * 获取首页研报列表
     * */
    @Override
    public ResultBody getReportList(Map map) {
        PageHelper.startPage((int)map.get("pageIndex"),(int)map.get("pageSize"));
        List<Map> list =  userInfoMapper.getReportList(map);
        PageInfo pageInfo = new PageInfo(list);
        Integer isFirst = pointsSystem.checkPointIsOk("每日首次登录",String.valueOf(map.get("userId")),1);
        if(isFirst==0){
            pointsSystem.setUserPoint(String.valueOf(map.get("userId")),"每日首次登录",null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        }
        return ResultBody.success(pageInfo);
    }

    /**
     * 获取猜你喜欢
     * */
    @Override
    public ResultBody getUserLike(String userId) {
        String tag = userInfoMapper.getUserTag(userId);


        StringBuilder tagParamBuilder = new StringBuilder();
        if (tag != null) {
            String[] tagsArray = tag.split(",");
            List<String> tagsList = Arrays.asList(tagsArray);
            for (int i = 0; i < tagsList.size(); i++) {
                String s = tagsList.get(i);
                tagParamBuilder.append("tag like '%").append(s).append("%' or ");
                tagParamBuilder.append("industry like '%").append(s).append("%' or ");
                if (i == tagsList.size() - 1) {
                    tagParamBuilder.delete(tagParamBuilder.length() - 4, tagParamBuilder.length());
                }
            }
        } else {
            tagParamBuilder.append("industry like '%科技传媒%' or industry like '%大消费%' ");
        }

        String tagParam = tagParamBuilder.toString();

        List<Map> list =  userInfoMapper.getUserLike(tagParam);
        return ResultBody.success(list);
    }

    /**
     * 获取研报详情
     * */
    @Override
    public ResultBody getReporttDeail(String id) {
        return ResultBody.success(userInfoMapper.getReportDetail(id));
    }

    /**
     * 收藏/取消收藏研报
     * */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultBody changeResColStatus(Map map) {
        if(map.get("userId")!=null){
            if(map.get("isCol").equals("true") || map.get("isCol").equals(true) || map.get("isCol").equals(1) || map.get("isCol").equals("1") ){
                userInfoMapper.saveCollection(map.get("id")+"",map.get("userId")+"");
//                userInfoMapper.delResCollectionNum(map.get("id")+"");
            }else if(map.get("isCol").equals("false") || map.get("isCol").equals(false) || map.get("isCol").equals(0) || map.get("isCol").equals("0") ){
                userInfoMapper.delCollection(map.get("id")+"",map.get("userId")+"");
//                userInfoMapper.addResCollectionNum(map.get("id")+"");
            }
            return ResultBody.success("收藏成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 保存用户浏览记录
     * */
    @Override
    public ResultBody saveUserBrowse(Map map) {
        if(map.get("userId")!=null){
            Integer num = userInfoMapper.getBrowseCount(map.get("id")+"",map.get("userId")+"");
            if( num == 0 ){
                userInfoMapper.saveBrowseRecord(map.get("id")+"",map.get("userId")+"");
            }
            return ResultBody.success("浏览成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 保存用户下载记录
     * */
    @Override
    public ResultBody saveUserDown(Map map) {
        if(map.get("userId")!=null) {
            Integer num = userInfoMapper.getDownCount(map.get("id") + "", map.get("userId") + "");
            if (num == 0 && map.get("userId") != null) {
                userInfoMapper.saveDownRecord(map.get("id") + "", map.get("userId") + "");
                userInfoMapper.addResDownNum(map.get("id") + "");
                pointsSystem.setUserPoint(String.valueOf(map.get("userId")), "下载干货", null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
            }
            return ResultBody.success("下载添加记录成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 保存用户分享记录
     * */
    @Override
    public ResultBody saveUserShare(Map map) {
        if(map.get("userId")!=null) {
            Integer num = userInfoMapper.getShareCount(map.get("bizId") + "", map.get("userId") + "");
            if (num == 0 && map.get("userId") != null) {
                userInfoMapper.saveShareRecord(map.get("bizId") + "", map.get("userId") + "", map.get("path") + "");
                Integer isFirst = pointsSystem.checkPointIsOk("每日首次分享", String.valueOf(map.get("userId")), 1);
                if (isFirst == 0) {
                    pointsSystem.setUserPoint(String.valueOf(map.get("userId")), "每日首次分享", null,"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
                    return ResultBody.success("分享成功，今日积分已到账！");
                }
            }
            return ResultBody.success("添加分享成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 获取行业订阅列表
     * */
    @Override
    public ResultBody getSubscribe(String userId) {
        Map map = new HashMap();
        map.put("sub",userInfoMapper.getSubscribe(userId));
        map.put("point", pointsSystem.getPointRule("行业订阅"));
        return ResultBody.success(map);
    }

    /**
     * 保存用户订阅
     * */
    @Override
    public ResultBody saveSubscribe(Map map) {
        if(map.get("userId")!=null){
            List<String> list = (List<String>) map.get("selectedIndustries");
            userInfoMapper.delSubscribe(map.get("userId")+"");
            if(list.size()>0){
                userInfoMapper.saveSubscribe(map.get("userId")+"", list);
            }
            pointsSystem.setUserPoint(map.get("userId")+"","行业订阅",Integer.parseInt(map.get("changeAmount")+""),"0x2edc4228a84d672affe8a594033cb84a029bcafc","f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
            return ResultBody.success("订阅成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 获取用户评论
     * */
    @Override
    public ResultBody getResComment(String resId) {
        return ResultBody.success(userInfoMapper.getResComment(resId));
    }

    /**
     * 评论点赞/取消点赞
     * */
    @Override
    public ResultBody changeCommentStatus(Map map) {
        if(map.get("creator") != null){
            if(map.get("isLike").equals(1) || map.get("isLike").equals("1") ){
                //取消点赞
                userInfoMapper.addCommentLike(map.get("comId")+"");
                userInfoMapper.saveUserLike(map);
            }else{
                userInfoMapper.delCommentLike(map.get("comId")+"");
                userInfoMapper.delUserLike(map);
            }
            return ResultBody.success("点赞成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 保存用户评论
     * */
    @Override
    public ResultBody saveUserComment(Map map) {
        if(map.get("userId")!=null){
            userInfoMapper.saveUserComment(map);
            return ResultBody.success("评论成功");
        }else{
            return ResultBody.error(401,"用户ID为空");
        }
    }

    /**
     * 获取用户每日任务状态
     * */
    @Override
    public ResultBody getUserTask(String userId) {
        List<Map> taskList = pointsSystem.getPointTask();
        List<String> finishedTask = userInfoMapper.getUserTask(userId);
        taskList.forEach(task -> {
            String taskName = (String) task.get("taskName");
            String finshStatus = finishedTask.contains(taskName) ? "已完成" : "去完成";
            task.put("finshStatus", finshStatus);
        });

        return ResultBody.success(taskList);
    }

    @Override
    public ResultBody saveAIChatHistory(AIParam aiParam) {
        userInfoMapper.saveAIChatHistory(aiParam);
        return ResultBody.success("保存成功");
    }
    @Override
    public ResultBody saveAINodeLog(AINodeLog aiNodeLog) {
        userInfoMapper.saveAINodeLog(aiNodeLog);
        return ResultBody.success("保存成功");
    }


    @Override
    public ResultBody getUserChatHistoryList(String userId,String title) {
        return ResultBody.success(userInfoMapper.getUserChatHistoryList(userId,title));
    }
    @Override
    public ResultBody getChatHistoryDetail(String conversationId) {
        String chatHisroty = userInfoMapper.getChatHistoryDetail(conversationId);
        JSONArray contentArray = JSONArray.parseArray(chatHisroty);
        return ResultBody.success(contentArray);
    }

    @Override
    public ResultBody deleteUserChatHistory(List<String> list){
        for (String s : list) {
            redisUtil.delete("dudu."+s);
            userInfoMapper.deleteUserChatHistory(s);
        }
        return ResultBody.success("删除成功");
    }

    @Override
    public ResultBody saveChromeData(Map map){
         userInfoMapper.saveChromeData(map);
        return ResultBody.success("成功");
    }
    @Override
    public ResultBody saveChromeKeyWord(Map map){
//         userInfoMapper.saveChromeKeyWord(map);
//         userInfoMapper.updateTaskStatus(map.get("id")+"","主题生成","success");
//         userInfoMapper.updateTaskStatus(map.get("id")+"","素材搜集","running");
//
//
//        Map promptTem = userInfoMapper.getUserPromptTem(map.get("username")+"",null);
//
//        if(map.get("id") != null){
//            Map prompt = userInfoMapper.getUserHotWordByTaskId(map.get("id")+"");
//            promptTem.put("theme",prompt.get("prompt"));
//            promptTem.put("keyword",prompt.get("answer"));
//        }

        return ResultBody.success(null);
    }

    @Override
    public ResultBody saveChromeKeyWordLink(Map map){
        List<Map> list = new ArrayList<>();
        List<Map> answerlist = (List<Map>) map.get("answer");
        for (Map s : answerlist) {
            Map resMap = new HashMap();
            resMap.put("answer",s.get("url"));
            resMap.put("author",s.get("name"));
            resMap.put("title",s.get("title"));
            resMap.put("answerNum",s.get("url").toString().length());
            resMap.put("promptNum",map.get("prompt").toString().length());
            resMap.put("username",map.get("username").toString().trim());
            resMap.put("id",map.get("id"));
            resMap.put("prompt",map.get("prompt"));
            resMap.put("userPrompt",map.get("userPrompt"));
            resMap.put("name",map.get("name"));
            resMap.put("text",map.get("text"));
            list.add(resMap);
        }
        if(!list.isEmpty()){
            userInfoMapper.saveChromeKeyWordLink(list);
            userInfoMapper.updateHotWordStatus(map.get("id")+"");
        }
        return ResultBody.success("成功");
    }
    @Override
    public ResultBody updateChromeKeyWordLink(Map map){
        userInfoMapper.updateChromeKeyWordLink(map);
        return ResultBody.success("成功");
    }
    @Override
    public ResultBody updateChatTitle(Map map){
         userInfoMapper.updateChatTitle(map);
        return ResultBody.success("成功");
    }

    @Override
    public ResultBody pushOffice(List<String> ids, String userName){
        WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserName(userName);
        List<Map> list = userInfoMapper.getPushOfficeData(ids,userName);
        if(list.isEmpty()){
            return ResultBody.error(300,"暂无素材可被收录");
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = today.format(formatter); // 格式化日期

        String title =woa.getOfficeAccountName()+"今日速递-"+formattedDate;
//        使用 Map 聚合数据
        Map<String, List<String>> groupedData = new LinkedHashMap<>();
        for (Map map : list) {
            String linkUrl = map.get("answer")+"";
           String keyWord = map.get("prompt")+"";
            String author = map.get("author")+"";
           String liTitle = "<a href ='"+linkUrl+"' style='color:#576b95' >"+author+"："+map.get("title")+"</a>";
           String str = map.get("summary").toString().replace("；;","；");
            str = str.replace(";","；");
           String[] summarys = str.split("；");
           String summary = "<p style='font-family: 'Arial''>";
           int i = 1;
            for (String s : summarys) {

                if(i==1){
                    summary = summary+i+". "+s+"。<br><br>";
                }else if(i==2){
                    summary = summary+i+"."+s+"。<br><br>";
                }else{
                    summary = summary+i+"."+s+"<br><br>";
                }
                i++;
            }
            summary = summary+"</p>";
           String content = liTitle+"<br><br>"+summary+"<br><br>";
           groupedData.computeIfAbsent(keyWord, k -> new ArrayList<>()).add(content);
        }

        // 输出结果
        String res = "";
        for (Map.Entry<String, List<String>> entry : groupedData.entrySet()) {
            String keyWord = "<p style='font-weight: normal;color:red;'>"+entry.getKey()+"</p>";
            String content = String.join("", entry.getValue());
            res =res + keyWord+"<br>"+content;
        }
//        V4PNB2XjrprWdg1sJxs7jpoxWs9YhZy8zYH38cbZSl3JzYw_liIxBesx7PuQ7-jV

        System.out.println(res);
        //String assessToken = weChatApiUtils.getOfficeAccessToken("wx4461361a058d608b","8e97c88d040ac7248f5f6240e578a1f3");
        String assessToken = weChatApiUtils.getOfficeAccessToken(woa.getAppId(),woa.getAppSecret());
        String url = "https://api.weixin.qq.com/cgi-bin/draft/add?access_token="+assessToken;

        List<JSONObject> paramlist = new ArrayList<>();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title",title);
        jsonObject.put("author",woa.getOfficeAccountName());
        jsonObject.put("content",res);
        //jsonObject.put("thumb_media_id","V4PNB2XjrprWdg1sJxs7jpoxWs9YhZy8zYH38cbZSl3JzYw_liIxBesx7PuQ7-jV");
        jsonObject.put("thumb_media_id",woa.getMediaId());
        paramlist.add(jsonObject);
        JSONObject param = new JSONObject();
        param.put("articles",paramlist);
        try {
            RestUtils.post(url, param);
        }catch (Exception e){

        }
        userInfoMapper.updateLinkStatus(list);
        return ResultBody.success("上传成功！");
    }
    public ResultBody authChecker(String userName){
       Integer num = userInfoMapper.getUserCountByUserName(userName);
       if(num > 0) {
           return ResultBody.success("上传成功！");
       }
        return ResultBody.error(404,"用户不存在");
    }
    public ResultBody changePoint(String userId,String method){
        Integer points = pointsSystem.getUserPoints(userId);
        if(points < 1){
            return ResultBody.error(201,"积分余额不足，请明日再来或者联系客服充值");
        }
        pointsSystem.setUserPoint(userId,method,null,"0x3f4413a0e863903147172b1e7672d7a23025e084","824af41abf2ca18335f5547ae293a4e250ed7e80a78f985fd01d551e0a0d3552");
        return ResultBody.success("执行成功！");
    }
    @Override
    public ResultBody pushAutoOffice(String taskId, String userName){
        userInfoMapper.updateTaskStatus(taskId,"内容生成","success");
        userInfoMapper.updateTaskStatus(taskId,"发布预览","running");


        WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserName(userName);
        List<Map> list = userInfoMapper.getPushAutoOfficeData(taskId,userName);
        if(list.size()==0){
            return ResultBody.error(300,"暂无素材可被收录");
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = today.format(formatter); // 格式化日期

        String title =woa.getOfficeAccountName()+"今日速递-"+formattedDate;
//        使用 Map 聚合数据
        Map<String, List<String>> groupedData = new LinkedHashMap<>();
        for (Map map : list) {
            String linkUrl = map.get("answer")+"";
           String keyWord = map.get("prompt")+"";
            String author = map.get("author")+"";
            String summary = map.get("summary").toString();
           String liTitle = "<a href ='"+linkUrl+"' style='color:#576b95' >"+author+"："+map.get("title")+"</a>";
            summary = "<p style='font-family: 'Arial''>"+summary;
            summary = summary+"</p>";
           String content = liTitle+"<br><br>"+summary+"<br><br>";
           groupedData.computeIfAbsent(keyWord, k -> new ArrayList<>()).add(content);
        }

        // 输出结果
        String res = "";
        for (Map.Entry<String, List<String>> entry : groupedData.entrySet()) {
            String keyWord = "<p style='font-weight: normal;color:red;'>"+entry.getKey()+"</p>";
            String content = String.join("", entry.getValue());
            res =res + keyWord+"<br>"+content;
        }

        System.out.println(res);
        String assessToken = weChatApiUtils.getOfficeAccessToken(woa.getAppId(),woa.getAppSecret());
        String url = "https://api.weixin.qq.com/cgi-bin/draft/add?access_token="+assessToken;

        List<JSONObject> paramlist = new ArrayList<>();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title",title);
        jsonObject.put("author",woa.getOfficeAccountName());
        jsonObject.put("content",res);
        //jsonObject.put("thumb_media_id","V4PNB2XjrprWdg1sJxs7jpoxWs9YhZy8zYH38cbZSl3JzYw_liIxBesx7PuQ7-jV");
        jsonObject.put("thumb_media_id",woa.getMediaId());
        paramlist.add(jsonObject);
        JSONObject param = new JSONObject();
        param.put("articles",paramlist);
        try {
            RestUtils.post(url, param);
        }catch (Exception e){

        }
        userInfoMapper.updateLinkStatus(list);

        userInfoMapper.updateTaskStatus(taskId,"发布预览","success");
        return ResultBody.success("上传成功！");
    }
    @Override
    public ResultBody pushAutoOneOffice(String taskId, String userName){
        userInfoMapper.updateTaskStatus(taskId,"内容生成","success");
        userInfoMapper.updateTaskStatus(taskId,"发布预览","running");


        WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserName(userName);
        List<Map> list = userInfoMapper.getPushAutoOfficeData(taskId,userName);
        if(list.size()==0){
            return ResultBody.error(300,"暂无素材可被收录");
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = today.format(formatter); // 格式化日期


//        使用 Map 聚合数据

        int i =1;
        for (Map map : list) {

            String oldtitle = map.get("title")+"";
            String oldlinkUrl = map.get("answer")+"";
            String oldkeyWord = map.get("prompt")+"";
            String oldauthor = map.get("author")+"";
            String oldsummary = map.get("summary").toString();

            String title ="快讯："+oldtitle+"-草稿-"+formattedDate+"-"+i;

            String liTitle = "<a href ='"+oldlinkUrl+"' style='color:#576b95' >"+oldauthor+"："+map.get("title")+"</a>";
            oldsummary = "<p style='font-family: 'Arial''>"+oldsummary;
            oldsummary = oldsummary+"</p>";
            String oldcontent = liTitle+"<br><br>"+oldsummary+"<br><br>";
            String res = "";
                String keyWord = "<p style='font-weight: normal;color:red;'>"+oldkeyWord+"</p>";
                String content = String.join("", oldcontent);
                res =res + keyWord+"<br>"+content;

            System.out.println(res);
            String assessToken = weChatApiUtils.getOfficeAccessToken(woa.getAppId(),woa.getAppSecret());
            String url = "https://api.weixin.qq.com/cgi-bin/draft/add?access_token="+assessToken;

            List<JSONObject> paramlist = new ArrayList<>();
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("title",title);
            jsonObject.put("author",woa.getOfficeAccountName());
            jsonObject.put("content",res);
            //jsonObject.put("thumb_media_id","V4PNB2XjrprWdg1sJxs7jpoxWs9YhZy8zYH38cbZSl3JzYw_liIxBesx7PuQ7-jV");
            jsonObject.put("thumb_media_id",woa.getMediaId());
            paramlist.add(jsonObject);
            JSONObject param = new JSONObject();
            param.put("articles",paramlist);
            try {
                RestUtils.post(url, param);
            }catch (Exception e){

            }
            i++;
        }

        // 输出结果
        userInfoMapper.updateLinkStatus(list);
        userInfoMapper.updateTaskStatus(taskId,"发布预览","success");
        return ResultBody.success("上传成功！");
    }

    @Override
    public ResultBody pushViewAutoOffice(String taskId){


        List<Map> list = userInfoMapper.getPushViewOfficeData(taskId);
        if(list.size()==0){
            return ResultBody.error(300,"暂无素材可被收录");
        }

//        使用 Map 聚合数据
        Map<String, List<String>> groupedData = new LinkedHashMap<>();
        for (Map map : list) {
            String linkUrl = map.get("answer")+"";
           String keyWord = map.get("prompt")+"";
            String author = map.get("author")+"";
            String summary = map.get("summary").toString();
           String liTitle = "<a href ='"+linkUrl+"' style='color:#576b95' >"+author+"："+map.get("title")+"</a>";
            summary = "<p style='font-family: 'Arial''>"+summary;
            summary = summary+"</p>";
           String content = liTitle+"<br><br>"+summary+"<br><br>";
           groupedData.computeIfAbsent(keyWord, k -> new ArrayList<>()).add(content);
        }
        // 输出结果
        String res = "";
        for (Map.Entry<String, List<String>> entry : groupedData.entrySet()) {
            String keyWord = "<p style='font-weight: normal;color:red;'>"+entry.getKey()+"</p>";
            String content = String.join("", entry.getValue());
            res =res + keyWord+"<br>"+content;
        }
        System.out.println(res);
        return ResultBody.success(res);
    }

    private void downloadFile(String fileUrl, Path targetPath) throws IOException {
//        fileUrl = "https://u3w.com/chatfile/logo.jpg";
        URL url = new URL(fileUrl);
        try (InputStream in = url.openStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String uploadImageToWeChat(Path file, WcOfficeAccount woa) throws IOException, InterruptedException {
        String accessToken = weChatApiUtils.getOfficeAccessToken(woa.getAppId(), woa.getAppSecret());
        //String accessToken = "87_EubpBd6h4bK2WJZ7ylMiGyBKgI0I-yAVIDmbmhY8-5fSPiEyLj35ki7hPtLchmJEqFyQ_zUhHqkQFZwipddCAsr4gRP-MOUHWhaoRaMTUrTNFoE66Gs71iT-ZRMFEQbAAAIHT";
        String uploadUrl = "https://api.weixin.qq.com/cgi-bin/material/add_material?access_token=" + accessToken + "&type=image";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "curl", uploadUrl, "-F", "media=@" + file.toAbsolutePath()
        );

        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        int exitCode = process.waitFor();
        if (exitCode == 0) {
            JSONObject jsonResponse = JSONObject.parseObject(response.toString());
            return jsonResponse.getString("media_id");
        } else {
            throw new IOException("上传失败: " + response.toString());
        }
    }
    @Override
    public ResultBody saveOfficeAccount(WcOfficeAccount wcOfficeAccount) {
        // 验证输入字段的长度和格式
        if (!isValidInput(wcOfficeAccount.getAppId(), wcOfficeAccount.getAppSecret(), wcOfficeAccount.getOfficeAccountName())) {
            return ResultBody.error(201, "绑定失败：参数不能为空");
        }
        try {
            if (wcOfficeAccount.getPicUrl() != null){
                // 1. 下载图片到本地临时文件
                Path tempFile = Files.createTempFile("temp", ".jpg");
                downloadFile(wcOfficeAccount.getPicUrl(), tempFile);

                // 2. 上传图片到微信服务器
                String mediaId = uploadImageToWeChat(tempFile, wcOfficeAccount);
                wcOfficeAccount.setMediaId(mediaId);
                // 3. 删除临时文件
                Files.deleteIfExists(tempFile);
            }
        } catch (Exception e) {
            return ResultBody.error(500, "accessToken过期或无效，请检查appId和appSecret");
        }

        try {
            // 先查询用户是否绑定公众号
            WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserId(wcOfficeAccount.getUserId());
            if (woa != null) {
                // 修改绑定的公众号
                wcOfficeAccount.setId(woa.getId());
                userInfoMapper.updateOfficeAccount(wcOfficeAccount);
            } else {
                // 添加操作
                userInfoMapper.saveOfficeAccount(wcOfficeAccount);
            }
            return ResultBody.success("绑定成功！");
        } catch (Exception e) {
            return ResultBody.error(500, "系统内部错误");
        }
    }

    private boolean isValidInput(String appId, String appSecret, String officeAccountName) {
        if (StringUtils.isEmpty(appId) || StringUtils.isEmpty(appSecret) || StringUtils.isEmpty(officeAccountName)) {
            return false;
        }
        // 验证
        return true;
    }

    @Override
    public ResultBody getOfficeAccount(Long userId) {
        WcOfficeAccount woc = userInfoMapper.getOfficeAccountByUserId(userId);
        return ResultBody.success(woc);
    }
    @Override
    public ResultBody getAgentBind(Long userId) {
        Map woc = userInfoMapper.getAgentTokenByUserId(userId+"");
        return ResultBody.success(woc);
    }
    @Override
    public ResultBody getSpaceInfoByUserId(Long userId) {
        Map woc = userInfoMapper.getSpaceInfoByUserId(userId+"");
        return ResultBody.success(woc);
    }

    @Override
    public ResultBody getJsPromptByName(String templateName) {
        Map woc = userInfoMapper.getJsPromptByName(templateName);
        return ResultBody.success(woc);
    }
    @Override
    public ResultBody saveAgentBind(Map map) {
        userInfoMapper.saveAgentBind(map);
        return ResultBody.success("成功");
    }
    @Override
    public ResultBody saveUserFlowId(Map map) {
        userInfoMapper.saveUserFlowId(map);
        return ResultBody.success("成功");
    }
    @Override
    public ResultBody saveSpaceBind(Map map) {
        userInfoMapper.saveSpaceBind(map);
        return ResultBody.success("成功");
    }
    @Override
    public ResultBody saveChromeTaskData(String taskId,String userid,String corpId) {
        List<Map> list =new ArrayList<>();

        Map oneMap = new HashMap();
        oneMap.put("taskId",taskId);
        oneMap.put("taskName","主题生成");
        oneMap.put("status","running");
        oneMap.put("planTime","40秒");
        oneMap.put("userid",userid);
        oneMap.put("corpId",corpId);
        list.add(oneMap);

        Map twoMap = new HashMap();
        twoMap.put("taskId",taskId);
        twoMap.put("taskName","素材搜集");
        twoMap.put("status","waiting");
        twoMap.put("planTime","3分钟");
        twoMap.put("userid",userid);
        twoMap.put("corpId",corpId);
        list.add(twoMap);

        Map threeMap = new HashMap();
        threeMap.put("taskId",taskId);
        threeMap.put("taskName","内容生成");
        threeMap.put("status","waiting");
        threeMap.put("planTime","5分钟");
        threeMap.put("userid",userid);
        threeMap.put("corpId",corpId);
        list.add(threeMap);

        Map fourMap = new HashMap();
        fourMap.put("taskId",taskId);
        fourMap.put("taskName","发布预览");
        fourMap.put("status","waiting");
        fourMap.put("planTime","1分钟");
        fourMap.put("userid",userid);
        fourMap.put("corpId",corpId);
        list.add(fourMap);


        userInfoMapper.saveChromeTaskData(list);
        return ResultBody.success("成功");
    }

    @Override
    public ResultBody getTaskStatus(String taskId){
      List<Map> list = userInfoMapper.getTaskStatus(taskId);
        return ResultBody.success(list);
    }
    @Override
    public ResultBody getUserPromptTem(String userId,String agentId){
      String promptTem = userInfoMapper.getUserPromptTem(userId,agentId);
        return ResultBody.success(promptTem);
    }
    @Override
    public ResultBody getPromptTem(Integer type,String userId){
        if(type == 1){
            List<Map> list = userInfoMapper.getPromptTem();
            return ResultBody.success(list);
        }else{
            String prompt = userInfoMapper.getTaskPromptById("desc",userId);
            return ResultBody.success(prompt);
        }
    }
    @Override
    public ResultBody updateUserPromptTem(Map map){

        Integer points = pointsSystem.getUserPoints(map.get("userId")+"");
        if(points < 1){
            return ResultBody.error(201,"积分余额不足，请明日再来或者联系客服充值");
        }

        if(map.get("agentId").equals("desc")){
            pointsSystem.setUserPoint(map.get("userId")+"","记忆修改",null,"0x3f4413a0e863903147172b1e7672d7a23025e084","824af41abf2ca18335f5547ae293a4e250ed7e80a78f985fd01d551e0a0d3552");
        }else{
            pointsSystem.setUserPoint(map.get("userId")+"","模板配置",null,"0x3f4413a0e863903147172b1e7672d7a23025e084","824af41abf2ca18335f5547ae293a4e250ed7e80a78f985fd01d551e0a0d3552");
        }

        if(map.get("isAllSel").equals(true)){
                //全选状态，直接全删全增
                userInfoMapper.delTaskPromptByUserId(map.get("userId")+"");
                userInfoMapper.saveAllTaskPromptByUserId(map.get("promptTemplate")+"",map.get("userId")+"");
            }else{
                String prompt = userInfoMapper.getTaskPromptById(map.get("agentId")+"",map.get("userId")+"");
                if(prompt != null){
                    userInfoMapper.updateTaskPromptByUserId(map.get("agentId")+"",map.get("promptTemplate")+"",map.get("userId")+"");
                }else{
                    userInfoMapper.saveTaskPromptByUserId(map.get("agentId")+"",map.get("promptTemplate")+"",map.get("userId")+"");
                }
            }


        return ResultBody.success("更新成功");
    }

    @Override
    public ResultBody getIsChangeByCorpId(String corpId){
// 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 获取10分钟前的时间
        LocalDateTime tenMinutesBefore = now.minusMinutes(10);

        // 定义日期时间格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 格式化时间
        String currentTime = now.format(formatter);
        String timeTenMinutesBefore = tenMinutesBefore.format(formatter);

        int num = userInfoMapper.getIsChangeByCorpId(corpId,currentTime,timeTenMinutesBefore);

        return ResultBody.success(num);
    }

    public static void main(String[] args) {
    }
}
