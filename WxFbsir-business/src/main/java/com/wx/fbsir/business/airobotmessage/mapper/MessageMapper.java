package com.wx.fbsir.business.airobotmessage.mapper;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MessageMapper {
    /**
     * 查询企业微信 Webhook 列表
     */
    List<WecomWebhook> selectWecomWebhookList();
    /**
     * 查询企业微信 Webhook
     *
     * @param id Webhook ID
     * @return Webhook 信息
     */
    @Select("select * from wc_webhook_url where id = #{id}")
    WecomWebhook selectWecomWebhookById(Long id);

    /**
     * 插入企业微信 Webhook
     *
     * @param wecomWebhook Webhook 信息
     * @return 插入结果
     */
    @Insert("insert into wc_webhook_url (name, webhook_url, description, status) " +
            "values (#{name}, #{webhookUrl}, #{description}, #{status})")
    int insertWecomWebhook(WecomWebhook wecomWebhook);

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
    @Delete("delete from wc_webhook_url where id = #{id}")
    int deleteWecomWebhookById(Long id);
}