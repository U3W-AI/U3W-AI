package com.wx.fbsir.business.dailyassistant.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * 腾讯元器智能体API调用服务
 *
 * API文档: https://yuanqi.tencent.com/openapi/v1/agent/chat/completions
 *
 * @author wxfbsir
 * @date 2025-12-05
 */
@Service
public class YuanqiAgentApiService {

    private static final Logger log = LoggerFactory.getLogger(YuanqiAgentApiService.class);

    private static final String API_URL = "https://yuanqi.tencent.com/openapi/v1/agent/chat/completions";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public YuanqiAgentApiService() {
        // 配置RestTemplate超时时间
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(30000);  // 连接超时：30秒
        factory.setReadTimeout(300000);    // 读取超时：300秒（5分钟，等待任务提交确认）

        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 调用腾讯元器智能体优化文章（异步工作流）
     *
     * 注意：此方法只负责提交任务到腾讯元器工作流，不等待内容生成完成。
     * 工作流会通过HTTP回调接口传递结果：
     * - /saveModelContent - 保存各模型生成的内容
     * - /updateOptimizedContent - 保存优化后的最终内容
     *
     * @param appKey API密钥（Bearer token）
     * @param appId 智能体ID（assistant_id）
     * @param userId 用户ID
     * @param articleTitle 文章标题
     * @param articleId 文章ID
     * @return 任务提交结果（成功返回taskId）
     */
    public YuanqiApiResponse optimizeArticle(String appKey, String appId, String userId, String articleTitle, Long articleId) {
        try {
            // 1. 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + appKey);

            // 2. 构建请求体
            Map<String, Object> requestBody = buildRequestBody(appId, userId, articleTitle, articleId);

            // 3. 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.debug("[元器API] 提交工作流任务 - AppId: {}, ArticleId: {}", appId, articleId);

            // 4. 发送请求（非流式）
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // 5. 解析响应（只需确认任务已提交，内容通过回调接收）
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.debug("[元器API] 任务提交成功，等待回调");

                return parseResponse(responseBody);
            } else {
                log.error("API调用失败，状态码: {}", response.getStatusCode());
                return YuanqiApiResponse.error("API调用失败，状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();

            // 特殊处理504 Gateway Timeout：任务可能已提交成功，只是网关等不及返回结果
            if (errorMsg != null && errorMsg.contains("504 Gateway Time-out")) {
                log.warn("收到504 Gateway Timeout，任务可能已提交成功，等待回调传递结果 - ArticleId: {}", articleId);
                log.warn("原因分析：腾讯元器网关超时（通常60秒），但工作流可能仍在后台执行");

                // 返回成功状态，让系统等待回调
                // 如果真的失败了，后续不会有回调，用户可以重试
                return YuanqiApiResponse.success("", "pending-" + articleId);
            }

            log.error("调用腾讯元器API异常", e);
            return YuanqiApiResponse.error("API调用异常: " + errorMsg);
        }
    }

    /**
     * 构建请求体
     * 根据腾讯元器API文档格式
     * 传递JSON格式的参数给智能体
     */
    private Map<String, Object> buildRequestBody(String appId, String userId, String articleTitle, Long articleId) {
        Map<String, Object> body = new HashMap<>();

        // 智能体ID
        body.put("assistant_id", appId);

        // 用户ID
        body.put("user_id", userId);

        // 非流式返回
        body.put("stream", false);

        // 构建消息数组
        List<Map<String, Object>> messages = new ArrayList<>();

        // 用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        // 消息内容数组 - 发送JSON格式的参数
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");

        // 构建JSON格式的消息内容
        String jsonMessage = buildJsonMessage(articleTitle, articleId);
        textContent.put("text", jsonMessage);
        contentList.add(textContent);

        userMessage.put("content", contentList);
        messages.add(userMessage);

        body.put("messages", messages);

        return body;
    }

    /**
     * 构建JSON格式的消息内容
     * 工作流中可以使用参数提取节点解析这个JSON
     */
    private String buildJsonMessage(String articleTitle, Long articleId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("articleTitle", articleTitle);
            params.put("articleId", articleId);

            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.error("构建JSON消息失败", e);
            // 如果JSON构建失败，返回简单的字符串格式
            return String.format("{\"articleTitle\":\"%s\",\"articleId\":%d}", articleTitle, articleId);
        }
    }


    /**
     * 解析API响应
     *
     * 异步工作流模式：只需确认任务已提交（有taskId），内容通过回调接收
     */
    private YuanqiApiResponse parseResponse(String responseBody) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            // 获取id（请求ID，作为taskId）
            String taskId = (String) response.get("id");

            if (taskId == null || taskId.isEmpty()) {
                return YuanqiApiResponse.error("API响应缺少taskId");
            }

            // 异步模式：任务已提交成功，返回taskId，不等待内容
            log.info("[元器API] 任务已提交 - TaskId: {}, 等待回调", taskId);
            return YuanqiApiResponse.success("", taskId);

        } catch (Exception e) {
            log.error("解析API响应失败", e);
            return YuanqiApiResponse.error("解析响应失败: " + e.getMessage());
        }
    }

    /**
     * 调用腾讯元器智能体进行文章排版（同步返回结果）
     *
     * 此方法会等待工作流执行完成并返回排版后的内容
     *
     * @param appKey API密钥（Bearer token）
     * @param appId 智能体ID（assistant_id，用于排版的工作流）
     * @param userId 用户ID
     * @param content 原始文章内容
     * @return 排版结果（包含排版后的内容）
     */
    public YuanqiApiResponse layoutArticleSync(String appKey, String appId, String userId, String content) {
        try {
            // 1. 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + appKey);

            // 2. 构建请求体（简化版，不包含articleId）
            Map<String, Object> requestBody = buildLayoutSyncRequestBody(appId, userId, content);

            // 3. 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.debug("[元器排版] 提交同步任务 - AppId: {}, 内容长度: {}", appId, content.length());

            // 4. 发送请求（非流式，等待响应）
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // 5. 解析响应并提取排版后的内容
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.debug("[元器排版] 任务执行完成");

                return parseLayoutResponse(responseBody);
            } else {
                log.error("同步排版API调用失败，状态码: {}", response.getStatusCode());
                return YuanqiApiResponse.error("排版API调用失败，状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            log.error("调用腾讯元器同步排版API异常", e);
            return YuanqiApiResponse.error("排版API调用异常: " + errorMsg);
        }
    }

    /**
     * 构建同步排版请求体
     * 与优化文章一样，发送JSON格式的参数给智能体
     */
    private Map<String, Object> buildLayoutSyncRequestBody(String appId, String userId, String content) {
        Map<String, Object> body = new HashMap<>();

        // 智能体ID
        body.put("assistant_id", appId);

        // 用户ID
        body.put("user_id", userId);

        // 非流式返回
        body.put("stream", false);

        // 构建消息数组
        List<Map<String, Object>> messages = new ArrayList<>();

        // 用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        // 消息内容数组 - 发送JSON格式的文章内容（与优化文章保持一致）
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");

        // 构建JSON格式的消息：{"content": "文章内容"}
        String jsonMessage = buildLayoutJsonMessage(content);
        textContent.put("text", jsonMessage);
        contentList.add(textContent);

        userMessage.put("content", contentList);
        messages.add(userMessage);

        body.put("messages", messages);

        return body;
    }

    /**
     * 构建排版用的JSON消息
     * 工作流中使用参数提取节点可以解析这个JSON获取文章内容
     */
    private String buildLayoutJsonMessage(String content) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("content", content);

            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.error("构建排版JSON消息失败", e);
            // 如果JSON构建失败，返回简单格式
            // 转义引号避免JSON格式错误
            String escapedContent = content.replace("\"", "\\\"").replace("\n", "\\n");
            return String.format("{\"content\":\"%s\"}", escapedContent);
        }
    }

    /**
     * 解析同步排版响应，提取排版后的内容
     */
    private YuanqiApiResponse parseLayoutResponse(String responseBody) {
        try {
            // 检查响应是否是HTML而不是JSON
            if (responseBody != null && responseBody.trim().startsWith("<!DOCTYPE") ||
                    (responseBody != null && responseBody.trim().startsWith("<html"))) {
                log.error("API返回了HTML页面而不是JSON，可能是认证失败或URL错误");
                log.error("响应内容前200字符: {}", responseBody.substring(0, Math.min(200, responseBody.length())));
                return YuanqiApiResponse.error("API调用失败：收到HTML响应而不是JSON，请检查智能体配置和API密钥");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);

            // 从响应中提取内容
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> firstChoice = choices.get(0);
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");

                if (message != null) {
                    // 提取content字段
                    Object contentObj = message.get("content");
                    String layoutedContent = "";

                    if (contentObj instanceof String) {
                        layoutedContent = (String) contentObj;
                    } else if (contentObj instanceof List) {
                        // 如果content是数组，拼接所有文本
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> contentArray = (List<Map<String, Object>>) contentObj;
                        StringBuilder sb = new StringBuilder();
                        for (Map<String, Object> item : contentArray) {
                            if ("text".equals(item.get("type"))) {
                                sb.append(item.get("text"));
                            }
                        }
                        layoutedContent = sb.toString();
                    }

                    log.info("[元器排版] 成功 - 输出: {} 字符", layoutedContent.length());
                    return YuanqiApiResponse.success(layoutedContent, null);
                }
            }

            log.error("API响应中未找到排版内容，完整响应: {}", responseBody);
            return YuanqiApiResponse.error("未找到排版内容");

        } catch (Exception e) {
            log.error("解析排版响应失败，响应内容: {}", responseBody);
            log.error("解析异常详情", e);
            return YuanqiApiResponse.error("解析响应失败: " + e.getMessage() + "，请检查智能体配置");
        }
    }

    /**
     * 调用腾讯元器智能体解析文档（异步工作流）
     *
     * 注意：此方法只负责提交任务到腾讯元器工作流，不等待内容生成完成。
     * 工作流会通过HTTP回调接口传递结果：
     * - /updateParsedContent - 保存解析后的内容
     *
     * @param appKey API密钥（Bearer token）
     * @param appId 智能体ID（assistant_id）
     * @param userId 用户ID
     * @param documentId 文档ID
     * @param prompt 提示词
     * @param url 文件访问URL（已替换域名）
     * @param originalFilename 原始文件名
     * @return 任务提交结果（成功返回taskId）
     */
    public YuanqiApiResponse parseDocument(String appKey, String appId, String userId,
                                           String documentId, String prompt, String url, String originalFilename) {
        try {
            // 1. 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + appKey);

            // 2. 构建请求体
            Map<String, Object> requestBody = buildDocumentParseRequestBody(appId, userId, documentId, prompt, url, originalFilename);

            // 3. 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            log.debug("[元器API] 提交文档解析工作流任务 - AppId: {}, DocumentId: {}", appId, documentId);

            // 4. 发送请求（非流式）
            ResponseEntity<String> response = restTemplate.exchange(
                    API_URL,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            // 5. 解析响应（只需确认任务已提交，内容通过回调接收）
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.debug("[元器API] 文档解析任务提交成功，等待回调");

                return parseResponse(responseBody);
            } else {
                log.error("文档解析API调用失败，状态码: {}", response.getStatusCode());
                return YuanqiApiResponse.error("API调用失败，状态码: " + response.getStatusCode());
            }

        } catch (Exception e) {
            String errorMsg = e.getMessage();

            // 特殊处理504 Gateway Timeout：任务可能已提交成功，只是网关等不及返回结果
            if (errorMsg != null && errorMsg.contains("504 Gateway Time-out")) {
                log.warn("收到504 Gateway Timeout，任务可能已提交成功，等待回调传递结果 - DocumentId: {}", documentId);
                log.warn("原因分析：腾讯元器网关超时（通常60秒），但工作流可能仍在后台执行");

                // 返回成功状态，让系统等待回调
                // 如果真的失败了，后续不会有回调，用户可以重试
                return YuanqiApiResponse.success("", "pending-" + documentId);
            }

            log.error("调用腾讯元器文档解析API异常", e);
            return YuanqiApiResponse.error("API调用异常: " + errorMsg);
        }
    }

    /**
     * 构建文档解析请求体
     * 根据腾讯元器API文档格式
     * 传递JSON格式的参数给智能体：documentId, prompt, url, originalFilename
     */
    private Map<String, Object> buildDocumentParseRequestBody(String appId, String userId,
                                                              String documentId, String prompt, String url, String originalFilename) {
        Map<String, Object> body = new HashMap<>();

        // 智能体ID
        body.put("assistant_id", appId);

        // 用户ID
        body.put("user_id", userId);

        // 非流式返回
        body.put("stream", false);

        // 构建消息数组
        List<Map<String, Object>> messages = new ArrayList<>();

        // 用户消息
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");

        // 消息内容数组 - 发送JSON格式的参数
        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textContent = new HashMap<>();
        textContent.put("type", "text");

        // 构建JSON格式的消息内容
        String jsonMessage = buildDocumentParseJsonMessage(documentId, prompt, url, originalFilename);
        textContent.put("text", jsonMessage);
        contentList.add(textContent);

        userMessage.put("content", contentList);
        messages.add(userMessage);

        body.put("messages", messages);

        return body;
    }

    /**
     * 构建文档解析用的JSON格式消息内容
     * 工作流中可以使用参数提取节点解析这个JSON
     */
    private String buildDocumentParseJsonMessage(String documentId, String prompt, String url, String originalFilename) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("documentId", documentId);
            params.put("prompt", prompt);
            params.put("url", url);
            params.put("originalFilename", originalFilename);

            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.error("构建文档解析JSON消息失败", e);
            // 如果JSON构建失败，返回简单的字符串格式
            // 转义特殊字符避免JSON格式错误
            String escapedPrompt = prompt != null ? prompt.replace("\"", "\\\"").replace("\n", "\\n") : "";
            String escapedUrl = url != null ? url.replace("\"", "\\\"") : "";
            String escapedFilename = originalFilename != null ? originalFilename.replace("\"", "\\\"") : "";
            return String.format("{\"documentId\":\"%s\",\"prompt\":\"%s\",\"url\":\"%s\",\"originalFilename\":\"%s\"}",
                    documentId, escapedPrompt, escapedUrl, escapedFilename);
        }
    }

    /**
     * API响应包装类
     */
    public static class YuanqiApiResponse {
        private boolean success;
        private String content;
        private String taskId;
        private String errorMessage;

        public static YuanqiApiResponse success(String content, String taskId) {
            YuanqiApiResponse response = new YuanqiApiResponse();
            response.success = true;
            response.content = content;
            response.taskId = taskId;
            return response;
        }

        public static YuanqiApiResponse error(String errorMessage) {
            YuanqiApiResponse response = new YuanqiApiResponse();
            response.success = false;
            response.errorMessage = errorMessage;
            return response;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public String getContent() {
            return content;
        }

        public String getTaskId() {
            return taskId;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
