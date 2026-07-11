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
public class WecomWebhook {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Enterprise scope. Every read/write must include this value. */
    @Column(name = "enterprise_id", nullable = false)
    private Long enterpriseId;

    /**
     * Webhook 名称，唯一
     */
    @Column(nullable = false, length = 100, unique = true)
    private String name;

    /**
     * 企业微信 Webhook 地址
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "webhook_url", nullable = false, length = 512)
    private String webhookUrl;

    /** Encrypted or external secret reference; never serialized to clients. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(name = "webhook_secret_ref", length = 1024)
    private String webhookSecretRef;

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

    /** Optimistic-lock version for updates. */
    @Column(nullable = false)
    private Integer version = 1;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getWebhookSecretRef() { return webhookSecretRef; }
    public void setWebhookSecretRef(String webhookSecretRef) { this.webhookSecretRef = webhookSecretRef; }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
