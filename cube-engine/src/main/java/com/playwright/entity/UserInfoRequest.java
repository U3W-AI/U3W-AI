package com.playwright.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "UserInfoRequest", description = "用户请求体，封装AI任务交互所需字段")
public class UserInfoRequest {

    @Schema(description = "用户输入的提示词或对话内容", example = "帮我写一个公司年会的开场白")
    private String userPrompt;

    @Schema(description = "用户 ID，唯一标识用户", example = "22")
    private String userId;

    @Schema(description = "企业 ID（如果是企业用户，关联的企业标识）", example = "corp_abc123")
    private String corpId;

    @Schema(description = "任务 ID，用于标识此次任务或请求的唯一编号", example = "task_7890")
    private String taskId;

    @Schema(description = "AI配置权限，多个组合时用逗号分隔" +
            "      yb-hunyuan-pt: 腾讯元宝T1基础版\n" +
            "      yb-hunyuan-sdsk: 腾讯元宝T1深度思考能力\n" +
            "      yb-hunyuan-lwss: 腾讯元宝T1联网搜索能力\n" +
            "      yb-deepseek-pt: 腾讯元宝DS基础版\n" +
            "      yb-deepseek-sdsk: 腾讯元宝DS深度思考能力",
            example = "yb-hunyuan-pt,yb-hunyuan-sdsk")
    private String roles;

    @Schema(description = "TurboS 智能体的聊天会话 ID（用于上下文关联）", example = "tone_chat_001")
    private String toneChatId;

    @Schema(description = "YB DeepSeek 智能体的聊天会话 ID", example = "ds_chat_002")
    private String ybDsChatId;

    @Schema(description = "数据库大模型（如 ZJ-DB）的聊天会话 ID", example = "db_chat_003")
    private String dbChatId;

    @Schema(description = "是否为新对话。true 表示清空上下文重新开始", example = "true")
    private String isNewChat;

    @Schema(description = "返回的消息内容", example = "欢迎来到我们的年会盛典！")
    private String draftContent;

    @Schema(description = "返回的模型名称", example = "TurboS")
    private String aiName;

    @Schema(description = "登录状态", example = "loggedIn")
    private String status;

    @Schema(description = "消息类型", example = "text")
    private String type;

    private String shareUrl;
}
