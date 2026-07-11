package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 企业已获场景包表实体 fbs_enterprise_pack
 *
 * 平台向企业分发（gift）记录，含企业级配额。
 * 配额扣减统一在此层（packQuota / usedQuota），remainQuota 实时计算。
 * 状态机：1=已授权, 2=已用尽（配额用完）, 3=已撤销（终态）
 *
 * @author FBSir
 * @date 2026-04-09
 */
public class FbsEnterprisePack extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 企业ID（fbs_enterprise.id） */
    private Long enterpriseId;

    /** 场景包ID（fbs_scene_pack.id） */
    private Long packId;

    /** 企业级总配额（packQuota） */
    private Integer packQuota;

    /** 已使用配额（usedQuota） */
    private Integer usedQuota;

    /** 分发时间 */
    private Date grantTime;

    /** 过期时间，NULL=永不过期 */
    private Date expiryTime;

    /** 状态：1=已授权, 2=已用尽, 3=已撤销 */
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

    // ========== 非持久化字段（用于 JOIN 查询回显）==========

    /** 场景包编码（JOIN查询，不存储） */
    private String packCode;

    /** 场景包名称（JOIN查询，不存储） */
    private String packName;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public Integer getPackQuota() { return packQuota; }
    public void setPackQuota(Integer packQuota) { this.packQuota = packQuota; }

    public Integer getUsedQuota() { return usedQuota; }
    public void setUsedQuota(Integer usedQuota) { this.usedQuota = usedQuota; }

    public Date getGrantTime() { return grantTime; }
    public void setGrantTime(Date grantTime) { this.grantTime = grantTime; }

    public Date getExpiryTime() { return expiryTime; }
    public void setExpiryTime(Date expiryTime) { this.expiryTime = expiryTime; }

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

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public String getPackName() { return packName; }
    public void setPackName(String packName) { this.packName = packName; }
}
