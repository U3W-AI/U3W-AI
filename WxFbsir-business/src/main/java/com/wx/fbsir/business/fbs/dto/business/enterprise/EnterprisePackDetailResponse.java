package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 企业包详情响应DTO
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class EnterprisePackDetailResponse {

    private Long id;
    private Long enterpriseId;
    private String enterpriseName;
    private Long packId;
    private String packCode;
    private String packName;
    private Integer packQuota;
    private Integer usedQuota;
    private Integer remainQuota;  // 实时 = packQuota - usedQuota
    private String grantTime;
    private String expiryTime;
    private Integer status;
    private String statusDesc;

    // ===== getter / setter =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }

    public Integer getPackQuota() { return packQuota; }
    public void setPackQuota(Integer packQuota) { this.packQuota = packQuota; }

    public Integer getUsedQuota() { return usedQuota; }
    public void setUsedQuota(Integer usedQuota) { this.usedQuota = usedQuota; }

    public Integer getRemainQuota() { return remainQuota; }
    public void setRemainQuota(Integer remainQuota) { this.remainQuota = remainQuota; }

    public String getGrantTime() { return grantTime; }
    public void setGrantTime(String grantTime) { this.grantTime = grantTime; }

    public String getExpiryTime() { return expiryTime; }
    public void setExpiryTime(String expiryTime) { this.expiryTime = expiryTime; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getStatusDesc() { return statusDesc; }
    public void setStatusDesc(String statusDesc) { this.statusDesc = statusDesc; }
}
