package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 企业分页查询请求DTO
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class EnterprisePageRequest {

    /** 企业名称（模糊匹配） */
    private String enterpriseName;

    /** 状态：1=正常, 2=已禁用 */
    private Integer status;

    // ===== getter / setter =====

    public String getEnterpriseName() { return enterpriseName; }
    public void setEnterpriseName(String enterpriseName) { this.enterpriseName = enterpriseName; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
