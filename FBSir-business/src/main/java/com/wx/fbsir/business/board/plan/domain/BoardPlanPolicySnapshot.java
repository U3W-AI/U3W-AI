package com.wx.fbsir.business.board.plan.domain;

/** Current committed policy plus independently loaded immutable plan identity. */
public class BoardPlanPolicySnapshot extends BoardPlanPolicyReceipt {
    private String identityProductCode;
    private String identityPlanCode;
    private Boolean identityVip;
    private Boolean identityConnectorRequired;
    private String identityStatus;

    public String getIdentityProductCode() { return identityProductCode; }
    public void setIdentityProductCode(String identityProductCode) { this.identityProductCode = identityProductCode; }
    public String getIdentityPlanCode() { return identityPlanCode; }
    public void setIdentityPlanCode(String identityPlanCode) { this.identityPlanCode = identityPlanCode; }
    public Boolean getIdentityVip() { return identityVip; }
    public void setIdentityVip(Boolean identityVip) { this.identityVip = identityVip; }
    public Boolean getIdentityConnectorRequired() { return identityConnectorRequired; }
    public void setIdentityConnectorRequired(Boolean identityConnectorRequired) { this.identityConnectorRequired = identityConnectorRequired; }
    public String getIdentityStatus() { return identityStatus; }
    public void setIdentityStatus(String identityStatus) { this.identityStatus = identityStatus; }
}
