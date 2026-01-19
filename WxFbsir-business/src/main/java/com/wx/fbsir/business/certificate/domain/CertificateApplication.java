package com.wx.fbsir.business.certificate.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.Date;

/**
 * 认证申请对象 certificate_application
 * 
 * @author wxfbsir
 * @date 2026-01-08
 */
@Table(name = "certificate_application")
public class CertificateApplication extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 申请ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    /** 用户ID */
    @Column(name = "user_id")
    private Long userId;

    /** 证书模板ID */
    @Column(name = "template_id")
    private Long templateId;

    /** 申请信息（JSON格式） */
    @Column(name = "application_info")
    @Transient  // 数据库中不存在此字段，使用application_data字段
    private String applicationInfo;

    /** 申请数据（JSON格式） */
    @Column(name = "application_data")
    private String applicationData;

    /** 申请内容（JSON格式，包含上传的材料信息） */
    @Column(name = "application_content")
    private String applicationContent;

    /** 申请编号 */
    @Column(name = "application_number")
    private String applicationNumber;

    /** 申请状态（DRAFT-草稿、SUBMITTED-已提交、UNDER_REVIEW-审核中、NODE_APPROVED-节点审核通过、REQUIRE_SUPPLEMENT-要求补充资料、APPROVED-最终审核通过、REJECTED-已驳回、ISSUED-已发放） */
    @Column(name = "application_status")
    private String applicationStatus;



    /** 审核备注 */
    @Column(name = "review_remark")
    private String reviewRemark;

    /** 最终审核人ID */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 最终审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "review_time")
    private Date reviewTime;

    /** 申请提交时扣除的积分 */
    @Column(name = "points_deducted")
    private Integer pointsDeducted;

    /** 审批通过时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "approve_time")
    private Date approveTime;

    /** 领取状态（not_received-未领取，received-已领取） */
    @Column(name = "receive_status")
    private String receiveStatus;

    /** 证书编号 */
    @Column(name = "certificate_id")
    private String certificateId;

    /** 申请编号 */
    @Transient
    private String applicationCode;

    /** 模板名称 */
    @Transient
    private String templateName;

    /** 证书类型 */
    @Transient
    private String certificateType;

    public Long getApplicationId()
    {
        return applicationId;
    }

    public void setApplicationId(Long applicationId)
    {
        this.applicationId = applicationId;
    }

    public Long getUserId()
    {
        return userId;
    }

    public void setUserId(Long userId)
    {
        this.userId = userId;
    }

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
    }

    public String getApplicationInfo()
    {
        return applicationInfo;
    }

    public void setApplicationInfo(String applicationInfo)
    {
        this.applicationInfo = applicationInfo;
    }

    public String getApplicationData()
    {
        return applicationData;
    }

    public void setApplicationData(String applicationData)
    {
        this.applicationData = applicationData;
    }

    public String getApplicationContent()
    {
        return applicationContent;
    }

    public void setApplicationContent(String applicationContent)
    {
        this.applicationContent = applicationContent;
    }

    public String getApplicationNumber()
    {
        return applicationNumber;
    }

    public void setApplicationNumber(String applicationNumber)
    {
        this.applicationNumber = applicationNumber;
    }

    public String getApplicationStatus()
    {
        return applicationStatus;
    }

    public void setApplicationStatus(String applicationStatus)
    {
        this.applicationStatus = applicationStatus;
    }



    public Long getReviewerId()
    {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId)
    {
        this.reviewerId = reviewerId;
    }

    public Date getReviewTime()
    {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime)
    {
        this.reviewTime = reviewTime;
    }

    public Integer getPointsDeducted()
    {
        return pointsDeducted;
    }

    public void setPointsDeducted(Integer pointsDeducted)
    {
        this.pointsDeducted = pointsDeducted;
    }

    public Date getApproveTime()
    {
        return approveTime;
    }

    public void setApproveTime(Date approveTime)
    {
        this.approveTime = approveTime;
    }

    public String getReceiveStatus()
    {
        return receiveStatus;
    }

    public void setReceiveStatus(String receiveStatus)
    {
        this.receiveStatus = receiveStatus;
    }

    public String getReviewRemark()
    {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark)
    {
        this.reviewRemark = reviewRemark;
    }

    public String getApplicationCode()
    {
        return applicationCode;
    }

    public void setApplicationCode(String applicationCode)
    {
        this.applicationCode = applicationCode;
    }

    public String getTemplateName()
    {
        return templateName;
    }

    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
    }

    public String getCertificateType()
    {
        return certificateType;
    }

    public void setCertificateType(String certificateType)
    {
        this.certificateType = certificateType;
    }

    public String getCertificateId()
    {
        return certificateId;
    }

    public void setCertificateId(String certificateId)
    {
        this.certificateId = certificateId;
    }
}