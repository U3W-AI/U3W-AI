package com.wx.fbsir.business.airobotmessage.service.impl;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import com.wx.fbsir.business.airobotmessage.mapper.MessageMapper;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Service
public class MessageServiceImpl implements MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageServiceImpl.class);
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${wechat.prompt-editor-url:${PROMPT_EDITOR_URL:}}")
    private String promptEditorUrl;
    @Autowired
    private MessageMapper messageMapper;

    @Override
    public List<WecomWebhook> selectWecomWebhookList() {
        return messageMapper.selectWecomWebhookList();
    }

    /**
     * 根据 ID 查询企业微信 Webhook
     *
     * @param id Webhook ID
     * @return Webhook 信息
     *
     */
    @Override
    public WecomWebhook selectWecomWebhookById(Long id) {
        return messageMapper.selectWecomWebhookById(id);
    }

    /**
     * 插入企业微信 Webhook
     *
     * @param wecomWebhook Webhook 信息
     * @return 插入结果
     */
    @Override
    public int insertWecomWebhook(WecomWebhook wecomWebhook) {
        return messageMapper.insertWecomWebhook(wecomWebhook);
    }

    /**
     * 更新企业微信 Webhook
     *
     * @param wecomWebhook Webhook 信息
     * @return 更新结果
     */
    @Override
    public boolean updateWecomWebhook(WecomWebhook wecomWebhook) {
        return messageMapper.updateWecomWebhook(wecomWebhook);
    }

    /**
     * 删除企业微信 Webhook
     *
     * @param id Webhook ID
     * @return 删除结果
     */
    @Override
    public int deleteWecomWebhookById(Long id) {
        return messageMapper.deleteWecomWebhookById(id);
    }

    /**
     * 发送 HTTP 请求
     *
     * @param webhookUrl Webhook 地址
     * @param json       请求体
     * @throws Exception 异常信息
     */
    private void sendHttpRequest(String webhookUrl, String json) throws Exception {
        int maxRetries = 1; // 最多重试1次
        int retries = 0;
        
        while (retries <= maxRetries) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                return; // 发送成功，直接返回
            } catch (Exception e) {
                retries++;
                if (retries > maxRetries) {
                    // 超过最大重试次数，抛出异常
                    logger.error("发送消息失败，已达到最大重试次数", e);
                    throw e;
                }
                // 记录重试信息
                logger.warn("发送消息失败，正在重试 ({}/{})", retries, maxRetries);
                // 等待1秒后重试
                Thread.sleep(1000);
            }
        }
    }

    /**
     * 向微信群推送文本消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     * @throws Exception 异常信息
     */
    @Override
    public void sendTextMessage(String userId, String messageContent, Long webhookId) throws Exception {
        logger.info("开始发送文本消息，用户ID：{}，Webhook ID：{}", userId, webhookId);
        
        if (webhookId == null) {
            throw new IllegalArgumentException("Webhook ID不能为空");
        }
        
        WecomWebhook wecomWebhook = messageMapper.selectWecomWebhookById(webhookId);
        if (wecomWebhook == null) {
            throw new IllegalArgumentException("Webhook不存在");
        }
        if (wecomWebhook.getWebhookUrl() == null || wecomWebhook.getWebhookUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook地址为空");
        }
        
        String webhookUrl = wecomWebhook.getWebhookUrl();
        String json = String.format(
                "{\"msgtype\":\"text\",\"text\":{\"content\":\"用户名：%s\n内容：%s\n时间：%s\"}}",
                userId, messageContent, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        sendHttpRequest(webhookUrl, json);
    }

    /**
     * 向微信群推送模板卡片消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     * @throws Exception 异常信息
     */
    @Override
    public void sendTemplateMessage(String userId, String messageContent, Long webhookId) throws Exception {
        logger.info("开始发送模板消息，用户ID：{}，Webhook ID：{}", userId, webhookId);
        
        if (webhookId == null) {
            throw new IllegalArgumentException("Webhook ID不能为空");
        }
        
        WecomWebhook wecomWebhook = messageMapper.selectWecomWebhookById(webhookId);
        if (wecomWebhook == null) {
            throw new IllegalArgumentException("Webhook不存在");
        }
        if (wecomWebhook.getWebhookUrl() == null || wecomWebhook.getWebhookUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook地址为空");
        }
        
        String webhookUrl = wecomWebhook.getWebhookUrl();
        String json = String.format(
                """
                        {
                                       "msgtype": "template_card",
                                       "template_card": {
                                         "card_type": "text_notice",
                                         "main_title": {"title": "新咨询需求"},
                                         "sub_title_text": "用户名：%s\n需求：%s",
                                         "horizontal_content_list": [
                                           {"keyname": "时间", "value": "%s"}
                                         ],
                                         "card_action": {"type": 1, "url": "https://work.weixin.qq.com"}
                                       }
                                     }""",
                userId, messageContent, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        sendHttpRequest(webhookUrl, json);
    }

    /**
     * 向微信群推送修改提示词模板卡片消息
     *
     * @param userId      用户名
     * @param webhookId   Webhook ID
     * @throws Exception 异常信息
     */
    @Override
    public void sendTemplatePromptMessage(String userId, Long webhookId) throws Exception {
        logger.info("开始发送提示词更新通知，用户ID：{}，Webhook ID：{}", userId, webhookId);
        
        if (webhookId == null) {
            throw new IllegalArgumentException("Webhook ID不能为空");
        }
        
        WecomWebhook wecomWebhook = messageMapper.selectWecomWebhookById(webhookId);
        if (wecomWebhook == null) {
            throw new IllegalArgumentException("Webhook不存在");
        }
        if (wecomWebhook.getWebhookUrl() == null || wecomWebhook.getWebhookUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Webhook地址为空");
        }
        
        String webhookUrl = wecomWebhook.getWebhookUrl();
        String json = String.format(
                """
                        {
                                      "msgtype": "template_card",
                                      "template_card": {
                                        "card_type": "text_notice",
                                        "main_title": {"title": "修改提示词请求"},
                                        "sub_title_text": "用户名：%s\n点击卡片进入修改页面",
                                        "horizontal_content_list": [
                                          {"keyname": "时间", "value": "%s"}
                                        ],
                                        "card_action": {"type": 1, "url": "%s"}
                                      }
                                    }""",
                userId, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), promptEditorUrl
        );

        sendHttpRequest(webhookUrl, json);
    }

    /**
     * 异步向微信群推送文本消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     */
    @Override
    public void sendTextMessageAsync(String userId, String messageContent, Long webhookId) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTextMessage(userId, messageContent, webhookId);
            } catch (Exception e) {
                logger.error("异步发送文本消息失败", e);
            }
        });
    }

    /**
     * 异步向微信群推送模板卡片消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     */
    @Override
    public void sendTemplateMessageAsync(String userId, String messageContent, Long webhookId) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTemplateMessage(userId, messageContent, webhookId);
            } catch (Exception e) {
                logger.error("异步发送模板消息失败", e);
            }
        });
    }

    /**
     * 异步向微信群推送修改提示词模板卡片消息
     *
     * @param userId    用户名
     * @param webhookId Webhook ID
     */
    @Override
    public void sendTemplatePromptMessageAsync(String userId, Long webhookId) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTemplatePromptMessage(userId, webhookId);
            } catch (Exception e) {
                logger.error("异步发送提示词更新通知失败", e);
            }
        });
    }
}