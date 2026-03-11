package com.wx.fbsir.business.airobotmessage.service;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;

import java.util.List;

public interface MessageService {
    /**
     * 查询企业微信 Webhook 列表
     */
    List<WecomWebhook> selectWecomWebhookList();
    /**
     * 根据 ID 查询企业微信 Webhook
     *
     * @param id Webhook ID
     * @return Webhook 信息
     *
     */
    WecomWebhook selectWecomWebhookById(Long id);

    /**
     * 插入企业微信 Webhook
     *
     * @param wecomWebhook Webhook 信息
     * @return 插入结果
     */
    int insertWecomWebhook(WecomWebhook wecomWebhook);

    /**
     * 向微信群推送文本消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     * @throws Exception 异常信息
     */
    void sendTextMessage(String userId, String messageContent, Long webhookId) throws Exception;

    /**
     * 向微信群推送模板卡片消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     * @throws Exception 异常信息
     */
    void sendTemplateMessage(String userId, String messageContent, Long webhookId) throws Exception;

    /**
     * 更新企业微信 Webhook
     *
     * @param wecomWebhook Webhook 信息
     * @return 更新结果
     */
    boolean updateWecomWebhook(WecomWebhook wecomWebhook);

    /**
     * 删除企业微信 Webhook
     *
     * @param id Webhook ID
     * @return 删除结果
     */
    int deleteWecomWebhookById(Long id);

    /**
     * 向微信群推送修改提示词模板卡片消息
     *
     * @param userId      用户名
     * @param webhookId   Webhook ID
     * @throws Exception 异常信息
     */
    void sendTemplatePromptMessage(String userId, Long webhookId) throws Exception;

    /**
     * 异步向微信群推送文本消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     */
    void sendTextMessageAsync(String userId, String messageContent, Long webhookId);

    /**
     * 异步向微信群推送模板卡片消息
     *
     * @param userId         用户名
     * @param messageContent 用户消息
     * @param webhookId      Webhook ID
     */
    void sendTemplateMessageAsync(String userId, String messageContent, Long webhookId);

    /**
     * 异步向微信群推送修改提示词模板卡片消息
     *
     * @param userId    用户名
     * @param webhookId Webhook ID
     */
    void sendTemplatePromptMessageAsync(String userId, Long webhookId);
}
