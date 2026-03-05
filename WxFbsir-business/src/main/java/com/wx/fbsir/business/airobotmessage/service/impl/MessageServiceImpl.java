package com.wx.fbsir.business.airobotmessage.service.impl;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import com.wx.fbsir.business.airobotmessage.mapper.MessageMapper;
import com.wx.fbsir.business.airobotmessage.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class MessageServiceImpl implements MessageService {

    @Value("${wechat.prompt-editor-url:${PROMPT_EDITOR_URL:}}")
    private String promptEditorUrl;
    @Autowired
    private MessageMapper messageMapper;

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
     * 向微信群推送文本消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @throws Exception 异常信息
     */
    @Override
    public void sendTextMessage(String userId, String messageContent) throws Exception {
        try {
            WecomWebhook wecomWebhook = messageMapper.selectWecomWebhookById(1L);
            String webhookUrl = wecomWebhook.getWebhookUrl();
            String json = java.lang.String.format(
                    "{\"msgtype\":\"text\",\"text\":{\"content\":\"用户名：%s\n内容：%s\n时间：%s\"}}",
                    userId, messageContent, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 向微信群推送模板卡片消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @throws Exception 异常信息
     */
    @Override
    public void sendTemplateMessage(String userId, String messageContent) throws Exception {
        try {
            WecomWebhook wecomWebhook = messageMapper.selectWecomWebhookById(1L);
            String webhookUrl = wecomWebhook.getWebhookUrl();
            String json = java.lang.String.format(
                    """
                            {
                                           "msgtype": "template_card",
                                           "template_card": {
                                             "card_type": "text_notice",
                                             "main_title": {"title": "新咨询需求"},
                                             "sub_title_text": "用户名：%s\\n需求：%s",
                                             "horizontal_content_list": [
                                               {"keyname": "时间", "value": "%s"}
                                             ],
                                             "card_action": {"type": 1, "url": "https://work.weixin.qq.com"}
                                           }
                                         }""",
                    userId, messageContent, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 向微信群推送修改提示词模板卡片消息
     *
     * @param userId         用户名
     * @throws Exception 异常信息
     */
    @Override
    public void sendTemplatePromptMessage(String userId) throws Exception {
        try {
            WecomWebhook wecomWebhook = messageMapper.selectWecomWebhookById(1L);
            String webhookUrl = wecomWebhook.getWebhookUrl();
            String json = java.lang.String.format(
                    """
                            {
                                          "msgtype": "template_card",
                                          "template_card": {
                                            "card_type": "text_notice",
                                            "main_title": {"title": "修改提示词请求"},
                                            "sub_title_text": "用户名：%s\\n点击卡片进入修改页面",
                                            "horizontal_content_list": [
                                              {"keyname": "时间", "value": "%s"}
                                            ],
                                            "card_action": {"type": 1, "url": "%s"}
                                          }
                                        }""",
                    userId, LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), promptEditorUrl
            );

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
