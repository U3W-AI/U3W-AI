package com.wx.fbsir.business.certificate.domain;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 证书申请管理视图对象
 * 基于CertificateApplication表数据构建，用于管理证书申请记录
 * 
 * @author wxfbsir
 */
public class CertificateApplicationManagementVO
{

    private Long applicationId;

    private Long templateId;

    private Long userId;

    private String applicationStatus;

    private String applicationData;

    private String applicationContent;

    private String applicationNumber;

    private Integer pointsDeducted;

    private String approveTime;

    private String receiveStatus;

    private String reviewRemark;

    private String createBy;

    private String createTime;

    private String updateBy;

    private String updateTime;

    private String remark;

    // 以下是额外的视图相关字段
    private Long issuanceId;

    private String certificateId;

    private String certificateContent;

    private String certificatePdf;

    private String issuanceTime;

    private String expiryDate;

    private String templateName;

    private String certificateType;

    private String applicantName;

    private String company;

    private String validFrom;

    private String validTo;

    // 构造函数
    public CertificateApplicationManagementVO() {}

    // Getter 和 Setter 方法
    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getApplicationStatus() {
        return applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus) {
        this.applicationStatus = applicationStatus;
    }

    public String getApplicationData() {
        return applicationData;
    }

    public void setApplicationData(String applicationData) {
        this.applicationData = applicationData;
    }

    public String getApplicationContent() {
        return applicationContent;
    }

    public void setApplicationContent(String applicationContent) {
        this.applicationContent = applicationContent;
    }

    public String getApplicationNumber() {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber) {
        this.applicationNumber = applicationNumber;
    }

    public Integer getPointsDeducted() {
        return pointsDeducted;
    }

    public void setPointsDeducted(Integer pointsDeducted) {
        this.pointsDeducted = pointsDeducted;
    }

    public String getApproveTime() {
        return approveTime;
    }

    public void setApproveTime(String approveTime) {
        this.approveTime = approveTime;
    }

    public String getReceiveStatus() {
        return receiveStatus;
    }

    public void setReceiveStatus(String receiveStatus) {
        this.receiveStatus = receiveStatus;
    }

    public String getReviewRemark() {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark) {
        this.reviewRemark = reviewRemark;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getIssuanceId() {
        return issuanceId;
    }

    public void setIssuanceId(Long issuanceId) {
        this.issuanceId = issuanceId;
    }

    public String getCertificateId() {
        return certificateId;
    }

    public void setCertificateId(String certificateId) {
        this.certificateId = certificateId;
    }

    public String getCertificateContent() {
        return certificateContent;
    }

    public void setCertificateContent(String certificateContent) {
        this.certificateContent = certificateContent;
    }

    public String getCertificatePdf() {
        return certificatePdf;
    }

    public void setCertificatePdf(String certificatePdf) {
        this.certificatePdf = certificatePdf;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public String getIssuanceTime() {
        return issuanceTime;
    }

    public void setIssuanceTime(String issuanceTime) {
        this.issuanceTime = issuanceTime;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    public String getCertificateType() {
        return certificateType;
    }

    public void setCertificateType(String certificateType) {
        this.certificateType = certificateType;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public void setApplicantName(String applicantName) {
        this.applicantName = applicantName;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public String getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(String validFrom) {
        this.validFrom = validFrom;
    }

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public String getValidTo() {
        return validTo;
    }

    public void setValidTo(String validTo) {
        this.validTo = validTo;
    }
}