package com.wx.fbsir.business.fbs.domain.enums;

/**
 * 积分操作动作枚举
 *
 * @see #FREEZE    冻结（预占积分）
 * @see #CONFIRM   确认（正式扣减）
 * @see #ROLLBACK  回滚（释放预占）
 *
 * TODO (OpenSpec #add-fbs-rights-foundation): 冻结/确认/回滚两阶段延期至后续 OpenSpec
 *      此枚举仅作占位，fbs_points_freeze 表暂不创建。
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public enum PointsAction {
    FREEZE(0,   "冻结（预占）"),
    CONFIRM(1,  "确认（正式扣减）"),
    ROLLBACK(2, "回滚（释放预占）");

    private final int code;
    private final String desc;

    PointsAction(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }
}
