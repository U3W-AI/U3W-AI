package com.wx.fbsir.business.certificate.service.impl;

import com.wx.fbsir.business.certificate.domain.CertificateApplication;
import com.wx.fbsir.business.certificate.service.ICertificateApplicationCoreService;
import com.wx.fbsir.business.certificate.service.ICertificateApplicationService;
import com.wx.fbsir.business.certificate.service.ICertificatePointsService;
import com.wx.fbsir.business.certificate.util.ApplicationNumberGenerator;
import com.wx.fbsir.business.certificate.util.ApplicationStatusUtil;
import com.wx.fbsir.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 认证申请核心业务服务
 * 包含状态机管理和积分扣减逻辑
 *
 * @author FBSir
 */
@Service
public class CertificateApplicationCoreService implements ICertificateApplicationCoreService {

    @Autowired
    private ICertificateApplicationService certificateApplicationService;



    @Autowired
    private ICertificatePointsService iCertificatePointsService;

    /**
     * 更新申请状态（带状态流转校验）
     */
    @Transactional
    public void updateApplicationStatus(Long applicationId, String newStatus, Long operatorId, String remark) {
        // 查询当前申请信息
        CertificateApplication application = certificateApplicationService.selectCertificateApplicationById(applicationId);
        if (application == null) {
            throw new ServiceException("申请不存在");
        }

        String oldStatus = application.getApplicationStatus();

        // 校验状态流转合法性
        if (!isValidStatusTransition(oldStatus, newStatus)) {
            throw new ServiceException("不允许从[" + ApplicationStatusUtil.getStatusDescription(oldStatus) + "]状态转换至[" + 
                ApplicationStatusUtil.getStatusDescription(newStatus) + "]状态");
        }

        // 更新状态
        application.setApplicationStatus(newStatus);
        // 如果是已批准或已发放状态，设置审批通过时间
        if (ApplicationStatusUtil.APPROVED.equals(newStatus) || ApplicationStatusUtil.ISSUED.equals(newStatus)) {
            // 只有在还没有设置通过时间的情况下才设置
            if (application.getApproveTime() == null) {
                application.setApproveTime(new Date());
            }
        }
        application.setUpdateBy(operatorId.toString());
        application.setUpdateTime(new Date());
        certificateApplicationService.updateCertificateApplication(application);
    }



    /**
     * 提交认证申请（含积分扣减）
     */
    @Transactional
    public boolean submitApplicationWithPointsDeduction(Long applicationId, Long userId) {
        // 扣減積分（業務類型為申請提交）
        boolean deducted = iCertificatePointsService.deductPointsForBusiness(
            userId, 
            "CERTIFICATE_APPLICATION", 
            applicationId.toString()
        );

        if (deducted) {
            // 更新申請狀態為待審核
            updateApplicationStatus(applicationId, ApplicationStatusUtil.PENDING_REVIEW, userId, "申請提交，積分扣減成功");
            return true;
        } else {
            return false; // 積分不足，提交失敗
        }
    }

    /**
     * 提交認證申請（創建申請並扣減積分）
     */
    @Transactional
    public com.wx.fbsir.common.core.domain.AjaxResult submitApplication(CertificateApplication application) {
        try {
            // 從安全上下文獲取用戶ID
            Long userId = com.wx.fbsir.common.utils.SecurityUtils.getUserId();
            
            // 設置用戶ID和其他必要字段
            application.setUserId(userId);
            application.setApplicationStatus(ApplicationStatusUtil.DRAFT); // 0 - 草稿
            application.setApplicationNumber(ApplicationNumberGenerator.generateApplicationNumber());
            application.setCreateTime(new Date());
            application.setUpdateTime(new Date());
            
            int result = certificateApplicationService.insertCertificateApplication(application);
            if (result <= 0) {
                return com.wx.fbsir.common.core.domain.AjaxResult.error("保存申請失敗");
            }
            
            Long applicationId = application.getApplicationId();
            
            // 2. 扣減積分（業務類型為申請提交）
            boolean deducted = iCertificatePointsService.deductPointsForBusiness(
                userId, 
                "CERTIFICATE_APPLICATION", 
                applicationId.toString()
            );

            if (deducted) {
                // 3. 更新申請狀態為待審核
                updateApplicationStatus(applicationId, ApplicationStatusUtil.PENDING_REVIEW, userId, "申請提交，積分扣減成功"); // 1 - 待審核
                return com.wx.fbsir.common.core.domain.AjaxResult.success("申請提交成功");
            } else {
                return com.wx.fbsir.common.core.domain.AjaxResult.error("積分不足，申請提交失敗");
            }
        } catch (Exception e) {
            throw new ServiceException("提交申請失敗: " + e.getMessage());
        }
    }

    @Override
    public boolean submitApplication(Long applicationId, Long userId) {
        // 扣減積分（業務類型為申請提交）
        boolean deducted = iCertificatePointsService.deductPointsForBusiness(
            userId,
            "CERTIFICATE_APPLICATION",
            applicationId.toString()
        );

        if (deducted) {
            // 更新申請狀態為待審核
            updateApplicationStatus(applicationId, ApplicationStatusUtil.PENDING_REVIEW, userId, "申請提交，積分扣減成功");
            return true;
        } else {
            return false; // 積分不足，提交失敗
        }
    }

    @Override
    public boolean withdrawApplication(Long applicationId, Long userId) {
        try {
            // 查詢當前申請信息
            CertificateApplication application = certificateApplicationService.selectCertificateApplicationById(applicationId);
            if (application == null) {
                throw new ServiceException("申請不存在");
            }

            String currentStatus = application.getApplicationStatus();
            // 只有待審核狀態的申請可以撤回
            if (!ApplicationStatusUtil.PENDING_REVIEW.equals(currentStatus)) {
                throw new ServiceException("只有待審核狀態的申請才能撤回");
            }

            // 更新狀態為草稿
            updateApplicationStatus(applicationId, ApplicationStatusUtil.DRAFT, userId, "申請已撤回");
            return true;
        } catch (Exception e) {
            throw new ServiceException("撤回申請失敗: " + e.getMessage());
        }
    }

    @Override
    public boolean reviewApplication(Long applicationId, String status, String reviewOpinion, Long reviewerId) {
        try {
            // 查询当前申请信息
            CertificateApplication application = certificateApplicationService.selectCertificateApplicationById(applicationId);
            if (application == null) {
                throw new ServiceException("申请不存在");
            }

            String oldStatus = application.getApplicationStatus();

            // 校验状态流转合法性
            if (!isValidStatusTransition(oldStatus, status)) {
                throw new ServiceException("不允许从[" + ApplicationStatusUtil.getStatusDescription(oldStatus) + "]状态转换至[" + 
                    ApplicationStatusUtil.getStatusDescription(status) + "]状态");
            }

            // 创建更新对象
            CertificateApplication updateApplication = new CertificateApplication();
            updateApplication.setApplicationId(applicationId);
            updateApplication.setApplicationStatus(status);
            updateApplication.setReviewRemark(reviewOpinion);
            updateApplication.setUpdateBy(reviewerId.toString());
            updateApplication.setUpdateTime(new Date());

            // 如果审核结果为通过(APPROVED)，则生成证书编号
            if (ApplicationStatusUtil.APPROVED.equals(status)) {
                String certificateId = com.wx.fbsir.business.certificate.util.CertificateIdGenerator.generateCertificateId();
                updateApplication.setCertificateId(certificateId);
                // 设置审批通过时间
                updateApplication.setApproveTime(new Date());
            }

            // 更新申请状态
            certificateApplicationService.updateCertificateApplication(updateApplication);
            return true;
        } catch (Exception e) {
            throw new ServiceException("审核申请失败: " + e.getMessage());
        }
    }

    @Override
    public boolean updateApplicationStatus(CertificateApplication application, String newStatus) {
        try {
            // 查询当前申请信息
            CertificateApplication existingApplication = certificateApplicationService.selectCertificateApplicationById(application.getApplicationId());
            if (existingApplication == null) {
                throw new ServiceException("申请不存在");
            }

            String oldStatus = existingApplication.getApplicationStatus();

            // 校验状态流转合法性
            if (!isValidStatusTransition(oldStatus, newStatus)) {
                throw new ServiceException("不允许从[" + ApplicationStatusUtil.getStatusDescription(oldStatus) + "]状态转换至[" + 
                    ApplicationStatusUtil.getStatusDescription(newStatus) + "]状态");
            }

            // 更新状态
            existingApplication.setApplicationStatus(newStatus);
            existingApplication.setUpdateTime(new Date());
            certificateApplicationService.updateCertificateApplication(existingApplication);
            return true;
        } catch (Exception e) {
            throw new ServiceException("更新申请状态失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isValidStatusTransition(String oldStatus, String newStatus) {
        return isValidStatusTransitionInternal(oldStatus, newStatus);
    }

    @Override
    public boolean deductApplicationPoints(Long userId, String businessType, String relatedId) {
        return iCertificatePointsService.deductPointsForBusiness(userId, businessType, relatedId);
    }

    @Override
    public boolean refundApplicationPoints(Long userId, String businessType, String relatedId) {
        return iCertificatePointsService.refundPointsForBusiness(userId, businessType, relatedId);
    }

    // 內部輔助方法，避免與接口方法同名
    private boolean isValidStatusTransitionInternal(String oldStatus, String newStatus) {
        switch (oldStatus) {
            case ApplicationStatusUtil.DRAFT: // 草稿
                return ApplicationStatusUtil.PENDING_REVIEW.equals(newStatus) || ApplicationStatusUtil.REJECTED.equals(newStatus);
            case ApplicationStatusUtil.PENDING_REVIEW: // 待審核
                return ApplicationStatusUtil.APPROVED.equals(newStatus) || ApplicationStatusUtil.REJECTED.equals(newStatus);
            case ApplicationStatusUtil.APPROVED: // 已批准
                return ApplicationStatusUtil.ISSUED.equals(newStatus); // 已批准後可發放證書
            case ApplicationStatusUtil.REJECTED: // 已駁回
                return ApplicationStatusUtil.PENDING_REVIEW.equals(newStatus);
            case ApplicationStatusUtil.ISSUED: // 已發放
                return false; // 已發放狀態為終態
            default:
                return false;
        }
    }
}