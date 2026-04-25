package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 企业组织主表实体 fbs_enterprise
 *
 * 状态机：1=正常, 2=已禁用（终态）
 * 不含 points_balance（企业积分池延期至后续阶段）
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class FbsEnterprise extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 企业编码（ENT_xxxxxx） */
    private String enterpriseCode;

    /** 企业名称（唯一） */
    private String enterpriseName;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 联系人邮箱 */
    private String contactEmail;

    /** 备注 */
    private String remark;

    /** 状态：1=正常, 2=已禁用 */
    private Integer status;

    /** 创建者 */
    private String createdBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新者 */
    private String updatedBy;

    /** 更新时间 */
    private Date updateTime;

    /** 删除标志（0=存在, 2=删除） */
    private String delFlag;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEnterpriseCode() { return enterpriseCode; }
    public void setEnterpriseCode(String enterpriseCode) { this.enterpriseCode = enterpriseCode; }

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }

    public String getDelFlag() { return delFlag; }
    public void setDelFlag(String delFlag) { this.delFlag = delFlag; }
}
