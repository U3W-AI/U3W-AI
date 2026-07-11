package com.wx.fbsir.business.certificate.util;

import com.wx.fbsir.business.certificate.domain.CertificateApplication;

/**
 * 认证申请工具类
 *
 * @author fbsir
 * @date 2026-01-08
 */
public class CertificateApplicationUtils {

    /**
     * 生成申请编号
     * 格式：CA + 年月日 + 6位序号
     */
    public static String generateApplicationNumber() {
        // 使用当前日期时间生成唯一编号
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String dateStr = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        // 生成随机数或递增序号作为后缀
        String seq = String.format("%06d", (int)(Math.random() * 1000000)); // 实际生产环境中应使用更可靠的序号生成方式
        return "CA" + dateStr + seq;
    }

    /**
     * 检查申请是否可以被编辑
     */
    public static boolean canEdit(CertificateApplication application) {
        // 只有草稿状态和被驳回的申请可以编辑
        return "DRAFT".equals(application.getApplicationStatus()) || 
               "REJECTED".equals(application.getApplicationStatus());
    }

    /**
     * 检查申请是否可以提交审核
     */
    public static boolean canSubmit(CertificateApplication application) {
        // 只有草稿状态的申请可以提交审核
        return "DRAFT".equals(application.getApplicationStatus());
    }

    /**
     * 检查申请是否可以撤销
     */
    public static boolean canWithdraw(CertificateApplication application) {
        // 只有已提交但还未进入审核阶段的申请可以撤销
        return "SUBMITTED".equals(application.getApplicationStatus());
    }
}