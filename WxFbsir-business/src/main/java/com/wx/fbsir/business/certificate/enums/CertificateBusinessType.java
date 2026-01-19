package com.wx.fbsir.business.certificate.enums;

/**
 * 认证申请业务类型枚举
 *
 * @author fbsir
 * @date 2026-01-08
 */
public enum CertificateBusinessType {
    CERTIFICATE_APPLICATION("CERTIFICATE_APPLICATION", "证书申请"),
    CERTIFICATE_RENEWAL("CERTIFICATE_RENEWAL", "证书续期"),
    CERTIFICATE_MODIFICATION("CERTIFICATE_MODIFICATION", "证书变更");

    private final String code;
    private final String desc;

    CertificateBusinessType(String code, String desc) {
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
     * 根据代码查找业务类型
     */
    public static CertificateBusinessType getByCode(String code) {
        for (CertificateBusinessType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}