package com.wx.fbsir.business.aigc.mapper;

import com.wx.fbsir.business.aigc.domain.ChatHistoryRequest;

import java.util.List;
import java.util.Map;

/**
 * AIGC Mapper接口
 * 
 * @author FBSir
 * @date 2026-01-07
 */
public interface AigcMapper {
    
    /**
     * 获取用户的主机ID
     * 
     * @param userId 用户ID
     * @return 主机ID
     */
    String getUserHostId(Long userId);

    /**
     * 保存聊天数据
     * 
     * @param chatData 聊天数据
     */
    void saveChatData(Map<String, Object> chatData);

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
     * @param params 查询参数
     * @return 草稿列表
     */
    List<Map<String, Object>> getDrafts(Map<String, Object> params);

    /**
     * 保存草稿
     * 
     * @param draftData 草稿数据
     */
    void saveDraft(Map<String, Object> draftData);

    /**
     * 删除草稿
     * 
     * @param params 删除参数
     * @return 删除条数
     */
    int deleteDraft(Map<String, Object> params);

    /**
     * 根据sessionId获取聊天记录
     * 
     * @param sessionId 会话ID
     * @return 聊天记录
     */
    Map<String, Object> getChatBySessionId(String sessionId);

    /**
     * 更新聊天数据
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
     * 
     * @param extensionData 扩展数据
     */
    void saveExtensionData(Map<String, Object> extensionData);

    /**
     * 🔥 获取草稿列表（按task_id分组，参考旧项目cube-admin）
     * 
     * @param userId 用户ID
     * @param keyWord 关键词
     * @return 分组后的草稿列表
     */
    List<Map<String, Object>> getPlayWrightDraftList(@org.apache.ibatis.annotations.Param("userId") Long userId, 
                                                      @org.apache.ibatis.annotations.Param("keyWord") String keyWord);

    /**
     * 🔥 获取指定task_id下的所有AI响应（参考旧项目cube-admin）
     * 
     * @param taskId 任务ID
     * @return AI响应列表
     */
    List<Map<String, Object>> getPlayWrightDraftAiList(String taskId);
    
    /**
     * 🔥 获取草稿文本内容（用于复制功能）
     * 
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param aiName AI名称
     * @return 草稿文本内容
     */
    String getDraftContent(@org.apache.ibatis.annotations.Param("userId") Long userId,
                          @org.apache.ibatis.annotations.Param("taskId") String taskId,
                          @org.apache.ibatis.annotations.Param("aiName") String aiName);
}
