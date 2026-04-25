package com.wx.fbsir.business.fbs.domain.entity;

import com.wx.fbsir.common.core.domain.BaseEntity;

import java.util.Date;

/**
 * 企业成员关联表实体 fbs_enterprise_member
 *
 * 用户与企业的关系只通过此表维护，不改 sys_user。
 * 状态机：1=正常, 2=已移除（终态）
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
public class FbsEnterpriseMember extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 企业ID（fbs_enterprise.id） */
    private Long enterpriseId;

    /** 用户ID（sys_user.user_id） */
    private Long userId;

    /** 成员角色：ADMIN=管理员, MEMBER=普通成员 */
    private String role;

    /** 加入时间 */
    private Date joinTime;

    /** 状态：1=正常, 2=已移除 */
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

    public Long getEnterpriseId() { return enterpriseId; }
    public void setEnterpriseId(Long enterpriseId) { this.enterpriseId = enterpriseId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public Date getJoinTime() { return joinTime; }
    public void setJoinTime(Date joinTime) { this.joinTime = joinTime; }

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
