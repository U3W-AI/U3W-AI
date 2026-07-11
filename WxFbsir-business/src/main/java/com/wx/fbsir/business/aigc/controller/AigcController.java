package com.wx.fbsir.business.aigc.controller;

import com.alibaba.fastjson2.JSON;
import com.wx.fbsir.business.aigc.domain.ChatHistoryRequest;
import com.wx.fbsir.business.aigc.domain.AiRequest;
import com.wx.fbsir.business.aigc.service.IAigcService;
import com.wx.fbsir.business.point.domain.PointsResult;
import com.wx.fbsir.business.point.service.PointsPrecheckService;
// import com.wx.fbsir.business.websocket.server.EngineSessionManager;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
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
    
    @Autowired
    private PointsPrecheckService pointsPrecheckService;

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

            // 5. 积分扣减（针对需要积分的AI服务）
            // TODO: 积分扣减仍需要完善
            String requestType = aiRequest.getType();
            if (requestType != null && requestType.contains("GITEE")) {
                // 扣减积分
                try {
//                    PointsResult pointsResult = pointsPrecheckService.tryChangePoints(userId, "GITEE_ANALYSIS", null);
                    PointsResult pointsResult = pointsPrecheckService.tryChangePoints(userId, "USE_GITEE_AI", null);
                    if (pointsResult.isSuccess()) {
                        // 保存积分余额到请求中，前端可获取
                        aiRequest.setPointsBalance(pointsResult.getBalanceAfter());
                        logger.info("Gitee AI 积分扣减成功，用户ID: {}, 扣减后余额: {}", userId, pointsResult.getBalanceAfter());
                    } else {
                        // 积分扣减失败，记录日志但继续执行
                        logger.warn("Gitee AI 积分扣减失败，用户ID: {}, 原因: {}", userId, pointsResult.getMsg());
                    }
                } catch (Exception e) {
                    // 积分处理异常，记录日志但继续执行
                    logger.error("Gitee AI 积分处理异常，用户ID: {}", userId, e);
                }
            }

            // 6. 通过WebSocket转发到Engine端处理
            // 临时实现：直接返回成功，Engine会通过WebSocket接收前端的实际请求
            // TODO: 后续实现完整的Engine通信逻辑
            
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
    
    /**
     * 🔥 获取草稿文本内容（用于复制功能）
     * 根据taskId和aiName获取数据库中存储的draft_content文本内容
     * 
     * @param taskId 任务ID
     * @param aiName AI名称
     * @return 文本内容
     */
    @GetMapping("/getDraftContent")
    public AjaxResult getDraftContent(@RequestParam String taskId, @RequestParam String aiName) {
        Long userId = getUserId();
        String content = aigcService.getDraftContent(userId, taskId, aiName);
        
        Map<String, Object> result = new HashMap<>();
        result.put("content", content);
        result.put("taskId", taskId);
        result.put("aiName", aiName);
        
        return AjaxResult.success(result);
    }

    /**
     * 生成当前会话的输出物并落库
     *
     * 设计说明：
     * 1. 前端仅传入 sessionId，由后端基于会话内容生成输出物，避免前端参与业务逻辑
     * 2. 生成结果会直接写入会话数据中，供后续导出与推送复用
     *
     * @param params 请求参数（包含 sessionId）
     * @return 统一响应结果（AjaxResult）
     */
    @PreAuthorize("@ss.hasPermi('business:output:generate')")
    @PostMapping("/output/generate")
    @Log(title = "生成输出物", businessType = BusinessType.INSERT)
    public AjaxResult generateOutputArtifact(@RequestBody Map<String, Object> params) {
        try {
            String sessionId = (String) params.get("sessionId");

            // sessionId 是定位会话的唯一标识，缺失时无法执行后续业务
            if (sessionId == null || sessionId.isEmpty()) {
                return AjaxResult.error("sessionId不能为空");
            }

            // 调用Service生成输出物
            Map<String, Object> result = aigcService.generateOutputArtifact(sessionId);

            // Service层返回失败时，直接透传业务错误信息
            if (Boolean.FALSE.equals(result.get("success"))) {
                return AjaxResult.error(String.valueOf(result.get("message")));
            }

            return AjaxResult.success(result);

        } catch (Exception e) {
            logger.error("[输出物生成] 接口异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    params.get("sessionId"), e.getClass().getSimpleName(), e.getMessage());
            return AjaxResult.error("生成输出物失败: " + e.getMessage());
        }
    }

    /**
     * 导出当前会话的输出物为 Markdown 文件
     *
     * 设计说明：
     * 1. 成功时以文件流形式返回 Markdown，触发浏览器下载
     * 2. 业务失败时返回 JSON 结构，便于前端识别错误信息而不是下载无效文件
     * 3. 统一使用 UTF-8 编码，避免中文乱码问题
     *
     * @param sessionId 会话ID
     */
    @PreAuthorize("@ss.hasPermi('business:output:exportMarkdown')")
    @GetMapping("/output/exportMarkdown/{sessionId}")
    @Log(title = "导出输出物Markdown", businessType = BusinessType.EXPORT)
    public void exportMarkdown(@PathVariable String sessionId,
                               jakarta.servlet.http.HttpServletResponse response) {
        try {
            Map<String, Object> result = aigcService.exportOutputArtifactMarkdown(sessionId);

            // 业务失败时返回 JSON，前端可根据 content-type 判断并提示错误
            if (Boolean.FALSE.equals(result.get("success"))) {
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(200);

                Map<String, Object> errorResult = AjaxResult.error(String.valueOf(result.get("message")));
                response.getWriter().write(JSON.toJSONString(errorResult));
                return;
            }

            // 成功时返回 Markdown 文件流，浏览器自动下载
            String markdown = String.valueOf(result.get("data"));
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/markdown;charset=UTF-8");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"fubangshou-output-" + sessionId + ".md\"");

            response.getWriter().write(markdown);

        } catch (Exception e) {
            logger.error("[输出物导出-Markdown] 接口异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    sessionId, e.getClass().getSimpleName(), e.getMessage());
            try {
                // 异常兜底：返回统一 JSON 错误，避免前端无响应或下载异常文件
                response.setCharacterEncoding("UTF-8");
                response.setContentType("application/json;charset=UTF-8");
                response.setStatus(500);

                Map<String, Object> errorResult = AjaxResult.error("导出失败");
                response.getWriter().write(JSON.toJSONString(errorResult));
            } catch (Exception ignored) {
                logger.error("[输出物导出-Markdown] 响应写出失败 - sessionId: {}", sessionId);
            }
        }
    }

    /**
     * 导出当前会话的输出物为 JSON 数据
     *
     * 设计说明：
     * 1. 返回标准结构数据，供前端展示或 Webhook 推送复用
     * 2. 业务失败时直接返回错误信息，不做文件流处理
     *
     * @param sessionId 会话ID
     * @return 输出物JSON结构
     */
    @PreAuthorize("@ss.hasPermi('business:output:exportJson')")
    @GetMapping("/output/exportJson/{sessionId}")
    @Log(title = "导出输出物JSON", businessType = BusinessType.EXPORT)
    public AjaxResult exportJson(@PathVariable String sessionId) {
        try {
            Map<String, Object> result = aigcService.exportOutputArtifactJson(sessionId);

            // 业务失败时直接透传错误信息
            if (Boolean.FALSE.equals(result.get("success"))) {
                return AjaxResult.error(String.valueOf(result.get("message")));
            }

            return AjaxResult.success(result);

        } catch (Exception e) {
            logger.error("[输出物导出-JSON] 接口异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    sessionId, e.getClass().getSimpleName(), e.getMessage());
            return AjaxResult.error("导出JSON失败: " + e.getMessage());
        }
    }

    /**
     * 推送当前会话的输出物到已登记的企业 Webhook
     *
     * 设计说明：
     * 1. 前端只传入 sessionId、format、enterpriseId 和 webhookId，不接收原始密钥
     * 2. 推送逻辑依赖导出结果，保证推送内容与导出数据一致
     * 3. 参数校验在入口完成，避免无效请求进入推送流程
     *
     * @param params 请求参数（包含 sessionId、format、enterpriseId、webhookId、idempotencyKey）
     * @return 推送结果
     */
    @PreAuthorize("@ss.hasPermi('business:output:pushWebhook')")
    @PostMapping("/output/pushWebhook")
    @Log(title = "推送输出物到Webhook", businessType = BusinessType.OTHER, isSaveRequestData = false)
    public AjaxResult pushWebhook(@RequestBody Map<String, Object> params) {
        try {
            String sessionId = (String) params.get("sessionId");
            String format = (String) params.get("format");
            Number enterpriseIdValue = (Number) params.get("enterpriseId");
            Number webhookIdValue = (Number) params.get("webhookId");
            String idempotencyKey = (String) params.get("idempotencyKey");

            // 关键参数缺失时直接拦截，避免进入后续推送逻辑
            if (sessionId == null || sessionId.isEmpty()) {
                return AjaxResult.error("sessionId不能为空");
            }
            if (format == null || format.isEmpty()) {
                return AjaxResult.error("format不能为空");
            }
            if (enterpriseIdValue == null || webhookIdValue == null) {
                return AjaxResult.error("企业和Webhook不能为空");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                return AjaxResult.error("idempotencyKey不能为空");
            }

            Map<String, Object> result =
                    aigcService.pushOutputArtifactWebhook(sessionId, format,
                            enterpriseIdValue.longValue(), webhookIdValue.longValue(),
                            idempotencyKey, com.wx.fbsir.common.utils.SecurityUtils.getUserId());

            return AjaxResult.success(result);

        } catch (Exception e) {
            logger.error("[输出物推送] 接口异常 - sessionId: {}, format: {}, enterpriseId: {}, webhookId: {}, 错误类型: {}",
                    params.get("sessionId"), params.get("format"), params.get("enterpriseId"), params.get("webhookId"),
                    e.getClass().getSimpleName());
            return AjaxResult.error("推送Webhook失败");
        }
    }

    /**
     * 保存用户编辑后的输出物内容
     *
     * 设计说明：
     * 1. 前端提交 sessionId、artifactId 及编辑后的内容，由后端定位目标输出物并回写
     * 2. 保存后会覆盖当前会话中的对应输出物，保证后续导出与推送使用最新内容
     * 3. 关键参数在入口完成校验，避免无效请求进入保存流程
     *
     * @param params 请求参数（包含 sessionId、artifactId、content 等）
     * @return 保存结果
     */
    @PreAuthorize("@ss.hasPermi('business:output:save')")
    @PostMapping("/output/save")
    @Log(title = "保存输出物", businessType = BusinessType.UPDATE)
    public AjaxResult saveOutputArtifact(@RequestBody Map<String, Object> params) {
        try {
            String sessionId = (String) params.get("sessionId");
            String artifactId = (String) params.get("artifactId");
            String content = (String) params.get("content");

            // 保存操作依赖会话与输出物定位信息，关键字段缺失时不能继续处理
            if (sessionId == null || sessionId.isEmpty()) {
                return AjaxResult.error("sessionId不能为空");
            }
            if (artifactId == null || artifactId.isEmpty()) {
                return AjaxResult.error("artifactId不能为空");
            }
            if (content == null || content.isEmpty()) {
                return AjaxResult.error("输出物内容不能为空");
            }

            Map<String, Object> result = aigcService.saveOutputArtifact(params);

            // 保存成功后返回最新输出物数据，便于前端直接刷新展示
            if (Boolean.TRUE.equals(result.get("success"))) {
                return AjaxResult.success("保存成功", result);
            }

            // 业务失败时透传具体错误信息
            return AjaxResult.error(String.valueOf(result.get("message")));

        } catch (Exception e) {
            logger.error("[输出物保存] 接口异常 - sessionId: {}, artifactId: {}, 错误类型: {}, 错误信息: {}",
                    params.get("sessionId"), params.get("artifactId"),
                    e.getClass().getSimpleName(), e.getMessage());
            return AjaxResult.error("保存输出物失败: " + e.getMessage());
        }
    }
}
