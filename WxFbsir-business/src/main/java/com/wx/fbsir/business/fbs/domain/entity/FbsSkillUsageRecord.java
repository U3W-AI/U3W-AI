package com.wx.fbsir.business.fbs.domain.entity;

import java.util.Date;

/**
 * Skill 使用记录表实体 fbs_skill_usage_record
 *
 * usage_record_id：调用方生成的幂等键（如 WorkBuddy taskId 或 UUID），
 *                 系统通过此键保证消费请求幂等，重复提交直接返回已有结果。
 *
 * 状态流转：0（进行中）→ 1（成功）或 2（失败）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class FbsSkillUsageRecord {

    /** 主键（自增Long） */
    private Long id;

    /**
     * 使用记录幂等键（String，对应 WorkBuddy taskId 或 UUID）
     * 全局唯一，调用方负责生成并保证唯一性。
     */
    private String usageRecordId;

    /** 用户ID */
    private Long userId;

    /** 宿主类型：WORKBUDDY/STANDALONE/API */
    private String hostType;

    /** 宿主会话ID（可空） */
    private String hostSessionId;

    /** 技能编码 */
    private String skillCode;

    /** 使用的场景包ID（关联 fbs_scene_pack.id，可空） */
    private Long packId;

    /** 使用的场景包版本（快照） */
    private String packVersion;

    /**
     * 扣减积分数量（0=免费，points_rule_code=NULL 时为0）
     */
    private Integer pointsAmount;

    /**
     * 状态：0=进行中, 1=成功, 2=失败
     * @see com.wx.fbsir.business.fbs.domain.enums.UsageStatus
     */
    private Integer status;

    /** 使用开始时间 */
    private Date startTime;

    /** 使用结束时间（成功或失败后更新） */
    private Date endTime;

    /** 使用时长（秒） */
    private Integer durationSeconds;

    /** 失败原因（status=2时有值） */
    private String errorMessage;

    /** 创建时间 */
    private Date createdAt;

    // ========== getter / setter ==========

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsageRecordId() { return usageRecordId; }
    public void setUsageRecordId(String usageRecordId) { this.usageRecordId = usageRecordId; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getHostType() { return hostType; }
    public void setHostType(String hostType) { this.hostType = hostType; }

    public String getHostSessionId() { return hostSessionId; }
    public void setHostSessionId(String hostSessionId) { this.hostSessionId = hostSessionId; }

    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getPackVersion() { return packVersion; }
    public void setPackVersion(String packVersion) { this.packVersion = packVersion; }

    public Integer getPointsAmount() { return pointsAmount; }
    public void setPointsAmount(Integer pointsAmount) { this.pointsAmount = pointsAmount; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Date getStartTime() { return startTime; }
    public void setStartTime(Date startTime) { this.startTime = startTime; }

    public Date getEndTime() { return endTime; }
    public void setEndTime(Date endTime) { this.endTime = endTime; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}
