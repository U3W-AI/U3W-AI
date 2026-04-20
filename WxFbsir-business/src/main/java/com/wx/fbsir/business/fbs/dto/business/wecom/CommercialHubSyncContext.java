package com.wx.fbsir.business.fbs.dto.business.wecom;

/**
 * commercial_hub 同步上下文
 *
 * <p>OpenSpec #13: 扩展 syncCommercialHub 签名，传入完整消费上下文，
 * 使 buildCommercialHubRecord() 能输出全部 24 个字段。</p>
 *
 * @author wxfbsir
 * @date 2026-04-18
 */
public class CommercialHubSyncContext {

    /** 用户ID */
    private Long userId;

    /** 场景包编码 */
    private String packCode;

    /** 消费积分数量（正数） */
    private int pointsAmount;

    /** 剩余积分（个人路径）/ 剩余配额（企业路径） */
    private Integer remainPoints;

    /** 宿主类型：WORKBUDDY / ENTERPRISE */
    private String hostType;

    /** 幂等 key（使用记录 ID） */
    private String usageRecordId;

    /** 场景包 ID（用于关联查询） */
    private Long packId;

    /** 授权码（可为空） */
    private String authCode;

    /** 积分规则编码 */
    private String pointsRuleCode;

    /** 场景包类型：1=平台包, 2=企业包 */
    private int packType;

    // ---- 构造器 ----

    public CommercialHubSyncContext() {}

    public CommercialHubSyncContext(Long userId, String packCode, int pointsAmount,
                                    Integer remainPoints, String hostType,
                                    String usageRecordId, Long packId,
                                    String authCode, String pointsRuleCode,
                                    int packType) {
        this.userId = userId;
        this.packCode = packCode;
        this.pointsAmount = pointsAmount;
        this.remainPoints = remainPoints;
        this.hostType = hostType;
        this.usageRecordId = usageRecordId;
        this.packId = packId;
        this.authCode = authCode;
        this.pointsRuleCode = pointsRuleCode;
        this.packType = packType;
    }

    // ---- Getter / Setter ----

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getPackCode() { return packCode; }
    public void setPackCode(String packCode) { this.packCode = packCode; }

    public int getPointsAmount() { return pointsAmount; }
    public void setPointsAmount(int pointsAmount) { this.pointsAmount = pointsAmount; }

    public Integer getRemainPoints() { return remainPoints; }
    public void setRemainPoints(Integer remainPoints) { this.remainPoints = remainPoints; }

    public String getHostType() { return hostType; }
    public void setHostType(String hostType) { this.hostType = hostType; }

    public String getUsageRecordId() { return usageRecordId; }
    public void setUsageRecordId(String usageRecordId) { this.usageRecordId = usageRecordId; }

    public Long getPackId() { return packId; }
    public void setPackId(Long packId) { this.packId = packId; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public String getPointsRuleCode() { return pointsRuleCode; }
    public void setPointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; }

    public int getPackType() { return packType; }
    public void setPackType(int packType) { this.packType = packType; }

    // ---- Builder ----

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long userId;
        private String packCode;
        private int pointsAmount;
        private Integer remainPoints;
        private String hostType;
        private String usageRecordId;
        private Long packId;
        private String authCode;
        private String pointsRuleCode;
        private int packType;

        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder packCode(String packCode) { this.packCode = packCode; return this; }
        public Builder pointsAmount(int pointsAmount) { this.pointsAmount = pointsAmount; return this; }
        public Builder remainPoints(Integer remainPoints) { this.remainPoints = remainPoints; return this; }
        public Builder hostType(String hostType) { this.hostType = hostType; return this; }
        public Builder usageRecordId(String usageRecordId) { this.usageRecordId = usageRecordId; return this; }
        public Builder packId(Long packId) { this.packId = packId; return this; }
        public Builder authCode(String authCode) { this.authCode = authCode; return this; }
        public Builder pointsRuleCode(String pointsRuleCode) { this.pointsRuleCode = pointsRuleCode; return this; }
        public Builder packType(int packType) { this.packType = packType; return this; }

        public CommercialHubSyncContext build() {
            return new CommercialHubSyncContext(userId, packCode, pointsAmount, remainPoints,
                    hostType, usageRecordId, packId, authCode, pointsRuleCode, packType);
        }
    }
}
