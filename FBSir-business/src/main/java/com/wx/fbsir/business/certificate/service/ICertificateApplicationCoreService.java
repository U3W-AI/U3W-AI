package com.wx.fbsir.business.certificate.service;

import com.wx.fbsir.business.certificate.domain.CertificateApplication;

/**
 * 认证申请核心业务服务接口
 *
 * @author fbsir
 * @date 2026-01-08
 */
public interface ICertificateApplicationCoreService {

    /**
     * 提交申请
     */
    boolean submitApplication(Long applicationId, Long userId);

    /**
     * 撤回申请
     */
    boolean withdrawApplication(Long applicationId, Long userId);

    /**
     * 审核申请
     */
    boolean reviewApplication(Long applicationId, String status, String reviewOpinion, Long reviewerId);

    /**
     * 更新申请状态
     */
    boolean updateApplicationStatus(CertificateApplication application, String newStatus);

    /**
     * 检查状态转换是否合法
     */
    boolean isValidStatusTransition(String oldStatus, String newStatus);

    /**
     * 扣减申请积分
     */
    boolean deductApplicationPoints(Long userId, String businessType, String relatedId);

    /**
     * 申请积分回退
     */
    boolean refundApplicationPoints(Long userId, String businessType, String relatedId);
}