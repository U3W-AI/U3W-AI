package com.wx.fbsir.business.fbs.domain.enums;

/**
 * Skill 使用记录状态枚举
 * 对应 fbs_skill_usage_record.status
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public enum UsageStatus {

    /** 进行中（已写入记录，积分尚未扣减完成） */
    IN_PROGRESS(0, "进行中"),
    /** 成功（积分已扣减，使用完成） */
    SUCCESS(1, "成功"),
    /** 失败（积分不足、校验失败等） */
    FAILED(2, "失败");

    private final int code;
    private final String desc;

    UsageStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static UsageStatus ofCode(int code) {
        for (UsageStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
