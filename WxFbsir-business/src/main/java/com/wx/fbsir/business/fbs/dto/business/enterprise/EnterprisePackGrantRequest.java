package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 平台向企业分发场景包请求DTO
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class EnterprisePackGrantRequest {

    /** 企业ID（必填） */
    private Long enterpriseId;

    /** 场景包编码（必填） */
    private String packCode;

    /** 配额数量（必填） */
    private Integer packQuota;

    /** 过期时间（可选，NULL=永不过期） */
    private String expiryTime;

    // ===== getter / setter =====

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public Integer getPackQuota() { return packQuota; }
    public void setPackQuota(Integer packQuota) { this.packQuota = packQuota; }

    public String getExpiryTime() { return expiryTime; }
    public void setExpiryTime(String expiryTime) { this.expiryTime = expiryTime; }
}
