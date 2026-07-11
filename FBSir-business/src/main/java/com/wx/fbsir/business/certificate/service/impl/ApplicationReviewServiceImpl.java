package com.wx.fbsir.business.certificate.service.impl;

import com.wx.fbsir.business.certificate.domain.ApplicationReview;
import com.wx.fbsir.business.certificate.domain.vo.ApplicationReviewVo;
import com.wx.fbsir.business.certificate.mapper.ApplicationReviewMapper;
import com.wx.fbsir.business.certificate.service.IApplicationReviewService;
import com.wx.fbsir.business.point.domain.PointsResult;
import com.wx.fbsir.business.point.service.PointsPrecheckService;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 申请审核记录Service业务层处理
 * 
 * @author FBSir
 */
@Service
public class ApplicationReviewServiceImpl implements IApplicationReviewService
{
    @Autowired
    private ApplicationReviewMapper applicationReviewMapper;
    
    @Autowired
    private com.wx.fbsir.business.certificate.service.ICertificateApplicationService certificateApplicationService;
    
    @Autowired
    private com.wx.fbsir.business.certificate.mapper.CertificateApplicationMapper certificateApplicationMapper;
    
    @Autowired
    private PointsPrecheckService pointsPrecheckService;
    
    @Autowired
    private ISysUserService sysUserService;

    /**
     * 查询申请审核记录
     * 
     * @param reviewId 申请审核记录ID
     * @return 申请审核记录
     */
    @Override
    public ApplicationReview selectApplicationReviewById(Long reviewId)
    {
        return applicationReviewMapper.selectApplicationReviewById(reviewId);
    }

    /**
     * 查询申请审核记录列表
     * 
     * @param applicationReview 申请审核记录
     * @return 申请审核记录
     */
    @Override
    public List<ApplicationReview> selectApplicationReviewList(ApplicationReview applicationReview)
    {
        return applicationReviewMapper.selectApplicationReviewList(applicationReview);
    }
    
    /**
     * 查询申请审核记录列表（带详细信息）
     * 
     * @param applicationReview 申请审核记录
     * @return 申请审核记录
     */
    @Override
    public List<ApplicationReviewVo> selectApplicationReviewListWithDetails(ApplicationReview applicationReview)
    {
        return applicationReviewMapper.selectApplicationReviewListWithDetails(applicationReview);
    }

    /**
     * 新增申请审核记录
     * 
     * @param applicationReview 申请审核记录
     * @return 结果
     */
    @Override
    public int insertApplicationReview(ApplicationReview applicationReview)
    {
        return applicationReviewMapper.insertApplicationReview(applicationReview);
    }

    /**
     * 修改申请审核记录
     * 
     * @param applicationReview 申请审核记录
     * @return 结果
     */
    @Override
    public int updateApplicationReview(ApplicationReview applicationReview)
    {
        return applicationReviewMapper.updateApplicationReview(applicationReview);
    }

    /**
     * 批量删除申请审核记录
     * 
     * @param reviewIds 需要删除的申请审核记录ID
     * @return 结果
     */
    @Override
    public int deleteApplicationReviewByIds(Long[] reviewIds)
    {
        return applicationReviewMapper.deleteApplicationReviewByIds(reviewIds);
    }

    /**
     * 删除申请审核记录信息
     * 
     * @param reviewId 申请审核记录ID
     * @return 结果
     */
    @Override
    public int deleteApplicationReviewById(Long reviewId)
    {
        return applicationReviewMapper.deleteApplicationReviewById(reviewId);
    }
    
    /**
     * 审核认证申请
     * 
     * @param applicationReview 审核信息
     * @return 结果
     */
    @Override
    @Transactional
    public int reviewApplication(ApplicationReview applicationReview)
    {
        // 查询当前申请信息
        com.wx.fbsir.business.certificate.domain.CertificateApplication currentApplication = 
            certificateApplicationService.selectCertificateApplicationById(applicationReview.getApplicationId());
        
        if (currentApplication == null) {
            throw new RuntimeException("申请不存在");
        }
        
        // 更新证书申请表的状态
        com.wx.fbsir.business.certificate.domain.CertificateApplication updateApplication = 
            new com.wx.fbsir.business.certificate.domain.CertificateApplication();
        updateApplication.setApplicationId(applicationReview.getApplicationId());
        
        // 如果审核结果为通过(APPROVED)，则需要扣除积分并生成证书编号
        if ("2".equals(applicationReview.getApplicationStatus()) || "APPROVED".equals(applicationReview.getApplicationStatus())) {
            // 获取申请人信息
            SysUser applicantUser = sysUserService.selectUserById(currentApplication.getUserId());
            if (applicantUser == null) {
                throw new RuntimeException("申请人不存在");
            }
            
            // 生成证书编号
            String certificateId = generateUniqueCertificateId();
            updateApplication.setCertificateId(certificateId);
        }
        
        // 执行积分扣除操作（使用ISSUE_CERTIFICATES规则）
        SysUser applicantUser = sysUserService.selectUserById(currentApplication.getUserId());
        if (applicantUser != null) {
            PointsResult pointsResult = pointsPrecheckService.tryChangePoints(applicantUser.getUserId(), "ISSUE_CERTIFICATES", null);
            if (!pointsResult.isSuccess()) {
                throw new RuntimeException("积分扣除失败: " + pointsResult.getMsg());
            }
        }
        updateApplication.setApplicationStatus(applicationReview.getApplicationStatus());
        // 设置审核备注，直接使用审核意见，不再设置默认值
        String reviewOpinion = applicationReview.getReviewOpinion();
        updateApplication.setReviewRemark(reviewOpinion);
        updateApplication.setReviewTime(new java.util.Date());
        
        // 如果审核结果为通过(APPROVED)，则设置审批通过时间
        if ("2".equals(applicationReview.getApplicationStatus()) || "APPROVED".equals(applicationReview.getApplicationStatus())) {
            // 只有在还没有设置通过时间的情况下才设置
            if (currentApplication.getApproveTime() == null) {
                updateApplication.setApproveTime(new java.util.Date());
            }
        }
        
        int result = certificateApplicationService.updateCertificateApplication(updateApplication);
        
        // 插入或更新审核记录
        applicationReview.setReviewTime(new java.util.Date());
        if (applicationReview.getReviewId() == null) {
            result += applicationReviewMapper.insertApplicationReview(applicationReview);
        } else {
            result += applicationReviewMapper.updateApplicationReview(applicationReview);
        }
        
        return result;
    }
    
    /**
     * 生成唯一的证书编号
     * @return 证书编号字符串
     */
    private String generateUniqueCertificateId() {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "CERT";
        
        // 从数据库获取当天的最大序号
        long maxSeq = 0;
        try {
            maxSeq = certificateApplicationMapper.selectMaxCertificateSequenceForDate(dateStr, prefix);
        } catch (Exception e) {
            // 如果查询失败，从0开始
            maxSeq = 0;
        }
        
        // 递增序号
        maxSeq++;
        
        // 确保序列号为6位，不足前面补0
        String seqStr = String.format("%06d", maxSeq);
        
        return prefix + dateStr + seqStr;
    }
}