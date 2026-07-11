package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 成员场景包授权表实体 fbs_member_pack
 *
 * 纯授权凭证（成员是否有资格使用某企业包），不存独立配额。
 * 配额扣减统一在 fbs_enterprise_pack 层面，不在此表操作。
 * 状态机：1=正常, 2=已用尽, 3=已过期, 4=已撤销
 * 禁用企业/移除成员不批量修改此表状态，消费时通过企业/成员状态 fail-closed
 *
 * @author FBSir
 * @date 2026-04-09
 */
public class FbsMemberPack extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 企业成员ID（fbs_enterprise_member.id） */
    private Long memberId;

    /** 企业包ID（fbs_enterprise_pack.id） */
    private Long enterprisePackId;

    /** 场景包ID（fbs_scene_pack.id，冗余便于查询） */
    private Long packId;

    /** 授权时间 */
    private Date grantTime;

    /** 过期时间，NULL=永不过期 */
    private Date expiryTime;

    /** 状态：1=正常, 2=已用尽, 3=已过期, 4=已撤销 */
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

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }

    public Long getEnterprisePackId() { return enterprisePackId; }
    public void setEnterprisePackId(Long enterprisePackId) { this.enterprisePackId = enterprisePackId; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

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
}
