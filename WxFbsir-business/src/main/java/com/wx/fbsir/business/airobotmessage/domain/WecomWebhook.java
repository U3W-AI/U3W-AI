package com.wx.fbsir.business.airobotmessage.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import java.time.LocalDateTime;

/**
 * 企业微信 Webhook 配置实体类
 * 对应表 wc_webhook_url
 */

@Table(name = "wc_webhook_url")
@Getter
public class WecomWebhook {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Webhook 名称，唯一
     */
    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /**
     * 企业微信 Webhook 地址
     */
    @Column(name = "webhook_url", nullable = false, length = 512)
    private String webhookUrl;

    /**
     * 描述信息
     */
    @Column(length = 255)
    private String description;

    /**
     * 状态：1-启用，0-禁用
     */
    @Column(nullable = false)
    private Boolean status = true;

    /**
     * 创建时间，由数据库自动生成，插入后不可更新
     */
    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间，由数据库自动更新
     */
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    public String getWebhookUrl() {
        return webhookUrl;
    }
}