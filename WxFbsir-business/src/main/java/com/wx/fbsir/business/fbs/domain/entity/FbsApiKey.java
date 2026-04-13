package com.wx.fbsir.business.fbs.domain.entity;

import java.util.Date;

/**
 * FBS API Key 管理表实体 fbs_api_key
 *
 * MVP 阶段 api_key 明文存储，后续迭代改为 SHA-256 hash。
 * 创建时返回完整 Key（仅此一次），列表查询脱敏（前8位+****）。
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
public class FbsApiKey {

    /** 主键 */
    private Long id;

    /** API Key（MVP明文存储） */
    private String apiKey;

    /** API Key 名称 */
    private String name;

    /** 关联场景包编码（NULL=全局Key） */
    private String packCode;

    /** 每分钟速率限制 */
    private Integer rateLimitPerMin;

    /** 状态：1=启用, 0=禁用 */
    private Integer status;

    /** 创建者 */
    private String createdBy;

    /** 创建时间 */
    private Date createTime;

    /** 更新者 */
    private String updatedBy;

    /** 更新时间 */
    private Date updateTime;

    /** 备注 */
    private String remark;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public Integer getRateLimitPerMin() { return rateLimitPerMin; }
    public void setRateLimitPerMin(Integer rateLimitPerMin) { this.rateLimitPerMin = rateLimitPerMin; }

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

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    /**
     * 脱敏 API Key（前8位+****）
     * 列表查询时使用，不暴露完整 Key
     */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.length() <= 8) {
            return "****";
        }
        return apiKey.substring(0, 8) + "****";
    }
}
