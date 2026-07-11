package com.wx.fbsir.business.certificate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 认证申请模块配置
 *
 * @author fbsir
 * @date 2026-01-08
 */
@Component
@ConfigurationProperties(prefix = "certificate.application")
public class CertificateApplicationConfig {

    /**
     * 申请草稿自动保存时间（分钟）
     */
    private int draftAutoSaveMinutes = 5;

    /**
     * 申请提交后允许撤销的时间（分钟）
     */
    private int withdrawAllowedMinutes = 30;

    /**
     * 审核超时时间（天）
     */
    private int reviewTimeoutDays = 7;

    /**
     * 证书有效期（天）
     */
    private int certificateValidDays = 365;

    public int getDraftAutoSaveMinutes() {
        return draftAutoSaveMinutes;
    }

    public void setDraftAutoSaveMinutes(int draftAutoSaveMinutes) {
        this.draftAutoSaveMinutes = draftAutoSaveMinutes;
    }

    public int getWithdrawAllowedMinutes() {
        return withdrawAllowedMinutes;
    }

    public void setWithdrawAllowedMinutes(int withdrawAllowedMinutes) {
        this.withdrawAllowedMinutes = withdrawAllowedMinutes;
    }

    public int getReviewTimeoutDays() {
        return reviewTimeoutDays;
    }

    public void setReviewTimeoutDays(int reviewTimeoutDays) {
        this.reviewTimeoutDays = reviewTimeoutDays;
    }

    public int getCertificateValidDays() {
        return certificateValidDays;
    }

    public void setCertificateValidDays(int certificateValidDays) {
        this.certificateValidDays = certificateValidDays;
    }
}