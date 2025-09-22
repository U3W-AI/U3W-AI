package com.cube.mcp;

import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.redis.RedisCache;
import com.cube.common.entity.CustomMultipartFile;
import com.cube.common.entity.UserInfoRequest;
import com.cube.common.entity.UserSimpleInfo;
import com.cube.common.utils.ThreadUserInfo;
import com.cube.mcp.constants.McpExceptionConstants;
import com.cube.mcp.entities.ImgInfo;
import com.cube.mcp.entities.Item;
import com.cube.mcp.entities.McpResult;
import com.cube.wechat.selfapp.app.config.MyWebSocketHandler;
import com.cube.wechat.selfapp.app.constants.WxExceptionConstants;
import com.cube.wechat.selfapp.app.controller.AIGCController;
import com.cube.wechat.selfapp.app.controller.MediaController;
import com.cube.wechat.selfapp.app.controller.WechatMpController;
import com.cube.wechat.selfapp.app.util.HttpUtil;
import com.cube.wechat.selfapp.app.util.UserInfoUtil;
import com.cube.wechat.selfapp.corpchat.util.ResultBody;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.hc.core5.http.ParseException;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author muyou
 * dateStart 2024/8/4 9:34
 * dateNow   2025/8/20 10:14
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CubeMcp {

    private final UserInfoUtil userInfoUtil;
    private final MyWebSocketHandler myWebSocketHandler;
    private final RedisCache redisCache;
    private final WechatMpController wechatMpController;
    private final AIGCController aigcController;
    private final MediaController mediaController;

    @Tool(name = "豆包AI", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult dbMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                           UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "豆包", "dbMcp", "zj-db");
    }

    @Tool(name = "腾讯元宝T1", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult ybT1Mcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                             UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "腾讯元宝T1", "ybT1Mcp", "yb-hunyuan-pt");
    }

    @Tool(name = "腾讯元宝DS", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult ybDsMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                             UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "腾讯元宝DS", "ybDsMcp", "yb-deepseek-pt");
    }

    @Tool(name = "百度AI", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult baiduMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                              UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "百度AI", "baiduMcp", "baidu-agent");
    }

    @Tool(name = "DeepSeekAI", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult dsMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                           UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "DeepSeek", "dsMcp", "deepseek");
    }

    @Tool(name = "通义千问", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult qwMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                           UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "通义千问", "qwMcp", "ty-qw");
    }

    @Tool(name = "秘塔", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult metasoMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                               UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "秘塔", "metasoMcp", "mita");
    }

    @Tool(name = "知乎直答", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult zhzdMcp(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词,用户选择的ai配置信息")
                             UserInfoRequest userInfoRequest) {
        return startAi(userInfoRequest, "知乎直答", "zhzdMcp", "zhzd-chat");
    }

    private McpResult startAi(UserInfoRequest userInfoRequest, String aiName, String methodName, String aiConfig) {
        long startTime = System.currentTimeMillis();
        try {
            userInfoRequest.setTaskId(UUID.randomUUID().toString());
            UserSimpleInfo userSimpleInfo = ThreadUserInfo.getUserInfo();
            if (userSimpleInfo == null || userSimpleInfo.getUserId() == null || userSimpleInfo.getUserId().isEmpty() || userSimpleInfo.getCropId() == null || userSimpleInfo.getCropId().isEmpty()) {
                throw new RuntimeException(McpExceptionConstants.MCP_USER_INFO_NOT_EXIST);
            }
            userInfoRequest.setUserId(userSimpleInfo.getUserId());
            userInfoRequest.setCorpId(userSimpleInfo.getCropId());
            userInfoRequest.setType("mcp");
            userInfoRequest.setAiName(methodName);
            String taskId = UUID.randomUUID().toString();
            userInfoRequest.setTaskId(taskId);
            userInfoRequest.setRoles(aiConfig);
            myWebSocketHandler.sendMsgToAI(userSimpleInfo.getCropId(), userInfoRequest);
            for (int i = 0; i < 20; i++) {
                Object cacheObject = redisCache.getCacheObject("mcp:" + userSimpleInfo.getUserId() + ":" + userInfoRequest.getAiName() + ":" + userInfoRequest.getTaskId());
                if (cacheObject != null) {
                    if(cacheObject instanceof McpResult) {
                        return (McpResult) cacheObject;
                    }
                    return McpResult.fail("返回结果错误,请联系管理员", null);
                }
                Thread.sleep(10000);
            }
            return McpResult.fail("返回结果错误,请联系管理员", null);
        } catch (Exception e) {
            // 使用增强的异常日志记�?
            return McpResult.fail(aiName + "调用异常,请联系管理员", null);
        }
    }

    @Tool(name = "投递到公众号", description = "通过用户信息调用ai,需要用户unionId,ai配置信息,提示词")
    public McpResult publishToOffice(@ToolParam(description = "用户调用信息,包括用户unionId,用户提示词?用户选择的ai配置信息")
                                     UserInfoRequest userInfoRequest) {
        try {
            String roles = "znpb-ds,yb-deepseek-pt,yb-deepseek-sdsk,yb-deepseek-lwss,";
            UserSimpleInfo userSimpleInfo = ThreadUserInfo.getUserInfo();
            if(userSimpleInfo == null) {
                throw new RuntimeException(McpExceptionConstants.MCP_USER_INFO_NOT_EXIST);
            }
            String unionId = userSimpleInfo.getUserId();
            String userId = userSimpleInfo.getUserId();
            userInfoRequest.setUserId(userId);
            userInfoRequest.setUnionId(unionId);
            String prompt = userInfoRequest.getUserPrompt();
//            先检查传递内容是否是链接
            String regex = "https?://[^\s]+";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(prompt);
            String promptUrl = null;
            if (matcher.find()) {
                promptUrl = matcher.group();
            }
            String content = null;
            // 获取提示词
            AjaxResult wechatLayout = mediaController.getCallWord("wechat_layout");
            Object o = wechatLayout.get("data");
            if(o == null) {
                return McpResult.fail("获取提示词失败,请稍后重试", "");
            }
            String znpbPrompt = o.toString();

            if (prompt.startsWith(znpbPrompt.substring(0, 10))) {
                prompt = prompt.substring(znpbPrompt.length());
                userInfoRequest.setUserPrompt("文本内容: `" + prompt + "`");
            } else {
                if (promptUrl != null) {
                    String getContentPrompt = promptUrl + " 获取以上链接内容";
//                无需深度思考和联网搜索
                    userInfoRequest.setRoles("yb-deepseek-pt, znpb");
                    userInfoRequest.setUserPrompt(getContentPrompt);
                    McpResult mcpResult = startAi(userInfoRequest, "元宝", "publishToOffice", roles);
                    content = mcpResult.getResult();
                    if (mcpResult.getCode() != 200 || content == null || content.isEmpty()) {
                        return McpResult.fail("获取链接内容失败,请稍后重试", "");
                    }
                    userInfoRequest.setUserPrompt("文本内容: `" + content + "`");
                } else {
                    userInfoRequest.setUserPrompt("文本内容: `" + prompt + "`");
                }
            }

            // 获取图片信息
            McpResult mcp = getMaterial(userInfoRequest);
            String listJson = mcp.getResult();
            String thumbMediaId = null;
            List<Item> images = JSONUtil.toList(listJson, Item.class);
            List<ImgInfo> imgInfoList = new ArrayList<>();

            for (Item image : images) {
                String name = image.getName();
                if (name.contains(unionId)) {
                    if (thumbMediaId == null && name.contains("封面")) {
                        thumbMediaId = image.getMedia_id();
                        continue;
                    }
                    ImgInfo imgInfo = new ImgInfo();
                    imgInfo.setImgDescription(name.substring(name.indexOf("-")));
                    imgInfo.setImgUrl(image.getUrl());
                    imgInfoList.add(imgInfo);
                }
            }
            if (imgInfoList.isEmpty()) {
                userInfoRequest.setUserPrompt(userInfoRequest.getUserPrompt() + " " + znpbPrompt);
            } else {
                userInfoRequest.setUserPrompt(userInfoRequest.getUserPrompt() + ", 图片信息: {" + imgInfoList.toString() + "} " + znpbPrompt);
            }
            McpResult mcpResult = startAi(userInfoRequest, "元宝", "publishToOffice", roles);
            if (mcpResult == null) {
                return McpResult.fail("腾讯元宝DS调用失败,请稍后重试", null);
            }
            String shareUrl = mcpResult.getShareUrl();
            String contentText = mcpResult.getResult();
            int first = contentText.indexOf("《");
            int second = contentText.indexOf("》", first + 1);
            String title = contentText.substring(first + 1, second);
            contentText = contentText.substring(second + 1, contentText.lastIndexOf(">") + 1);
            contentText = contentText.replaceAll("\r\n\r\n", "");

            Map<String, Object> map = new HashMap<>();
            map.put("title", title);
            map.put("contentText", contentText);
            map.put("unionId", unionId);
            map.put("shareUrl", shareUrl);
            map.put("thumbMediaId", thumbMediaId);
            ResultBody resultBody = wechatMpController.publishToOffice(map);
            long code = resultBody.getCode();
            String res = resultBody.getData().toString();
//            成功获取
            if (code == 200 && res != null && !res.isEmpty()) {
                return McpResult.success("草稿保存成功", res);
            } else {
                return McpResult.fail(res, null);
            }
        } catch (Exception e) {
            // 使用增强的异常日志记�?
            return McpResult.fail("投递到公众号异常,请联系管理员", null);
        }
    }

    @Tool(name = "获取图片素材", description = "获取图片素材")
    public McpResult getMaterial(@ToolParam(description = "用户调用信息,必须包括用户unionId")
                                 UserInfoRequest userInfoRequest) throws WxErrorException, IOException, ParseException {
        try {
            UserSimpleInfo userSimpleInfo = ThreadUserInfo.getUserInfo();
            if(userSimpleInfo == null) {
                throw new RuntimeException(WxExceptionConstants.WX_PARAMETER_EXCEPTION);
            }
            String unionId = userSimpleInfo.getUnionId();
            if (unionId == null || unionId.isEmpty()) {
                throw new RuntimeException(WxExceptionConstants.WX_PARAMETER_EXCEPTION);
            }
            Map<String, Object> map = new HashMap<>();
            map.put("unionId", unionId);
            map.put("type", "image");
            ResultBody resultBody = wechatMpController.getMaterial(map);
            long code = resultBody.getCode();
            Object res = resultBody.getData();
            List<Item> itemList = JSONObject.parseArray(res.toString(), Item.class);
//            成功获取
            if (code == 200 && itemList != null && !itemList.isEmpty()) {
                return McpResult.success(JSONObject.toJSONString(itemList), null);
            } else {
                return McpResult.fail("获取图片失败", null);
            }
        } catch (Exception e) {
            throw e;
        }
    }

    @Tool(name = "上传图片素材", description = "上传图片素材")
    public McpResult uploadImgMaterial(@ToolParam(description = "用户调用信息,必须包含用户unionId")
                                       UserInfoRequest userInfoRequest,
                                       @ToolParam(description = "图片描述信息")
                                       String imgDescription,
                                       @ToolParam(description = "图片路径")
                                       String imageUrl) throws Exception {
        try {
            UserSimpleInfo userSimpleInfo = ThreadUserInfo.getUserInfo();
            if(userSimpleInfo == null) {
                throw new RuntimeException(WxExceptionConstants.WX_PARAMETER_EXCEPTION);
            }
            userInfoRequest.setUnionId(userSimpleInfo.getUnionId());
            userInfoRequest.setImageUrl(imageUrl);
            userInfoRequest.setImageDescription(imgDescription);
            McpResult mcpResult = uploadMaterialByUrl("image", userInfoRequest.getImageUrl(), userInfoRequest.getUnionId(), userInfoRequest.getImageDescription());
            if (mcpResult == null || mcpResult.getShareUrl() == null || mcpResult.getShareUrl().isEmpty()) {
                return McpResult.fail("上传图片素材失败", "");
            }
            return mcpResult;
        } catch (Exception e) {
            throw e;
        }
    }

    @Tool(name = "生成图片", description = "调用豆包生成图片")
    public McpResult generateImgMaterial(@ToolParam(description = "用户调用信息,必须包含unionId")
                                         UserInfoRequest userInfoRequest,
                                         @ToolParam(description = "图片描述信息")
                                         String imgDescription) throws Exception {
        try {
            userInfoRequest.setImageDescription(imgDescription);
            String roles = "zj-db,";
            UserSimpleInfo userSimpleInfo = ThreadUserInfo.getUserInfo();
            if(userSimpleInfo == null) {
                throw new RuntimeException(WxExceptionConstants.WX_PARAMETER_EXCEPTION);
            }
            String unionId = userSimpleInfo.getUnionId();
            userInfoRequest.setUnionId(unionId);
            userInfoRequest.setCorpId(userSimpleInfo.getCropId());
            String userId = null;
            if (unionId != null && !unionId.isEmpty()) {
                userId = userInfoUtil.getUserIdByUnionId(unionId);
            }
            if (userId == null) {
                return McpResult.fail("您无权限访问,请联系管理员", "");
            }
            userInfoRequest.setUserId(userId);
            userInfoRequest.setRoles(roles);
            userInfoRequest.setUserPrompt(imgDescription + "\n根据以上描述信息,生成一张对应的图片");
            return startAi(userInfoRequest, "豆包", "generateImgMaterial", "zj-db");
        } catch (Exception e) {
            throw e;
        }
    }

    @Tool(name = "上传公众号文章封面", description = "设置公众号文章封面, 未设置则使用上一次或者默认封")
    public McpResult uploadCoverImgMaterial(@ToolParam(description = "用户调用信息,必须包含用户unionId")
                                            UserInfoRequest userInfoRequest,
                                            @ToolParam(description = "图片路径")
                                            String imageUrl) throws Exception {
        try {
            userInfoRequest.setImageUrl(imageUrl);
            userInfoRequest.setImageDescription("封面");
            McpResult mcpResult = uploadMaterialByUrl("image", userInfoRequest.getImageUrl(), userInfoRequest.getUnionId(), userInfoRequest.getImageDescription());
            if (mcpResult == null || mcpResult.getShareUrl() == null || mcpResult.getShareUrl().isEmpty()) {
                return McpResult.fail("上传图片素材失败", "");
            }
            return mcpResult;
        } catch (Exception e) {
            throw e;
        }
    }

    @Tool(name = "AI生成公众号文章封面", description = "调用豆包生成图片并设置公众号文章封面, 未设置则使用上一次或者默认封面")
    public McpResult generateCoverImgMaterial(@ToolParam(description = "用户调用信息,必须包含用户unionId")
                                              UserInfoRequest userInfoRequest,
                                              @ToolParam(description = "图片描述信息")
                                              String imgDescription) throws Exception {
        try {
            McpResult mcpResult = generateImgMaterial(userInfoRequest, "生成公众号文章封面" + imgDescription);
            if (mcpResult == null || mcpResult.getShareUrl() == null || mcpResult.getShareUrl().isEmpty()) {
                return McpResult.fail("上传图片素材失败", "");
            }
            return mcpResult;
        } catch (Exception e) {
            throw e;
        }
    }


    public McpResult uploadMaterialByUrl(String type, String url, String unionId, String description) throws Exception {
        try {
            if (type.equals("image")) {
                InputStream inputStream = HttpUtil.getImageStreamByHttpClient(url);
                if (inputStream == null) {
                    throw new RuntimeException(WxExceptionConstants.WX_URL_INVALID_EXCEPTION);
                }
                String imgUrl = uploadImgMaterial(unionId, inputStream, description);
                if (imgUrl == null) {
                    return McpResult.fail("图片链接获取失败", null);
                }
                return McpResult.success("图片生成成功", imgUrl);
            }
            //TODO 对不同素材的判断,后续会添加其他类型
            return McpResult.fail("上传素材失败", "");
        } catch (WxErrorException e) {
            throw e;
        }
    }

    public McpResult uploadMaterialByStream(String type, InputStream inputStream, String unionId, String description) throws Exception {
        try {
            if (type.equals("image")) {
                String imgUrl = uploadImgMaterial(unionId, inputStream, description);
                if (imgUrl == null) {
                    return McpResult.fail("图片链接获取失败", null);
                }
                return McpResult.success("图片生成成功", imgUrl);
            }
            return McpResult.fail("上传素材失败", "");
            //TODO 对不同素材的判断,后续会添加其他类型
        } catch (WxErrorException e) {
            throw e;
        }
    }

    private String uploadImgMaterial(String unionId, InputStream inputStream, String description) throws WxErrorException, IOException {
        try {
            MultipartFile multipartFile = CustomMultipartFile.fromInputStream(
                    inputStream,
                    "multipartFile",
                    "upload-image.jpg",
                    "image/jpeg"
            );
            ResultBody resultBody = null;
            if (description.contains("文章封面")) {
                resultBody = wechatMpController.uploadMaterial("image", unionId, "封面", multipartFile);

            } else {
                resultBody = wechatMpController.uploadMaterial("image", unionId, description, multipartFile);
            }
            if(resultBody == null) {
                return null;
            }
            long code = resultBody.getCode();
            String res = resultBody.getData().toString();
//            成功获取
            if (code == 200 && res != null && !res.isEmpty()) {
                return res;
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException(WxExceptionConstants.WX_UPLOAD_IMG_EXCEPTION);
        }
    }
}
