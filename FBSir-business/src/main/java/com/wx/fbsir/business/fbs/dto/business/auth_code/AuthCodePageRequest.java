package com.wx.fbsir.business.fbs.dto.business.auth_code;

/**
 * 授权码分页请求
 *
 * @author FBSir
 * @date 2026-04-08
 */
public class AuthCodePageRequest {

    /** 授权码（模糊查询） */
    private String authCode;

    /** 关联目标ID（场景包ID） */
    private Long targetId;

    /** 启用状态：0=禁用, 1=启用 */
    private Integer available;

    /** 使用状态：0=未激活, 1=已激活, 2=已用尽, 3=已过期, 4=已撤销 */
    private Integer status;

    /** 发放者类型：1=平台, 2=企业, 3=用户 */
    private Integer issuerType;

    /** 页码（由BaseController startPage自动注入） */
    private Integer pageNum;

    /** 每页大小（由BaseController startPage自动注入） */
    private Integer pageSize;

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }

    public Integer getAvailable() { return available; }
    public void setAvailable(Integer available) { this.available = available; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getIssuerType() { return issuerType; }
    public void setIssuerType(Integer issuerType) { this.issuerType = issuerType; }

    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }

    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
