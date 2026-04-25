package com.wx.fbsir.business.fbs.domain.enums;

/**
 * 场景包状态枚举
 * 对应 fbs_scene_pack.status
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
public enum PackStatus {

    /** 草稿（未发布，仅创建者可见） */
    DRAFT(0, "草稿"),
    /** 已发布（用户可使用） */
    PUBLISHED(1, "已发布"),
    /** 已下架（不可新增权益，现有用户不受影响） */
    OFFLINE(2, "已下架");

    private final int code;
    private final String desc;

    PackStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static PackStatus ofCode(int code) {
        for (PackStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
