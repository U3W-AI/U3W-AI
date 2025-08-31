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
import com.cube.wechat.selfapp.corpchat.util.RedisUtil;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import com.cube.wechat.selfapp.corpchat.util.WeChatApiUtils;
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
import java.text.SimpleDateFormat;
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
     */
    @Override
    public ResultBody getUserCount(String userId) {
        return ResultBody.success(userInfoMapper.getUserCount(userId));
    }

    /**
     * 获取积分明细
     */
    @Override
    public ResultBody getUserPointsRecord(Map map) {
        PageHelper.startPage((int) map.get("pageIndex"), (int) map.get("pageSize"));
        List<Map> list = userInfoMapper.getUserPointsRecord(map);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
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
    public ResultBody getUserChatHistoryList(String userId, String title) {
        return ResultBody.success(userInfoMapper.getUserChatHistoryList(userId, title));
    }

    @Override
    public ResultBody getChatHistoryDetail(String conversationId) {
        String chatHisroty = userInfoMapper.getChatHistoryDetail(conversationId);
        JSONArray contentArray = JSONArray.parseArray(chatHisroty);
        return ResultBody.success(contentArray);
    }

    @Override
    public ResultBody deleteUserChatHistory(List<String> list) {
        for (String s : list) {
            redisUtil.delete("dudu." + s);
            userInfoMapper.deleteUserChatHistory(s);
        }
        return ResultBody.success("删除成功");
    }

    @Override
    public ResultBody saveChromeData(Map map) {
        userInfoMapper.saveChromeData(map);
        return ResultBody.success("成功");
    }

    @Override
    public ResultBody updateChatTitle(Map map) {
        userInfoMapper.updateChatTitle(map);
        return ResultBody.success("成功");
    }

    @Override
    public ResultBody pushOffice(List<String> ids, String userName) {
        WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserName(userName);
        List<Map> list = userInfoMapper.getPushOfficeData(ids, userName);
        if (list.isEmpty()) {
            return ResultBody.error(300, "暂无素材可被收录");
        }
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String formattedDate = today.format(formatter); // 格式化日期

        String title = woa.getOfficeAccountName() + "今日速递-" + formattedDate;
//        使用 Map 聚合数据
        Map<String, List<String>> groupedData = new LinkedHashMap<>();
        for (Map map : list) {
            String linkUrl = map.get("answer") + "";
            String keyWord = map.get("prompt") + "";
            String author = map.get("author") + "";
            String liTitle = "<a href ='" + linkUrl + "' style='color:#576b95' >" + author + "：" + map.get("title") + "</a>";
            String str = map.get("summary").toString().replace("；;", "；");
            str = str.replace(";", "；");
            String[] summarys = str.split("；");
            String summary = "<p style='font-family: 'Arial''>";
            int i = 1;
            for (String s : summarys) {

                if (i == 1) {
                    summary = summary + i + ". " + s + "。<br><br>";
                } else if (i == 2) {
                    summary = summary + i + "." + s + "。<br><br>";
                } else {
                    summary = summary + i + "." + s + "<br><br>";
                }
                i++;
            }
            summary = summary + "</p>";
            String content = liTitle + "<br><br>" + summary + "<br><br>";
            groupedData.computeIfAbsent(keyWord, k -> new ArrayList<>()).add(content);
        }

        // 输出结果
        String res = "";
        for (Map.Entry<String, List<String>> entry : groupedData.entrySet()) {
            String keyWord = "<p style='font-weight: normal;color:red;'>" + entry.getKey() + "</p>";
            String content = String.join("", entry.getValue());
            res = res + keyWord + "<br>" + content;
        }
//        V4PNB2XjrprWdg1sJxs7jpoxWs9YhZy8zYH38cbZSl3JzYw_liIxBesx7PuQ7-jV

        System.out.println(res);
        //String assessToken = weChatApiUtils.getOfficeAccessToken("wx4461361a058d608b","8e97c88d040ac7248f5f6240e578a1f3");
        String assessToken = weChatApiUtils.getOfficeAccessToken(woa.getAppId(), woa.getAppSecret());
        String url = "https://api.weixin.qq.com/cgi-bin/draft/add?access_token=" + assessToken;

        List<JSONObject> paramlist = new ArrayList<>();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", title);
        jsonObject.put("author", woa.getOfficeAccountName());
        jsonObject.put("content", res);
        //jsonObject.put("thumb_media_id","V4PNB2XjrprWdg1sJxs7jpoxWs9YhZy8zYH38cbZSl3JzYw_liIxBesx7PuQ7-jV");
        jsonObject.put("thumb_media_id", woa.getMediaId());
        paramlist.add(jsonObject);
        JSONObject param = new JSONObject();
        param.put("articles", paramlist);
        try {
            RestUtils.post(url, param);
        } catch (Exception e) {

        }
        userInfoMapper.updateLinkStatus(list);
        return ResultBody.success("上传成功！");
    }

    public ResultBody authChecker(String userName) {
        Integer num = userInfoMapper.getUserCountByUserName(userName);
        if (num > 0) {
            return ResultBody.success("上传成功！");
        }
        return ResultBody.error(404, "用户不存在");
    }

    public ResultBody changePoint(String userId, String method) {
        Integer points = pointsSystem.getUserPoints(userId);
        if (points < 1) {
            return ResultBody.error(201, "积分余额不足，请明日再来或者联系客服充值");
        }
        pointsSystem.setUserPoint(userId, method, null, "0x2edc4228a84d672affe8a594033cb84a029bcafc", "f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        return ResultBody.success("执行成功！");
    }

    @Override
    public ResultBody getOfficeAccountByUserId(String userId) {
        WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserName(userId);
        if(woa != null) {
            return ResultBody.success(woa);
        }
        return ResultBody.FAIL;
    }

    @Override
    public ResultBody pushAutoOneOffice(Map map) {
        WcOfficeAccount woa = userInfoMapper.getOfficeAccountByUserName(map.get("userId") + "");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String formattedDate = sdf.format(new Date()); // 格式化日期

        String assessToken = weChatApiUtils.getOfficeAccessToken(woa.getAppId(), woa.getAppSecret());
        String url = "https://api.weixin.qq.com/cgi-bin/draft/add?access_token=" + assessToken;
        String detailUrl = "https://api.weixin.qq.com/cgi-bin/draft/get?access_token=" + assessToken;
        String contentText = map.get("contentText").toString();

        int first = contentText.indexOf("《");
        int second = contentText.indexOf("》", first + 1);
        String title = contentText.substring(first + 1, second);
//            contentText = contentText.substring(second + 6);
        contentText = contentText.substring(second + 1, contentText.lastIndexOf(">") + 1);
        contentText = contentText.replaceAll("\r\n\r\n", "");
        if (map.get("shareUrl") != null && !map.get("shareUrl").equals("")) {
            String shareUrl = "原文链接：" + map.get("shareUrl") + "<br><br>";
            contentText = shareUrl + contentText;
        }
        List<JSONObject> paramlist = new ArrayList<>();
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("title", title);

        jsonObject.put("author", woa.getOfficeAccountName());
        jsonObject.put("content", contentText);
        jsonObject.put("thumb_media_id", woa.getMediaId());
        jsonObject.put("content_source_url", map.get("shareUrl"));
        paramlist.add(jsonObject);
        JSONObject param = new JSONObject();
        param.put("articles", paramlist);
        try {
            JSONObject result = RestUtils.post(url, param);
            String mediaId = result.get("media_id").toString();
            JSONObject postJson = new JSONObject();
            postJson.put("media_id", mediaId);
            JSONObject detail = RestUtils.post(detailUrl, postJson);
            Object o = detail.get("news_item");
            List<Map<String, Object>> newsItemArray = (List<Map<String, Object>>) o;
            if (newsItemArray == null || newsItemArray.isEmpty()) {
                throw new RuntimeException("news_item 数组为空");
            }
            Map<String, Object> newsItem = newsItemArray.get(0);
            String tempUrl = (String) newsItem.get("url");
            System.out.println(tempUrl);
            return ResultBody.success(tempUrl);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ResultBody.error(300, "推送失败");
    }

    @Override
    public ResultBody pushViewAutoOffice(String taskId) {


        List<Map> list = userInfoMapper.getPushViewOfficeData(taskId);
        if (list.size() == 0) {
            return ResultBody.error(300, "暂无素材可被收录");
        }

//        使用 Map 聚合数据
        Map<String, List<String>> groupedData = new LinkedHashMap<>();
        for (Map map : list) {
            String linkUrl = map.get("answer") + "";
            String keyWord = map.get("prompt") + "";
            String author = map.get("author") + "";
            String summary = map.get("summary").toString();
            String liTitle = "<a href ='" + linkUrl + "' style='color:#576b95' >" + author + "：" + map.get("title") + "</a>";
            summary = "<p style='font-family: 'Arial''>" + summary;
            summary = summary + "</p>";
            String content = liTitle + "<br><br>" + summary + "<br><br>";
            groupedData.computeIfAbsent(keyWord, k -> new ArrayList<>()).add(content);
        }
        // 输出结果
        String res = "";
        for (Map.Entry<String, List<String>> entry : groupedData.entrySet()) {
            String keyWord = "<p style='font-weight: normal;color:red;'>" + entry.getKey() + "</p>";
            String content = String.join("", entry.getValue());
            res = res + keyWord + "<br>" + content;
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
            if (wcOfficeAccount.getPicUrl() != null) {
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
    public ResultBody getAgentBind(Long userId) {
        Map woc = userInfoMapper.getAgentTokenByUserId(userId + "");
        return ResultBody.success(woc);
    }

    @Override
    public ResultBody getSpaceInfoByUserId(Long userId) {
        Map woc = userInfoMapper.getSpaceInfoByUserId(userId + "");
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
    public ResultBody saveChromeTaskData(String taskId, String userid, String corpId) {
        List<Map> list = new ArrayList<>();

        Map oneMap = new HashMap();
        oneMap.put("taskId", taskId);
        oneMap.put("taskName", "主题生成");
        oneMap.put("status", "running");
        oneMap.put("planTime", "40秒");
        oneMap.put("userid", userid);
        oneMap.put("corpId", corpId);
        list.add(oneMap);

        Map twoMap = new HashMap();
        twoMap.put("taskId", taskId);
        twoMap.put("taskName", "素材搜集");
        twoMap.put("status", "waiting");
        twoMap.put("planTime", "3分钟");
        twoMap.put("userid", userid);
        twoMap.put("corpId", corpId);
        list.add(twoMap);

        Map threeMap = new HashMap();
        threeMap.put("taskId", taskId);
        threeMap.put("taskName", "内容生成");
        threeMap.put("status", "waiting");
        threeMap.put("planTime", "5分钟");
        threeMap.put("userid", userid);
        threeMap.put("corpId", corpId);
        list.add(threeMap);

        Map fourMap = new HashMap();
        fourMap.put("taskId", taskId);
        fourMap.put("taskName", "发布预览");
        fourMap.put("status", "waiting");
        fourMap.put("planTime", "1分钟");
        fourMap.put("userid", userid);
        fourMap.put("corpId", corpId);
        list.add(fourMap);


        userInfoMapper.saveChromeTaskData(list);
        return ResultBody.success("成功");
    }

    @Override
    public ResultBody getTaskStatus(String taskId) {
        List<Map> list = userInfoMapper.getTaskStatus(taskId);
        return ResultBody.success(list);
    }

    @Override
    public ResultBody getUserPromptTem(String userId, String agentId) {
        String promptTem = userInfoMapper.getUserPromptTem(userId, agentId);
        return ResultBody.success(promptTem);
    }

    @Override
    public ResultBody getPromptTem(Integer type, String userId) {
        if (type == 1) {
            List<Map> list = userInfoMapper.getPromptTem();
            return ResultBody.success(list);
        } else {
            String prompt = userInfoMapper.getTaskPromptById("desc", userId);
            return ResultBody.success(prompt);
        }
    }

    @Override
    public ResultBody updateUserPromptTem(Map map) {

        Integer points = pointsSystem.getUserPoints(map.get("userId") + "");
        if (points < 1) {
            return ResultBody.error(201, "积分余额不足，请明日再来或者联系客服充值");
        }

        if (map.get("agentId").equals("desc")) {
            pointsSystem.setUserPoint(map.get("userId") + "", "记忆修改", null, "0x2edc4228a84d672affe8a594033cb84a029bcafc", "f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        } else {
            pointsSystem.setUserPoint(map.get("userId") + "", "模板配置", null, "0x2edc4228a84d672affe8a594033cb84a029bcafc", "f34f737203aa370f53ef0e041c1bff36bf59db8eb662cdb447f01d9634374dd");
        }

        if (map.get("isAllSel").equals(true)) {
            //全选状态，直接全删全增
            userInfoMapper.delTaskPromptByUserId(map.get("userId") + "");
            userInfoMapper.saveAllTaskPromptByUserId(map.get("promptTemplate") + "", map.get("userId") + "");
        } else {
            String prompt = userInfoMapper.getTaskPromptById(map.get("agentId") + "", map.get("userId") + "");
            if (prompt != null) {
                userInfoMapper.updateTaskPromptByUserId(map.get("agentId") + "", map.get("promptTemplate") + "", map.get("userId") + "");
            } else {
                userInfoMapper.saveTaskPromptByUserId(map.get("agentId") + "", map.get("promptTemplate") + "", map.get("userId") + "");
            }
        }


        return ResultBody.success("更新成功");
    }

    @Override
    public ResultBody getIsChangeByCorpId(String corpId) {
// 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 获取10分钟前的时间
        LocalDateTime tenMinutesBefore = now.minusMinutes(10);

        // 定义日期时间格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // 格式化时间
        String currentTime = now.format(formatter);
        String timeTenMinutesBefore = tenMinutesBefore.format(formatter);

        int num = userInfoMapper.getIsChangeByCorpId(corpId, currentTime, timeTenMinutesBefore);

        return ResultBody.success(num);
    }

    public static void main(String[] args) {
    }
}
