package com.wx.fbsir.business.aigc.handler;

import com.alibaba.fastjson2.JSON;
import com.wx.fbsir.business.aigc.service.IAigcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AI结果处理器
 * 负责处理Engine返回的TASK_RESULT消息并存储到数据库
 * 
 * @author wxfbsir
 * @date 2026-01-08
 */
@Component
public class AiResultHandler {

    private static final Logger log = LoggerFactory.getLogger(AiResultHandler.class);

    @Autowired
    private IAigcService aigcService;

    /**
     * 处理AI咨询结果并存储到数据库
     * 
     * @param userId 用户ID
     * @param sessionId 会话ID（业务级，用于追踪）
     * @param aiType AI类型（如 deepseek）
     * @param resultData 结果数据
     * @param userPrompt 用户提问
     */
    public void handleAiResult(String userId, String sessionId, String aiType, 
                                Map<String, Object> resultData, String userPrompt) {
        try {
            log.info("[AiResultHandler] 保存AI咨询结果 - 用户: {}, 会话: {}, AI类型: {}", 
                userId, sessionId, aiType);

            Map<String, Object> chatData = new HashMap<>();
            
            // 使用sessionId作为记录ID（确保同一会话只有一条记录）
            chatData.put("id", sessionId != null ? sessionId : UUID.randomUUID().toString());
            chatData.put("userId", userId);
            chatData.put("userPrompt", userPrompt);
            
            // 将完整结果数据转为JSON存储
            chatData.put("data", JSON.toJSONString(resultData));
            
            // 设置内部chatId
            String chatId = getStringValue(resultData, "chatId");
            chatData.put("chatId", chatId != null ? chatId : sessionId);
            
            // 根据AI类型设置对应的会话ID字段（简化版：只支持DeepSeek和元宝）
            if ("deepseek".equalsIgnoreCase(aiType)) {
                chatData.put("deepseekChatId", chatId);
            } else if ("yuanbao".equalsIgnoreCase(aiType) || "腾讯元宝".equals(aiType)) {
                // 统一使用yb_chat_id存储元宝会话ID
                chatData.put("ybChatId", chatId);
            }
            
            // 保存到数据库
            aigcService.saveChatData(chatData);
            
            log.info("[AiResultHandler] AI咨询结果保存成功 - 会话: {}", sessionId);
        } catch (Exception e) {
            log.error("[AiResultHandler] 保存AI咨询结果失败 - 用户: {}, 会话: {}", userId, sessionId, e);
        }
    }

    /**
     * 从Map或JSONObject中安全获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || key == null) return null;
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }
}
