package com.playwright.entity;

import lombok.Data;

/**
 * @author 优立方
 * @version JDK 1.8
 * @date 2025年05月27日 15:21
 */
@Data
public class UserInfoRequest {

    /**
     * 用户输入的提示词或对话内容
     */
    private String userPrompt;

    /**
     * 用户 ID，唯一标识用户
     */
    private String userId;

    /**
     * 企业 ID（如果是企业用户，关联的企业标识）
     */
    private String corpId;

    /**
     * 任务 ID，用于标识此次任务或请求的唯一编号
     */
    private String taskId;

    /**
     * AI配置权限，多个组合时用逗号分隔，如：yb-hunyuan-pt,yb-hunyuan-sdsk,yb-hunyuan-lwss
     * yb-hunyuan-pt: 腾讯元宝T1基础版
     * yb-hunyuan-sdsk: 腾讯元宝T1深度思考能力
     * yb-hunyuan-lwss: 腾讯元宝T1联网搜索能力
     *
     * yb-deepseek-pt: 腾讯元宝DS基础版
     * yb-deepseek-sdsk: 腾讯元宝DS深度思考能力
     * yb-deepseek-lwss: 腾讯元宝DS联网搜索能力
     *
     * cube-trubos-agent: TurboS@元器
     * cube-turbos-large-agent: TurboS长文版@元器
     * cube-mini-max-agent: MiniMax@元器
     * cube-sogou-agent: 搜狗搜索@元器
     * cube-lwss-agent: 官方搜索@元器
     *
     * zj-db: 豆包基础版
     * zj-db-sdsk: 豆包深度思考能力
     */
    private String roles;

    /**
     * TurboS 智能体的聊天会话 ID（用于上下文关联）
     */
    private String toneChatId;

    /**
     * YB DeepSeek 智能体的聊天会话 ID
     */
    private String ybDsChatId;

    /**
     * 数据库大模型（如 ZJ-DB）的聊天会话 ID
     */
    private String dbChatId;

    /**
     * 是否为新对话。如果为 true，清空上下文重新开始
     */
    private String isNewChat;

    /**
     * 返回的消息内容
     */
    private String draftContent;

    /**
     * 返回的模型名称
     */
    private String aiName;

    /**
     * 登录状态
     */
    private String status;

    /**
     * 消息类型
     */
    private String type;
}
