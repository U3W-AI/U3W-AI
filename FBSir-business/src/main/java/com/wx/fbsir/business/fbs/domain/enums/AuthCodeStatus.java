package com.wx.fbsir.business.fbs.domain.enums;

/**
 * 授权码使用状态枚举
 * 对应 fbs_auth_code.status
 *
 * 注意：status 与 available 是两个独立维度：
 *  - available：管理层面开关（0=禁用, 1=启用），不在本枚举中
 *  - status：使用状态（见下）
 *
 * 激活状态机：
 *  - 可激活：status IN(0,1) AND available=1 AND activated_count < max_activations AND 未过期
 *  - 首次激活后：status → 1，activated_count + 1
 *  - activated_count >= max_activations：status → 2
 *  - status 为 2/3/4 时均不可激活（Fail-Closed）
 *
 * @author FBSir
 * @date 2026-04-08
 */
public enum AuthCodeStatus {

    /** 未激活（从未有人使用过） */
    NOT_ACTIVATED(0, "未激活"),
    /** 已激活（至少有一人使用过，仍可继续，适用于 max_activations > 1 场景） */
    ACTIVATED(1, "已激活"),
    /** 已用尽（activated_count >= max_activations） */
    EXHAUSTED(2, "已用尽"),
    /** 已过期（超过 deadline） */
    EXPIRED(3, "已过期"),
    /** 已撤销（管理员主动撤销） */
    REVOKED(4, "已撤销");

    private final int code;
    private final String desc;

    AuthCodeStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 判断当前状态是否允许激活（status IN(0,1)）
     */
    public boolean isActivatable() {
        return this == NOT_ACTIVATED || this == ACTIVATED;
    }

    public static AuthCodeStatus ofCode(int code) {
        for (AuthCodeStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        return null;
    }
}
