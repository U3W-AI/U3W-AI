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
}
