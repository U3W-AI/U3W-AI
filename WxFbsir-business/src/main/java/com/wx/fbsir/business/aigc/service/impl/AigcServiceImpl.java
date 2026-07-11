package com.wx.fbsir.business.aigc.service.impl;

import com.wx.fbsir.business.aigc.domain.AiRequest;
import com.wx.fbsir.business.aigc.domain.ChatHistoryRequest;
import com.wx.fbsir.business.aigc.mapper.AigcMapper;
import com.wx.fbsir.business.aigc.service.IAigcService;
import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import java.time.LocalDateTime;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



/**
 * AIGC服务实现类
 * 
 * @author wxfbsir
 * @date 2026-01-07
 */
@Service
public class AigcServiceImpl implements IAigcService {

    private static final Logger log = LoggerFactory.getLogger(AigcServiceImpl.class);

    @Autowired
    private AigcMapper aigcMapper;

    @Autowired
    private MessageService messageService;

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
    
    @Override
    public String getDraftContent(Long userId, String taskId, String aiName) {
        return aigcMapper.getDraftContent(userId, taskId, aiName);
    }

    /**
     * 基于当前会话生成输出物并回写到会话 data 字段
     *
     * 设计说明：
     * 1. 输出物以当前会话为粒度生成，只使用本次会话中的 userPrompt 和 answer
     * 2. 输出物写回 wc_chat_history.data.data.outputArtifacts，避免新增表带来的联动成本
     * 3. 生成前统一校验会话有效性、归属、数据格式与回答内容，避免脏数据落库
     *
     * @param sessionId 会话ID
     * @return 统一返回结构：success / message / data
     */
    @Override
    public Map<String, Object> generateOutputArtifact(String sessionId) {
        Map<String, Object> result = new HashMap<>();

        // 先校验会话是否存在且归属于当前用户，避免通过非法 sessionId 操作他人会话
        Map<String, Object> chatData = getChatBySessionId(sessionId);
        if (chatData == null) {
            result.put("success", false);
            result.put("message", "当前会话不存在");
            result.put("data", null);
            return result;
        }
        if (!isSessionOwner(chatData)) {
            result.put("success", false);
            result.put("message", "无权访问该会话");
            result.put("data", null);
            return result;
        }

        String userPrompt = (String) chatData.get("userPrompt");
        String dataStr = (String) chatData.get("data");

        // data 字段承载完整会话结果，生成输出物前必须先保证 JSON 可解析，否则不能继续写回
        Map<String, Object> dataMap;
        try {
            dataMap = dataStr != null ? JSON.parseObject(dataStr) : new HashMap<>();
        } catch (Exception e) {
            log.error("[输出物生成] 会话数据格式异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    sessionId, e.getClass().getSimpleName(), e.getMessage());
            result.put("success", false);
            result.put("message", "会话数据格式异常");
            result.put("data", null);
            return result;
        }

        // 输出物统一挂载在 data.data 节点下；若节点不存在则补齐，保证历史数据也能兼容
        Map<String, Object> innerData = (Map<String, Object>) dataMap.get("data");
        if (innerData == null) {
            innerData = new HashMap<>();
            dataMap.put("data", innerData);
        }

        // answer 为空说明当前对话尚未形成有效结果，此时生成输出物没有业务意义
        String answer = (String) innerData.get("answer");
        if (answer == null) {
            result.put("success", false);
            result.put("message", "请先完成对话后再生成输出物");
            result.put("data", null);
            return result;
        }

        // 当前版本采用规则生成，先产出一份标准结构，后续如接入大模型可在此处平滑替换
        Map<String, Object> artifact = new HashMap<>();
        artifact.put("id", "art-" + System.currentTimeMillis());
        artifact.put("type", "decision_summary");
        artifact.put("title", "本期结论");
        artifact.put("content", "问题：" + userPrompt + "\n结论摘要：" + answer);
        artifact.put("createdAt", LocalDateTime.now().toString());

        // MVP 仅保留当前最新输出物，重新生成时直接覆盖，避免同一会话下多份输出物难以管理
        List<Map<String, Object>> outputArtifacts = new ArrayList<>();
        outputArtifacts.add(artifact);
        innerData.put("outputArtifacts", outputArtifacts);

        // 输出物生成完成后回写到会话 data 字段，保持会话数据与页面展示一致
        chatData.put("data", JSON.toJSONString(dataMap));
        updateChatData(chatData);

        log.info("[输出物生成] 生成成功 - sessionId: {}, artifactId: {}", sessionId, artifact.get("id"));

        result.put("success", true);
        result.put("message", "生成成功");
        result.put("data", artifact);
        return result;
    }

    /**
     * 导出当前会话的输出物为 Markdown 文本
     *
     * 设计说明：
     * 1. 输出物数据存储于 wc_chat_history.data.data.outputArtifacts 中
     * 2. 导出仅取当前会话的第一条输出物（MVP阶段只保留一份）
     * 3. 导出前统一校验会话有效性、归属、数据结构及输出物内容，避免无效导出
     * 4. 返回统一结构 {success, message, data}，其中 data 为 Markdown 字符串
     *
     * @param sessionId 会话ID
     * @return 导出结果（Markdown 文本或错误信息）
     */
    @Override
    public Map<String, Object> exportOutputArtifactMarkdown(String sessionId) {
        Map<String, Object> result = new HashMap<>();

        // 校验会话是否存在及归属，防止非法访问或跨用户操作
        Map<String, Object> chatData = getChatBySessionId(sessionId);
        if (chatData == null) {
            result.put("success", false);
            result.put("message", "当前会话不存在");
            result.put("data", null);
            return result;
        }
        if (!isSessionOwner(chatData)) {
            result.put("success", false);
            result.put("message", "无权访问该会话");
            result.put("data", null);
            return result;
        }

        String dataStr = (String) chatData.get("data");

        // data 字段为会话完整结构，导出前必须保证 JSON 可解析，否则无法继续读取输出物
        Map<String, Object> dataMap;
        try {
            dataMap = dataStr != null ? JSON.parseObject(dataStr) : new HashMap<>();
        } catch (Exception e) {
            log.error("[输出物导出-Markdown] 会话数据格式异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    sessionId, e.getClass().getSimpleName(), e.getMessage());
            result.put("success", false);
            result.put("message", "会话数据格式异常");
            result.put("data", null);
            return result;
        }

        // 输出物统一挂载在 data.data 节点；若节点缺失说明该会话尚未生成输出物
        Map<String, Object> innerData = (Map<String, Object>) dataMap.get("data");
        if (innerData == null) {
            result.put("success", false);
            result.put("message", "当前会话没有输出物");
            result.put("data", null);
            return result;
        }

        // 获取输出物列表（MVP阶段仅维护一条输出物）
        List<Map<String, Object>> outputArtifacts =
                (List<Map<String, Object>>) innerData.get("outputArtifacts");
        if (outputArtifacts == null || outputArtifacts.isEmpty()) {
            result.put("success", false);
            result.put("message", "当前会话没有输出物");
            result.put("data", null);
            return result;
        }

        Map<String, Object> artifact = outputArtifacts.get(0);
        String content = (String) artifact.get("content");

        // 内容为空说明输出物尚未形成有效结果，此时导出无业务意义
        if (content == null || content.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "当前输出物内容为空");
            result.put("data", null);
            return result;
        }

        String createdAt = String.valueOf(artifact.get("createdAt"));

        // 构造标准 Markdown 结构，包含元信息（sessionId、生成时间、来源）及正文内容
        String markdown = "---\n"
                + "session_id: " + sessionId + "\n"
                + "generated_at: " + createdAt + "\n"
                + "source: 福帮手\n"
                + "---\n\n"
                + "## 决策/结论摘要\n\n"
                + content;

        log.info("[输出物导出-Markdown] 导出成功 - sessionId: {}, artifactId: {}",
                sessionId, artifact.get("id"));

        result.put("success", true);
        result.put("message", "导出成功");
        result.put("data", markdown);
        return result;
    }

    /**
     * 导出当前会话的输出物为 JSON 数据结构
     *
     * 设计说明：
     * 1. 与 Markdown 导出保持统一返回结构 {success, message, data}
     * 2. data 中封装输出物核心字段（title、content、type、generatedAt），便于前端展示与 Webhook 复用
     * 3. 仅返回当前会话的第一条输出物（MVP阶段单输出物设计）
     * 4. 导出前统一校验会话有效性、归属及数据结构完整性，避免无效数据输出
     *
     * @param sessionId 会话ID
     * @return 导出结果（JSON结构或错误信息）
     */
    @Override
    public Map<String, Object> exportOutputArtifactJson(String sessionId) {
        Map<String, Object> result = new HashMap<>();

        // 校验会话是否存在及归属，防止越权访问或非法操作
        Map<String, Object> chatData = getChatBySessionId(sessionId);
        if (chatData == null) {
            result.put("success", false);
            result.put("message", "当前会话不存在");
            result.put("data", null);
            return result;
        }
        if (!isSessionOwner(chatData)) {
            result.put("success", false);
            result.put("message", "无权访问该会话");
            result.put("data", null);
            return result;
        }

        String dataStr = (String) chatData.get("data");

        // data 字段为会话完整结构，必须保证 JSON 可解析，否则无法获取输出物
        Map<String, Object> dataMap;
        try {
            dataMap = dataStr != null ? JSON.parseObject(dataStr) : new HashMap<>();
        } catch (Exception e) {
            log.error("[输出物导出-JSON] 会话数据格式异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    sessionId, e.getClass().getSimpleName(), e.getMessage());
            result.put("success", false);
            result.put("message", "会话数据格式异常");
            result.put("data", null);
            return result;
        }

        // 输出物统一挂载在 data.data 节点；节点缺失说明尚未生成输出物
        Map<String, Object> innerData = (Map<String, Object>) dataMap.get("data");
        if (innerData == null) {
            result.put("success", false);
            result.put("message", "当前会话没有输出物");
            result.put("data", null);
            return result;
        }

        // 获取输出物列表（MVP阶段仅保留一条输出物）
        List<Map<String, Object>> outputArtifacts =
                (List<Map<String, Object>>) innerData.get("outputArtifacts");
        if (outputArtifacts == null || outputArtifacts.isEmpty()) {
            result.put("success", false);
            result.put("message", "当前会话没有输出物");
            result.put("data", null);
            return result;
        }

        Map<String, Object> artifact = outputArtifacts.get(0);

        // 构造标准输出结构，便于前端与Webhook直接消费
        Map<String, Object> data = new HashMap<>();
        data.put("sessionId", sessionId);
        data.put("type", artifact.get("type"));
        data.put("title", artifact.get("title"));
        data.put("content", artifact.get("content"));
        data.put("generatedAt", artifact.get("createdAt"));

        log.info("[输出物导出-JSON] 导出成功 - sessionId: {}, artifactId: {}",
                sessionId, artifact.get("id"));

        result.put("success", true);
        result.put("message", "导出成功");
        result.put("data", data);
        return result;
    }

    /**
     * 将当前会话的输出物推送到租户范围内已登记的 Webhook
     *
     * 设计说明：
     * 1. 推送仅允许操作当前用户自己的会话，避免通过伪造 sessionId 推送他人数据
     * 2. 推送内容复用现有导出结果，避免重复拼装输出物结构，保证导出与推送口径一致
     * 3. 统一复用 MessageService 的租户管理员校验、密钥解密、幂等台账和审计回执
     *
     * @param sessionId 会话ID
     * @param format 推送格式，md 表示复用 Markdown 导出结果，其他情况默认复用 JSON 导出结果
     * @param enterpriseId 企业ID
     * @param webhookId 已登记的Webhook ID
     * @param idempotencyKey 幂等键
     * @param userId 操作用户ID
     * @return 推送结果（success / message / data）
     */
    @Override
    public Map<String, Object> pushOutputArtifactWebhook(String sessionId, String format, Long enterpriseId,
                                                          Long webhookId, String idempotencyKey, Long userId) {
        Map<String, Object> result = new HashMap<>();

        // 校验会话是否存在及归属，防止越权访问或将他人会话内容推送到外部系统
        Map<String, Object> chatData = getChatBySessionId(sessionId);
        if (chatData == null) {
            result.put("success", false);
            result.put("message", "当前会话不存在");
            result.put("data", null);
            return result;
        }
        if (!isSessionOwner(chatData)) {
            result.put("success", false);
            result.put("message", "无权访问该会话");
            result.put("data", null);
            return result;
        }

        if (enterpriseId == null || webhookId == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            result.put("success", false);
            result.put("message", "Webhook目标或幂等键无效");
            result.put("data", null);
            return result;
        }

        try {
            String content;

            if ("md".equalsIgnoreCase(format)) {
                // Markdown 推送直接复用导出结果，确保导出内容与推送内容完全一致
                Map<String, Object> mdResult = exportOutputArtifactMarkdown(sessionId);
                if (Boolean.FALSE.equals(mdResult.get("success"))) {
                    result.put("success", false);
                    result.put("message", String.valueOf(mdResult.get("message")));
                    result.put("data", null);
                    return result;
                }
                Object md = mdResult.get("data");
                content = md == null ? "" : String.valueOf(md);
            } else {
                // JSON 推送复用标准输出结构，再转换为适合Webhook消费的文本内容
                Map<String, Object> jsonData = exportOutputArtifactJson(sessionId);
                if (Boolean.FALSE.equals(jsonData.get("success"))) {
                    result.put("success", false);
                    result.put("message", String.valueOf(jsonData.get("message")));
                    result.put("data", null);
                    return result;
                }

                Object dataObj = jsonData.get("data");
                Map<String, Object> jsonArtifact = (dataObj instanceof Map)
                        ? (Map<String, Object>) dataObj
                        : new HashMap<>();

                String title = jsonArtifact.get("title") == null ? "" : String.valueOf(jsonArtifact.get("title"));
                String artifactContent = jsonArtifact.get("content") == null ? "" : String.valueOf(jsonArtifact.get("content"));

                // 输出物对象存在但内容为空时，不再继续推送，避免向外部系统发送无意义数据
                if (artifactContent.trim().isEmpty()) {
                    result.put("success", false);
                    result.put("message", "当前输出物内容为空");
                    result.put("data", null);
                    return result;
                }

                content = "AI输出物\n\n" + title + "\n\n" + artifactContent;
            }

            WebhookDeliveryReceipt receipt = messageService.send(
                    new WebhookSendRequest(enterpriseId, webhookId, idempotencyKey,
                            "AI输出物", content, null), userId);
            boolean accepted = "PROVIDER_ACCEPTED".equals(receipt.status());
            result.put("success", accepted);
            result.put("message", accepted ? "企微接口已接受" : "投递状态：" + receipt.status());
            result.put("data", receipt);
            return result;

        } catch (Exception e) {
            log.error("[输出物推送] 推送异常 - sessionId: {}, enterpriseId: {}, webhookId: {}, 错误类型: {}",
                    sessionId, enterpriseId, webhookId, e.getClass().getSimpleName());

            result.put("success", false);
            result.put("message", "推送失败");
            result.put("data", null);
            return result;
        }
    }


    /**
     * 将任意对象安全转换为 JSON 字符串
     *
     * 设计说明：
     * 1. 封装统一的 JSON 序列化逻辑，避免在业务代码中重复调用 JSON.toJSONString
     * 2. 在序列化异常时进行兜底处理，返回空 JSON（{}），防止异常向上抛出影响主流程
     * 3. 适用于日志记录、Webhook 构造等对 JSON 字符串有要求但允许降级的场景
     *
     * @param obj 待转换对象
     * @return JSON 字符串，异常时返回 "{}"
     */
    private String convertToJsonString(Object obj) {
        try {
            return JSON.toJSONString(obj);
        } catch (Exception e) {
            log.error("[JSON转换] 序列化失败 - 错误类型: {}, 错误信息: {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return "{}";
        }
    }

    /**
     * 校验当前登录用户是否为会话所有者
     *
     * 设计说明：
     * 1. 用于限制会话数据的访问范围，防止通过伪造 sessionId 访问或修改其他用户的会话记录
     * 2. 会话数据中的 userId 来源于数据库，类型可能为 String 或 Number，需要统一转换后比较
     * 3. 任一关键字段缺失或类型转换失败时，均视为校验不通过，避免越权风险（安全优先策略）
     *
     * @param chatData 会话数据（包含 userId 字段）
     * @return true 表示当前用户为会话所有者，false 表示无权限
     */
    private boolean isSessionOwner(Map<String, Object> chatData) {
        // 获取当前登录用户ID（来源于安全上下文）
        Long currentUserId = SecurityUtils.getUserId();

        // 会话所属用户ID（从数据库读取，类型不固定）
        Object sessionUserIdObj = chatData.get("userId");

        // 任一为空均视为非法数据或未登录状态，直接拒绝访问
        if (currentUserId == null || sessionUserIdObj == null) {
            return false;
        }

        try {
            // 统一转换为 Long 进行比较，避免类型不一致导致误判
            Long sessionUserId = Long.valueOf(String.valueOf(sessionUserIdObj));
            return currentUserId.equals(sessionUserId);
        } catch (Exception e) {
            // 类型转换失败说明数据异常，记录日志用于排查数据问题
            log.error("[会话权限校验] userId转换失败 - sessionUserIdObj: {}, 错误类型: {}, 错误信息: {}",
                    sessionUserIdObj, e.getClass().getSimpleName(), e.getMessage());
            return false;
        }
    }

    /**
     * 保存用户编辑后的输出物内容并回写到当前会话
     *
     * 设计说明：
     * 1. 输出物编辑结果直接回写到 wc_chat_history.data.data.outputArtifacts，保持会话数据与页面展示一致
     * 2. 保存前统一校验参数、会话归属、数据结构及输出物存在性，避免脏数据写入
     * 3. 当前版本按 artifactId 定位目标输出物，仅更新 title 与 content 字段，减少对其他结构的影响
     * 4. 若未找到对应输出物，则直接返回失败，避免误更新其他输出物
     *
     * @param params 保存参数，包含 sessionId、artifactId、title、content
     * @return 保存结果（success / message / data）
     */
    @Override
    public Map<String, Object> saveOutputArtifact(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();

        String sessionId = (String) params.get("sessionId");
        String artifactId = (String) params.get("artifactId");
        String title = (String) params.get("title");
        String content = (String) params.get("content");

        // 保存属于用户主动编辑行为，关键标识与内容不能为空，否则无法准确定位并更新目标输出物
        if (sessionId == null || sessionId.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "sessionId不能为空");
            result.put("data", null);
            return result;
        }

        if (artifactId == null || artifactId.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "artifactId不能为空");
            result.put("data", null);
            return result;
        }

        if (content == null || content.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "输出物内容不能为空");
            result.put("data", null);
            return result;
        }

        // 校验会话是否存在及归属，防止通过伪造参数修改其他用户会话中的输出物
        Map<String, Object> chatData = getChatBySessionId(sessionId);
        if (chatData == null) {
            result.put("success", false);
            result.put("message", "当前会话不存在");
            result.put("data", null);
            return result;
        }
        if (!isSessionOwner(chatData)) {
            result.put("success", false);
            result.put("message", "无权访问该会话");
            result.put("data", null);
            return result;
        }

        String dataStr = (String) chatData.get("data");

        // 会话完整数据保存在 data 字段中，保存前必须先保证 JSON 可解析，否则无法安全修改输出物内容
        Map<String, Object> dataMap;
        try {
            dataMap = dataStr != null ? JSON.parseObject(dataStr) : new HashMap<>();
        } catch (Exception e) {
            log.error("[输出物保存] 会话数据格式异常 - sessionId: {}, 错误类型: {}, 错误信息: {}",
                    sessionId, e.getClass().getSimpleName(), e.getMessage());
            result.put("success", false);
            result.put("message", "会话数据格式异常");
            result.put("data", null);
            return result;
        }

        // 输出物统一存放在 data.data.outputArtifacts 节点，节点缺失说明当前会话尚未生成输出物
        Map<String, Object> innerData = (Map<String, Object>) dataMap.get("data");
        if (innerData == null) {
            result.put("success", false);
            result.put("message", "当前会话没有输出物");
            result.put("data", null);
            return result;
        }

        // 获取输出物列表；若列表为空，则说明当前会话不存在可编辑的输出物
        List<Map<String, Object>> outputArtifacts =
                (List<Map<String, Object>>) innerData.get("outputArtifacts");
        if (outputArtifacts == null || outputArtifacts.isEmpty()) {
            result.put("success", false);
            result.put("message", "当前会话没有输出物");
            result.put("data", null);
            return result;
        }

        // 按 artifactId 精确定位目标输出物，只更新允许用户编辑的字段，避免误修改其他数据
        boolean found = false;
        Map<String, Object> updatedArtifact = null;

        for (Map<String, Object> artifact : outputArtifacts) {
            String currentArtifactId = String.valueOf(artifact.get("id"));
            if (artifactId.equals(currentArtifactId)) {
                if (title != null && !title.trim().isEmpty()) {
                    artifact.put("title", title);
                }
                artifact.put("content", content);
                found = true;
                updatedArtifact = artifact;
                break;
            }
        }

        // 若未命中目标输出物，说明前端传入的 artifactId 与当前会话数据不匹配，直接返回失败
        if (!found) {
            result.put("success", false);
            result.put("message", "未找到对应输出物");
            result.put("data", null);
            return result;
        }

        // 输出物更新后立即回写会话数据，确保后续导出、推送和页面展示使用的是最新内容
        chatData.put("data", JSON.toJSONString(dataMap));
        updateChatData(chatData);

        log.info("[输出物保存] 保存成功 - sessionId: {}, artifactId: {}",
                sessionId, updatedArtifact.get("id"));

        result.put("success", true);
        result.put("message", "保存成功");
        result.put("data", updatedArtifact);
        return result;
    }


}
