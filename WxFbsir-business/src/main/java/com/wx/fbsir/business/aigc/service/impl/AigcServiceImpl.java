package com.wx.fbsir.business.aigc.service.impl;

import com.wx.fbsir.business.aigc.domain.AiRequest;
import com.wx.fbsir.business.aigc.domain.ChatHistoryRequest;
import com.wx.fbsir.business.aigc.mapper.AigcMapper;
import com.wx.fbsir.business.aigc.service.IAigcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AIGC服务实现类
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
@Service
public class AigcServiceImpl implements IAigcService {

    @Autowired
    private AigcMapper aigcMapper;

    @Override
    public String getUserHostId(Long userId) {
        return aigcMapper.getUserHostId(userId);
    }

    @Override
    public void saveInitialRequest(AiRequest aiRequest) {
        // 🔥 预保存请求记录到聊天历史表（使用sessionId作为主键，便于后续更新）
        Map<String, Object> chatData = new HashMap<>();
        // 使用 sessionId 作为主键，确保后续 Engine 返回时能够更新同一条记录
        chatData.put("id", aiRequest.getSessionId() != null ? aiRequest.getSessionId() : UUID.randomUUID().toString());
        chatData.put("userId", aiRequest.getUserId());
        chatData.put("userPrompt", aiRequest.getUserPrompt());
        chatData.put("chatId", aiRequest.getChatId());  // 🔥 前端传递的会话分组ID
        
        // 保存完整的请求数据作为JSON（初始状态）
        chatData.put("data", convertToJsonString(aiRequest));
        
        aigcMapper.saveChatData(chatData);
    }

    @Override
    public List<Map<String, Object>> getChatHistory(ChatHistoryRequest request) {
        return aigcMapper.getChatHistory(request);
    }

    @Override
    public Map<String, Object> getLatestChat(String userId) {
        return aigcMapper.getLatestChat(userId);
    }

    @Override
    public List<Map<String, Object>> getDrafts(Long userId, String aiName, String keyword) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("aiName", aiName);
        params.put("keyword", keyword);
        return aigcMapper.getDrafts(params);
    }

    @Override
    public boolean saveDraft(Map<String, Object> draftData) {
        try {
            // 设置草稿ID
            draftData.put("id", UUID.randomUUID().toString());
            aigcMapper.saveDraft(draftData);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean deleteDraft(String draftId, Long userId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("draftId", draftId);
            params.put("userId", userId);
            int result = aigcMapper.deleteDraft(params);
            return result > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<Map<String, Object>> getAvailableAiList() {
        // 硬编码的AI列表（未来可配置）
        List<Map<String, Object>> aiList = new ArrayList<>();
        
        Map<String, Object> deepSeek = new HashMap<>();
        deepSeek.put("id", "deepseek");
        deepSeek.put("name", "DeepSeek");
        deepSeek.put("description", "DeepSeek AI助手，支持深度思考和联网搜索");
        deepSeek.put("avatar", "/static/ai/deepseek.png");
        deepSeek.put("onlineStatus", true);
        deepSeek.put("features", List.of("深度思考", "联网搜索", "代码生成"));
        deepSeek.put("types", List.of(
            "AI_DEEPSEEK_CHECK_LOGIN",
            "AI_DEEPSEEK_SCAN_LOGIN", 
            "AI_DEEPSEEK_QUERY"
        ));
        aiList.add(deepSeek);
        
        // 未来可扩展其他AI
        // Map<String, Object> otherAi = new HashMap<>();
        // ...
        
        return aiList;
    }

    @Override
    public void saveChatData(Map<String, Object> chatData) {
        aigcMapper.saveChatData(chatData);
    }

    @Override
    public Map<String, Object> getChatBySessionId(String sessionId) {
        return aigcMapper.getChatBySessionId(sessionId);
    }

    @Override
    public void updateChatData(Map<String, Object> chatData) {
        aigcMapper.updateChatData(chatData);
    }

    @Override
    public Map<String, Object> getLatestChatByChatId(String chatId) {
        return aigcMapper.getLatestChatByChatId(chatId);
    }

    @Override
    public void saveExtensionData(Map<String, Object> extensionData) {
        aigcMapper.saveExtensionData(extensionData);
    }

    @Override
    public List<Map<String, Object>> getPlayWrightDrafts(Long userId, String keyWord) {
        // 1. 获取按task_id分组的草稿列表
        List<Map<String, Object>> list = aigcMapper.getPlayWrightDraftList(userId, keyWord);
        
        // 2. 为每个分组获取AI响应列表
        for (Map<String, Object> item : list) {
            String taskId = String.valueOf(item.get("taskId"));
            if (taskId != null && !taskId.isEmpty() && !"null".equals(taskId)) {
                List<Map<String, Object>> aiResponses = aigcMapper.getPlayWrightDraftAiList(taskId);
                item.put("aiResponses", aiResponses);
            } else {
                item.put("aiResponses", new java.util.ArrayList<>());
            }
        }
        
        return list;
    }

    /**
     * 将对象转换为JSON字符串
     */
    private String convertToJsonString(Object obj) {
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
