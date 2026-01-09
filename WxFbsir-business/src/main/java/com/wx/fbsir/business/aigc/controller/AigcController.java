package com.wx.fbsir.business.aigc.controller;

import com.wx.fbsir.business.aigc.domain.ChatHistoryRequest;
import com.wx.fbsir.business.aigc.domain.AiRequest;
import com.wx.fbsir.business.aigc.service.IAigcService;
// import com.wx.fbsir.business.websocket.server.EngineSessionManager;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AIGC通用控制器
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 功能概述
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * 1. 通用AI请求处理 - 支持所有AI_xxx类型的请求
 * 2. 会话历史管理 - 保存和查询用户的AI对话记录
 * 3. 草稿内容管理 - 保存AI生成的内容到草稿库
 * 4. WebSocket通信 - 与Engine端实时通信
 * 
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 📌 设计思路
 * ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 * 
 * - 通用化设计：所有AI_xxx请求都走同一个接口，通过type区分
 * - 扩展友好：新增AI只需要在Engine端添加处理器，无需修改Admin端
 * - 数据统一：所有AI的对话都保存到同一个表结构中
 * - WebSocket通信：实时转发请求到Engine端，接收处理结果
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
@RestController
@RequestMapping("/aigc")
public class AigcController extends BaseController {

    @Autowired
    private IAigcService aigcService;

    // TODO: 实现WebSocket通信到Engine端
    // @Autowired
    // private EngineSessionManager engineSessionManager;

    /**
     * 通用AI请求处理接口
     * 
     * 支持的请求类型：
     * - AI_DEEPSEEK_CHECK_LOGIN: DeepSeek登录检查
     * - AI_DEEPSEEK_SCAN_LOGIN: DeepSeek扫码登录
     * - AI_DEEPSEEK_QUERY: DeepSeek AI咨询
     * - 后续扩展: AI_XXX_XXX 类型的请求
     * 
     * @param aiRequest AI请求对象
     * @return 处理结果
     */
    @PostMapping("/request")
    @Log(title = "AI请求", businessType = BusinessType.OTHER)
    public AjaxResult handleAiRequest(@Validated @RequestBody AiRequest aiRequest) {
        try {
            // 1. 生成请求ID（全链路跟踪）
            String requestId = UUID.randomUUID().toString();
            aiRequest.setRequestId(requestId);
            
            // 2. 设置用户信息
            Long userId = getUserId();
            String username = getUsername();
            aiRequest.setUserId(userId.toString());
            aiRequest.setUsername(username);
            
            // 3. 获取用户的主机ID
            String hostId = aigcService.getUserHostId(userId);
            if (hostId == null) {
                return AjaxResult.error("请先在个人中心配置主机ID");
            }
            aiRequest.setHostId(hostId);

            // 4. 保存初始请求记录到数据库（用于全链路追踪）
            aigcService.saveInitialRequest(aiRequest);

            // 5. 通过WebSocket转发到Engine端处理 - 临时简化实现
            // TODO: 实现具体的Engine通信逻辑
            // boolean sent = engineSessionManager.sendToEngine(hostId, aiRequest);
            // if (!sent) {
            //     return AjaxResult.error("主机离线或连接异常，请检查Engine服务状态");
            // }

            return AjaxResult.success("请求已发送，正在处理中...", requestId);
            
        } catch (Exception e) {
            logger.error("AI请求处理失败", e);
            return AjaxResult.error("请求处理失败: " + e.getMessage());
        }
    }

    /**
     * 查询用户的聊天历史记录
     * 
     * @param request 查询请求
     * @return 历史记录列表
     */
    @GetMapping("/chat/history")
    public TableDataInfo getChatHistory(ChatHistoryRequest request) {
        // 🔥 只在获取全部记录时才使用分页，获取单条时不分页（避免LIMIT冲突）
        if (request.getIsAll() == null || request.getIsAll() == 1) {
            startPage();
        }
        
        // 设置当前用户ID
        request.setUserId(getUserId().toString());
        
        List<Map<String, Object>> list = aigcService.getChatHistory(request);
        return getDataTable(list);
    }

    /**
     * 查询最近一次聊天记录（用于页面加载时恢复状态）
     * 
     * @return 最近聊天记录
     */
    @GetMapping("/chat/latest")
    public AjaxResult getLatestChat() {
        String userId = getUserId().toString();
        Map<String, Object> latestChat = aigcService.getLatestChat(userId);
        return AjaxResult.success("查询成功", latestChat);
    }

    /**
     * 获取草稿列表
     * 
     * @param aiName AI名称（可选筛选）
     * @param keyword 关键词搜索（可选）
     * @return 草稿列表
     */
    @GetMapping("/drafts")
    public TableDataInfo getDrafts(@RequestParam(required = false) String aiName,
                                   @RequestParam(required = false) String keyword) {
        startPage();
        
        Long userId = getUserId();
        List<Map<String, Object>> list = aigcService.getDrafts(userId, aiName, keyword);
        return getDataTable(list);
    }

    /**
     * 保存草稿内容（通常由Engine端回调触发）
     * 
     * @param draftData 草稿数据
     * @return 保存结果
     */
    @PostMapping("/draft/save")
    @Log(title = "保存草稿", businessType = BusinessType.INSERT)
    public AjaxResult saveDraft(@RequestBody Map<String, Object> draftData) {
        try {
            // 设置用户信息
            draftData.put("userName", getUserId());
            
            boolean success = aigcService.saveDraft(draftData);
            return success ? AjaxResult.success("草稿保存成功") : AjaxResult.error("草稿保存失败");
            
        } catch (Exception e) {
            logger.error("草稿保存失败", e);
            return AjaxResult.error("草稿保存失败: " + e.getMessage());
        }
    }

    /**
     * 删除草稿
     * 
     * @param draftId 草稿ID
     * @return 删除结果
     */
    @DeleteMapping("/draft/{draftId}")
    @Log(title = "删除草稿", businessType = BusinessType.DELETE)
    public AjaxResult deleteDraft(@PathVariable String draftId) {
        try {
            Long userId = getUserId();
            boolean success = aigcService.deleteDraft(draftId, userId);
            return success ? AjaxResult.success("删除成功") : AjaxResult.error("删除失败");
        } catch (Exception e) {
            logger.error("删除草稿失败", e);
            return AjaxResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用的AI列表（硬编码，未来可配置）
     * 
     * @return AI列表
     */
    @GetMapping("/ai/list")
    public AjaxResult getAiList() {
        List<Map<String, Object>> aiList = aigcService.getAvailableAiList();
        return AjaxResult.success("查询成功", aiList);
    }

    /**
     * 保存聊天数据（前端接收到TASK_RESULT后调用）
     * 
     * @param chatData 聊天数据
     * @return 保存结果
     */
    @PostMapping("/chat/save")
    @Log(title = "保存聊天记录", businessType = BusinessType.INSERT)
    public AjaxResult saveChatData(@RequestBody Map<String, Object> chatData) {
        try {
            // 设置用户ID
            chatData.put("userId", getUserId().toString());
            
            // 确保有ID字段（使用sessionId或生成新的）
            if (chatData.get("id") == null) {
                String sessionId = (String) chatData.get("sessionId");
                chatData.put("id", sessionId != null ? sessionId : UUID.randomUUID().toString());
            }
            
            aigcService.saveChatData(chatData);
            return AjaxResult.success("聊天记录保存成功");
            
        } catch (Exception e) {
            logger.error("保存聊天记录失败", e);
            return AjaxResult.error("保存聊天记录失败: " + e.getMessage());
        }
    }

    /**
     * 检查用户是否配置了主机ID
     * 
     * @return 检查结果
     */
    @GetMapping("/user/host-status")
    public AjaxResult checkUserHostStatus() {
        Long userId = getUserId();
        String hostId = aigcService.getUserHostId(userId);
        
        boolean hasHost = hostId != null && !hostId.isEmpty();
        // boolean engineOnline = hasHost && engineSessionManager.isEngineOnline(hostId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("hasHostId", hasHost);
        result.put("hostId", hasHost ? hostId : null);
        result.put("engineOnline", false); // TODO: 实现Engine在线状态检查
        
        return AjaxResult.success("查询成功", result);
    }

    /**
     * 🔥 获取草稿列表（按task_id分组，参考旧项目cube-admin的卡片式显示）
     * 返回格式：每条记录包含question、questionTime，以及aiResponses数组
     * 
     * @param keyWord 关键词搜索（可选）
     * @return 分组后的草稿列表（含AI响应）
     */
    @GetMapping("/getPlayWrighDrafts")
    public TableDataInfo getPlayWrightDrafts(@RequestParam(required = false) String keyWord) {
        startPage();
        
        Long userId = getUserId();
        List<Map<String, Object>> list = aigcService.getPlayWrightDrafts(userId, keyWord);
        return getDataTable(list);
    }
}
