package com.wx.fbsir.business.fbs.domain.enums;

/**
 * 积分冻结状态枚举
 *
 * @see #OPENING   冻结中（预占积分）
 * @see #CONFIRMED 已确认（正式扣减）
 * @see #ROLLED_BACK 已回滚（释放预占）
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 冻结/确认/回滚两阶段延期至后续 OpenSpec
 *      此枚举仅作占位，fbs_points_freeze 表暂不创建。
 *
 * @author FBSir
 * @date 2026-04-08
 */
public enum FreezeStatus {
    OPENING(0,    "冻结中（预占）"),
    CONFIRMED(1,  "已确认（正式扣减）"),
    ROLLED_BACK(2, "已回滚（释放预占）");

    private final int code;
    private final String desc;

    FreezeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
