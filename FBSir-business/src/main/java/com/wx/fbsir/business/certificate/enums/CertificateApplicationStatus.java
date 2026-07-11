package com.wx.fbsir.business.certificate.enums;

/**
 * 认证申请状态枚举
 *
 * @author fbsir
 * @date 2026-01-08
 */
public enum CertificateApplicationStatus {
    DRAFT("0", "草稿"),
    PENDING_REVIEW("1", "待审核"),  // Pending Review
    APPROVED("2", "已批准"),       // Approved
    REJECTED("3", "已驳回"),      // Rejected
    ISSUED("4", "已发放");

    private final String code;
    private final String desc;

    CertificateApplicationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据代码查找状态
     */
    public static CertificateApplicationStatus getByCode(String code) {
        for (CertificateApplicationStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}