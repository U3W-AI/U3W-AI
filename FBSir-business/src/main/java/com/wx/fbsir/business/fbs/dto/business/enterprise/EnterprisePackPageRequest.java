package com.wx.fbsir.business.fbs.dto.business.enterprise;

/**
 * 企业场景包分页查询请求DTO
 *
 * @author FBSir
 * @date 2026-04-10
 */
public class EnterprisePackPageRequest {

    /** 企业ID（必需） */
    private Long enterpriseId;

    /** 状态：1=已授权, 2=已用尽, 3=已撤销 */
    private Integer status;

    // ===== getter / setter =====

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
