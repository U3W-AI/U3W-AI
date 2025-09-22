package com.playwright.entity;

import lombok.Data;
import org.springframework.ai.tool.annotation.ToolParam;

@Data
public class UserInfoRequest {

    @ToolParam(description = "用户输入的提示词或对话内容")
    private String userPrompt;

    @ToolParam(description = "用户 ID，唯一标识用户", required = false)
    private String userId;

    @ToolParam(description = "企业 ID（如果是企业用户，关联的企业标识）", required = false)
    private String corpId;

    @ToolParam(description = "用户唯一标识unionId")
    private String unionId;

    @ToolParam(description = "任务 ID，用于标识此次任务或请求的唯一编号", required = false)
    private String taskId;
    @ToolParam(description = "AI配置权限，多个组合时用逗号分隔" +
            "      yb-hunyuan-pt: 选择腾讯元宝T1时必选\n" +
            "      yb-hunyuan-sdsk: 腾讯元宝T1深度思考能力\n" +
            "      yb-hunyuan-lwss: 腾讯元宝T1联网搜索能力\n" +
            "      yb-deepseek-pt: 选择腾讯元宝DS必选\n" +
            "      yb-deepseek-sdsk: 腾讯元宝DS深度思考能力\n" +
            "      yb-deepseek-lwss: 腾讯元吧DS联网搜索能力\n" +
            "      zj-db: 选择豆包时必选\n" +
            "      zj-db-sdsk: 豆包深度思考能力\n" +
            "      baidu-agent: 选择百度Ai时必选\n" +
            "      baidu-sdss: 百度智能助手深度思考能力\n")
    private String roles;

    @ToolParam(description = "TurboS 智能体的聊天会话 ID（用于上下文关联）",required = false)
    private String toneChatId;

    @ToolParam(description = "YB DeepSeek 智能体的聊天会话 ID",required = false)
    private String ybDsChatId;

    @ToolParam(description = "数据库大模型（如 ZJ-DB）的聊天会话 ID",required = false)
    private String dbChatId;

    @ToolParam(description = "百度AI的聊天会话 ID",required = false)
    private String baiduChatId;

    @ToolParam(description = "DeepSeek AI的聊天会话 ID",required = false)
    private String deepseekChatId;

    @ToolParam(description = "通义千问的聊天会话 ID",required = false)
    private String tyChatId;

    @ToolParam(description = "知乎直答的聊天会话 ID",required = false)
    private String zhzdChatId;

    @ToolParam(description = "秘塔的聊天会话 ID",required = false)
    private String metasoChatId;

    @ToolParam(description = "是否为新对话。true 表示清空上下文重新开始", required = false)
    private String isNewChat;

    @ToolParam(description = "返回的消息内容", required = false)
    private String draftContent;

    @ToolParam(description = "返回的模型名称", required = false)
    private String aiName;

    @ToolParam(description = "登录状态", required = false)
    private String status;

    @ToolParam(description = "消息类型", required = false)
    private String type;

    @ToolParam(description = "图片链接", required = false)
    private String imageUrl;
    @ToolParam(description = "图片描述", required = false)
    private String imageDescription;

    @ToolParam(description = "分享链接", required = false)
    private String shareUrl;

    @ToolParam(description = "分享图片链接", required = false)
    private String shareImgUrl;
}
