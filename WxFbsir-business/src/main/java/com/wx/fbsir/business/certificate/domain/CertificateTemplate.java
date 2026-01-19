package com.wx.fbsir.business.certificate.domain;

import com.wx.fbsir.common.core.domain.BaseEntity;
import jakarta.persistence.*;

/**
 * 证书模板对象 certificate_template
 * 
 * @author wxfbsir
 * @date 2026-01-08
 */
@Table(name = "certificate_template")
public class CertificateTemplate extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 模板ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    /** 模板名称 */
    @Column(name = "template_name")
    private String templateName;

    /** 证书类型 */
    @Column(name = "certificate_type")
    private String certificateType;

    /** 模板内容（HTML格式） */
    @Column(name = "template_content")
    private String templateContent;

    /** 申请必填字段（JSON格式） */
    @Column(name = "apply_required_fields")
    private String applyRequiredFields;

    /** 模板字段配置（JSON格式） */
    @Column(name = "template_fields")
    private String templateFields;

    /** 证书底版图片路径 */
    @Column(name = "certificate_bg_image")
    private String certificateBgImage;

    /** 字段位置配置（JSON格式） */
    @Column(name = "field_positions")
    private String fieldPositions;

    /** 审核流程配置ID */
    @Column(name = "review_process_config_id")
    private Long reviewProcessConfigId;

    /** 状态（0正常 1停用） */
    @Column(name = "status")
    private String status;

    /** 备注 */
    @Column(name = "remark")
    private String remark;

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
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

    public String getTemplateContent()
    {
        return templateContent;
    }

    public void setTemplateContent(String templateContent)
    {
        this.templateContent = templateContent;
    }

    public String getApplyRequiredFields()
    {
        return applyRequiredFields;
    }

    public void setApplyRequiredFields(String applyRequiredFields)
    {
        this.applyRequiredFields = applyRequiredFields;
    }

    public String getTemplateFields()
    {
        return templateFields;
    }

    public void setTemplateFields(String templateFields)
    {
        this.templateFields = templateFields;
    }

    public Long getReviewProcessConfigId()
    {
        return reviewProcessConfigId;
    }

    public void setReviewProcessConfigId(Long reviewProcessConfigId)
    {
        this.reviewProcessConfigId = reviewProcessConfigId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
    }

    public String getCertificateBgImage()
    {
        return certificateBgImage;
    }

    public void setCertificateBgImage(String certificateBgImage)
    {
        this.certificateBgImage = certificateBgImage;
    }

    public String getFieldPositions()
    {
        return fieldPositions;
    }

    public void setFieldPositions(String fieldPositions)
    {
        this.fieldPositions = fieldPositions;
    }
}