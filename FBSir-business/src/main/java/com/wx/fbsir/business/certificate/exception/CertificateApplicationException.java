package com.wx.fbsir.business.certificate.exception;

/**
 * 认证申请业务异常类
 *
 * @author fbsir
 * @date 2026-01-08
 */
public class CertificateApplicationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CertificateApplicationException(String message) {
        super(message);
    }

    public CertificateApplicationException(String message, Object... args) {
        super(String.format(message, args));
    }

    public CertificateApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}