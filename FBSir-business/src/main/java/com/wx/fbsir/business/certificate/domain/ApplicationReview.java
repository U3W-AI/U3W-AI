package com.wx.fbsir.business.certificate.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.persistence.*;

import java.util.Date;

/**
 * 申请审核记录对象 application_review
 * 
 * @author FBSir
 * @date 2026-01-08
 */
@Table(name = "application_review")
public class ApplicationReview extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 审核ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long reviewId;

    /** 申请ID */
    @Column(name = "application_id")
    private Long applicationId;

    /** 审核人ID */
    @Column(name = "reviewer_id")
    private Long reviewerId;

    /** 审核意见 */
    @Column(name = "review_opinion")
    private String reviewOpinion;

    /** 审核结果（APPROVED-通过、REJECTED-驳回、REQUIRE_SUPPLEMENT-要求补充） */
    @Column(name = "review_result")
    private String reviewResult;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "review_time")
    private Date reviewTime;

    /** 审核节点ID */
    @Column(name = "node_id")
    private String nodeId;

    /** 审核节点名称 */
    @Column(name = "node_name")
    private String nodeName;

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public Long getApplicationId()
    {
        return applicationId;
    }

    public void setApplicationId(Long applicationId)
    {
        this.applicationId = applicationId;
    }

    public Long getReviewerId()
    {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId)
    {
        this.reviewerId = reviewerId;
    }

    public String getReviewOpinion()
    {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion)
    {
        this.reviewOpinion = reviewOpinion;
    }

    public String getReviewResult()
    {
        return reviewResult;
    }

    public void setReviewResult(String reviewResult)
    {
        this.reviewResult = reviewResult;
    }

    public Date getReviewTime()
    {
        return reviewTime;
    }

    public void setReviewTime(Date reviewTime)
    {
        this.reviewTime = reviewTime;
    }

    public String getNodeId()
    {
        return nodeId;
    }

    public void setNodeId(String nodeId)
    {
        this.nodeId = nodeId;
    }

    public String getNodeName()
    {
        return nodeName;
    }

    public void setNodeName(String nodeName)
    {
        this.nodeName = nodeName;
    }
    
    // 用于查询条件的属性
    private String applicationStatus;
    private String templateName;
    private String applicantName;
    
    public String getApplicationStatus()
    {
        return applicationStatus;
    }
    
    public void setApplicationStatus(String applicationStatus)
    {
        this.applicationStatus = applicationStatus;
    }
    
    public String getTemplateName()
    {
        return templateName;
    }
    
    public void setTemplateName(String templateName)
    {
        this.templateName = templateName;
    }
    
    public String getApplicantName()
    {
        return applicantName;
    }
    
    public void setApplicantName(String applicantName)
    {
        this.applicantName = applicantName;
    }
}