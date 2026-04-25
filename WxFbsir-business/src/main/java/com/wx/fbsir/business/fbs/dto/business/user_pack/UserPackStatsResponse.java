package com.wx.fbsir.business.fbs.dto.business.user_pack;

/**
 * 用户权益统计响应
 *
 * 注意：fbs_user_pack.status 与 fbs_auth_code.status 是两个不同维度。
 * fbs_user_pack.status: 1=有效, 2=已过期, 3=已撤销（无 exhaustedCount）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public class UserPackStatsResponse {

    /** 用户ID */
    private Long userId;

    /** 总数 */
    private Long totalCount;

    /** 有效数（status=1） */
    private Long activeCount;

    /** 已过期数（status=2） */
    private Long expiredCount;

    /** 已撤销数（status=3） */
    private Long revokedCount;

    // ===== getter / setter =====

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getTotalCount() { return totalCount; }
    public void setTotalCount(Long totalCount) { this.totalCount = totalCount; }

    public Long getActiveCount() { return activeCount; }
    public void setActiveCount(Long activeCount) { this.activeCount = activeCount; }

    public Long getExpiredCount() { return expiredCount; }
    public void setExpiredCount(Long expiredCount) { this.expiredCount = expiredCount; }

    public Long getRevokedCount() { return revokedCount; }
    public void setRevokedCount(Long revokedCount) { this.revokedCount = revokedCount; }
}
