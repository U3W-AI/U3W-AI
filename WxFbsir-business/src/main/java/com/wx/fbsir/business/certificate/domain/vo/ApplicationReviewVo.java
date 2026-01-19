package com.wx.fbsir.business.certificate.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.wx.fbsir.business.certificate.domain.CertificateApplication;

import java.util.Date;

/**
 * 申请审核列表视图对象
 * 
 * @author wxfbsir
 */
public class ApplicationReviewVo extends CertificateApplication
{
    private static final long serialVersionUID = 1L;

    /** 审核ID */
    private Long reviewId;

    /** 审核人ID */
    private Long reviewerId;

    /** 审核意见 */
    private String reviewRemark;

    /** 审核结果（APPROVED-通过、REJECTED-驳回、REQUIRE_SUPPLEMENT-要求补充） */
    private String reviewResult;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date reviewTime;

    /** 审核节点ID */
    private String nodeId;

    /** 审核节点名称 */
    private String nodeName;

    /** 申请人姓名 */
    private String applicantName;

    /** 申请日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 申请积分 */
    private Integer applicationPoints;

    /** 联系电话 */
    private String phone;

    /** 所属单位 */
    private String company;

    /** 上传材料 */
    private String applicationMaterials;

    /** 补充说明 */
    private String remark;

    /** 审核意见 */
    private String reviewOpinion;

    @Override
    public String getApplicationCode()
    {
        return this.getApplicationNumber(); // 使用applicationNumber作为applicationCode
    }

    @Override
    public void setApplicationCode(String applicationCode)
    {
        this.setApplicationNumber(applicationCode); // 设置applicationNumber
    }

    public Long getReviewId()
    {
        return reviewId;
    }

    public void setReviewId(Long reviewId)
    {
        this.reviewId = reviewId;
    }

    public Long getReviewerId()
    {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId)
    {
        this.reviewerId = reviewerId;
    }

    public String getReviewRemark()
    {
        return reviewRemark;
    }

    public void setReviewRemark(String reviewRemark)
    {
        this.reviewRemark = reviewRemark;
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

    public String getApplicantName()
    {
        return applicantName;
    }

    public void setApplicantName(String applicantName)
    {
        this.applicantName = applicantName;
    }

    public Date getCreateTime()
    {
        return createTime;
    }

    public void setCreateTime(Date createTime)
    {
        this.createTime = createTime;
    }

    public Integer getApplicationPoints()
    {
        return applicationPoints;
    }

    public void setApplicationPoints(Integer applicationPoints)
    {
        this.applicationPoints = applicationPoints;
    }

    public String getPhone()
    {
        return phone;
    }

    public void setPhone(String phone)
    {
        this.phone = phone;
    }

    public String getCompany()
    {
        return company;
    }

    public void setCompany(String company)
    {
        this.company = company;
    }

    public String getApplicationMaterials()
    {
        return applicationMaterials;
    }

    public void setApplicationMaterials(String applicationMaterials)
    {
        this.applicationMaterials = applicationMaterials;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getReviewOpinion()
    {
        return reviewOpinion;
    }

    public void setReviewOpinion(String reviewOpinion)
    {
        this.reviewOpinion = reviewOpinion;
    }
}