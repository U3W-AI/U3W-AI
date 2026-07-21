package com.wx.fbsir.business.board.domain;

import java.util.Date;

/** Server-side plan policy loaded from fbs_product_plan. */
public class BoardProductPlan {
    private String productCode;
    private String planCode;
    private String planName;
    private Boolean vip;
    private Boolean connectorRequired;
    private Integer dailyMeetingLimit;
    private Integer agendaLimit;
    private Integer seatLimit;
    private Boolean secretaryEnabled;
    private String status;
    private Long version;
    private Date updatedAt;

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Boolean getVip() { return vip; }
    public void setVip(Boolean vip) { this.vip = vip; }
    public Boolean getConnectorRequired() { return connectorRequired; }
    public void setConnectorRequired(Boolean connectorRequired) { this.connectorRequired = connectorRequired; }
    public Integer getDailyMeetingLimit() { return dailyMeetingLimit; }
    public void setDailyMeetingLimit(Integer dailyMeetingLimit) { this.dailyMeetingLimit = dailyMeetingLimit; }
    public Integer getAgendaLimit() { return agendaLimit; }
    public void setAgendaLimit(Integer agendaLimit) { this.agendaLimit = agendaLimit; }
    public Integer getSeatLimit() { return seatLimit; }
    public void setSeatLimit(Integer seatLimit) { this.seatLimit = seatLimit; }
    public Boolean getSecretaryEnabled() { return secretaryEnabled; }
    public void setSecretaryEnabled(Boolean secretaryEnabled) { this.secretaryEnabled = secretaryEnabled; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Date getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
