package com.wx.fbsir.business.aigc.service;

import com.wx.fbsir.business.aigc.domain.AiRequest;
import com.wx.fbsir.business.aigc.domain.ChatHistoryRequest;

import java.util.List;
import java.util.Map;

/**
 * AIGC服务接口
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
public interface IAigcService {
    
    /**
     * 获取用户的主机ID
     * 
     * @param userId 用户ID
     * @return 主机ID
     */
    String getUserHostId(Long userId);

    /**
     * 保存初始请求记录
     * 
     * @param aiRequest AI请求对象
     */
    void saveInitialRequest(AiRequest aiRequest);

    /**
     * 查询用户的聊天历史记录
     * 
     * @param request 查询请求
     * @return 历史记录列表
     */
    List<Map<String, Object>> getChatHistory(ChatHistoryRequest request);

    /**
     * 获取用户最近一次聊天记录
     * 
     * @param userId 用户ID
     * @return 最近聊天记录
     */
    Map<String, Object> getLatestChat(String userId);

    /**
     * 获取草稿列表
     * 
     * @param userId 用户ID
     * @param aiName AI名称（可选）
     * @param keyword 关键词（可选）
     * @return 草稿列表
     */
    List<Map<String, Object>> getDrafts(Long userId, String aiName, String keyword);

    /**
     * 保存草稿内容
     * 
     * @param draftData 草稿数据
     * @return 保存结果
     */
    boolean saveDraft(Map<String, Object> draftData);

    /**
     * 删除草稿
     * 
     * @param draftId 草稿ID
     * @param userId 用户ID
     * @return 删除结果
     */
    boolean deleteDraft(String draftId, Long userId);

    /**
     * 获取可用的AI列表（硬编码）
     * 
     * @return AI列表
     */
    List<Map<String, Object>> getAvailableAiList();

    /**
     * 保存聊天数据（Engine回调使用）
     * 
     * @param chatData 聊天数据
     */
    void saveChatData(Map<String, Object> chatData);

    /**
     * 根据sessionId获取聊天记录
     * 
     * @param sessionId 会话ID
     * @return 聊天记录
     */
    Map<String, Object> getChatBySessionId(String sessionId);

    /**
     * 更新聊天数据（追加日志、截图等）
     * 
     * @param chatData 聊天数据
     */
    void updateChatData(Map<String, Object> chatData);

    /**
     * 🔥 根据chatId获取最新的聊天记录（用于上下文复用，获取已有AI会话ID）
     * 
     * @param chatId 会话ID
     * @return 最新聊天记录（包含所有AI会话ID）
     */
    Map<String, Object> getLatestChatByChatId(String chatId);
    
    /**
     * 🔥 保存AI记录扩展数据（原草稿表）
     * 存储AI生成的分享链接、截图等扩展信息
     * 
     * @param extensionData 扩展数据
     */
    void saveExtensionData(Map<String, Object> extensionData);

    /**
     * 🔥 获取草稿列表（按task_id分组，参考旧项目cube-admin）
     * 返回格式：每条记录包含question、questionTime，以及aiResponses数组
     * 
     * @param userId 用户ID
     * @param keyWord 关键词
     * @return 分组后的草稿列表（含AI响应）
     */
    List<Map<String, Object>> getPlayWrightDrafts(Long userId, String keyWord);
    
    /**
     * 🔥 获取草稿文本内容（用于复制功能）
     * 根据taskId和aiName从数据库获取draft_content
     * 
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param aiName AI名称
     * @return 草稿文本内容
     */
    String getDraftContent(Long userId, String taskId, String aiName);

    /**
     * 生成输出物
     *
     * 设计说明：
     * 基于当前会话的对话结果生成结构化输出物，并写入会话数据中供后续导出与推送复用。
     *
     * @param sessionId 会话ID
     * @return 输出物信息
     */
    Map<String, Object> generateOutputArtifact(String sessionId);


    /**
     * 导出输出物为 Markdown
     *
     * 设计说明：
     * 将输出物转换为 Markdown 文本结构，供文件下载或外部展示使用。
     *
     * @param sessionId 会话ID
     * @return Markdown 内容
     */
    Map<String, Object> exportOutputArtifactMarkdown(String sessionId);


    /**
     * 导出输出物为 JSON
     *
     * 设计说明：
     * 提供标准化结构数据，供前端渲染或 Webhook 推送复用。
     *
     * @param sessionId 会话ID
     * @return JSON结构数据
     */
    Map<String, Object> exportOutputArtifactJson(String sessionId);


    /**
     * 推送输出物到 Webhook
     *
     * 设计说明：
     * 基于导出结果构造推送内容，将当前会话的输出物发送到外部系统。
     *
     * @param sessionId 会话ID
     * @param format 推送格式（md/json）
     * @param webhookUrl Webhook地址
     * @return 推送结果
     */
    Map<String, Object> pushOutputArtifactWebhook(String sessionId, String format, String webhookUrl);


    /**
     * 保存编辑后的输出物
     *
     * 设计说明：
     * 将用户编辑后的输出物内容回写至会话数据中，保证后续导出与推送使用最新结果。
     *
     * @param params 保存参数
     * @return 更新后的输出物
     */
    Map<String, Object> saveOutputArtifact(Map<String, Object> params);
}
