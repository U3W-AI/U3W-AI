package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 企业创建/编辑请求DTO
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class EnterpriseCreateRequest {

    /** 企业名称（必填，唯一） */
    private String enterpriseName;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 联系人邮箱 */
    private String contactEmail;

    /** 备注 */
    private String remark;

    // ===== getter / setter =====

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
}
