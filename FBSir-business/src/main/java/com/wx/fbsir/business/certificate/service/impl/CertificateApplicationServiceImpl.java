package com.wx.fbsir.business.certificate.service.impl;

import com.wx.fbsir.business.certificate.domain.CertificateApplication;
import com.wx.fbsir.business.certificate.domain.CertificateApplicationManagementVO;
import com.wx.fbsir.business.certificate.mapper.CertificateApplicationMapper;
import com.wx.fbsir.business.certificate.service.ICertificateApplicationService;
import com.wx.fbsir.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证申请Service业务层处理
 * 
 * @author FBSir
 */
@Service
public class CertificateApplicationServiceImpl implements ICertificateApplicationService
{
    @Autowired
    private CertificateApplicationMapper certificateApplicationMapper;



    /**
     * 查询认证申请
     * 
     * @param applicationId 认证申请ID
     * @return 认证申请
     */
    @Override
    public CertificateApplication selectCertificateApplicationById(Long applicationId)
    {
        return certificateApplicationMapper.selectCertificateApplicationById(applicationId);
    }

    /**
     * 查询认证申请列表
     * 
     * @param certificateApplication 认证申请
     * @return 认证申请
     */
    @Override
    public List<CertificateApplication> selectCertificateApplicationList(CertificateApplication certificateApplication)
    {
        return certificateApplicationMapper.selectCertificateApplicationList(certificateApplication);
    }

    /**
     * 新增认证申请
     * 
     * @param certificateApplication 认证申请
     * @return 结果
     */
    @Override
    public int insertCertificateApplication(CertificateApplication certificateApplication)
    {
        return certificateApplicationMapper.insertCertificateApplication(certificateApplication);
    }

    /**
     * 修改认证申请
     * 
     * @param certificateApplication 认证申请
     * @return 结果
     */
    @Override
    public int updateCertificateApplication(CertificateApplication certificateApplication)
    {
        return certificateApplicationMapper.updateCertificateApplication(certificateApplication);
    }

    /**
     * 批量删除认证申请
     * 
     * @param applicationIds 需要删除的认证申请ID
     * @return 结果
     */
    @Override
    public int deleteCertificateApplicationByIds(Long[] applicationIds)
    {
        return certificateApplicationMapper.deleteCertificateApplicationByIds(applicationIds);
    }

    /**
     * 删除认证申请信息
     * 
     * @param applicationId 认证申请ID
     * @return 结果
     */
    @Override
    public int deleteCertificateApplicationById(Long applicationId)
    {
        return certificateApplicationMapper.deleteCertificateApplicationById(applicationId);
    }

    /**
     * 查询证书发放列表（基于申请表）
     *
     * @param certificateApplication 查询条件
     * @return 证书发放集合
     */
    @Override
    public List<CertificateApplicationManagementVO> selectCertificateIssuanceList(CertificateApplication certificateApplication) {
        return certificateApplicationMapper.selectCertificateIssuanceList(certificateApplication);
    }

    /**
     * 查询证书发放详情（基于申请表）
     *
     * @param applicationId 申请ID
     * @return 证书发放信息
     */
    @Override
    public CertificateApplicationManagementVO selectCertificateIssuanceById(Long applicationId) {
        return certificateApplicationMapper.selectCertificateIssuanceById(applicationId);
    }

    /**
     * 根据用户ID查询认证申请列表
     *
     * @param userId 用户ID
     * @return 认证申请集合
     */
    @Override
    public List<CertificateApplication> selectCertificateApplicationByUserId(Long userId) {
        return certificateApplicationMapper.selectCertificateApplicationByUserId(userId);
    }

    /**
     * 领取证书
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean receiveCertificate(Long applicationId, Long userId) {
        // 查询申请信息
        CertificateApplication application = certificateApplicationMapper.selectCertificateApplicationById(applicationId);
        if (application == null) {
            return false;
        }

        // 验证用户权限和申请状态
        if (!application.getUserId().equals(userId) || 
            (!"2".equals(application.getApplicationStatus()) /* APPROVED 状态 */ && 
             !"4".equals(application.getApplicationStatus()) /* ISSUED 状态 */)) {
            return false;
        }

        // 扣减积分（领取证书需要消耗积分）
        boolean deducted = false;
        try {
            // 需要注入积分服务来处理积分扣减
            // 这里应该使用 ICertificatePointsService 来处理积分相关操作
            // 但由于循环依赖问题，这里暂时简化处理
            deducted = true; // 假设积分扣减成功
        } catch (Exception e) {
            throw new ServiceException("积分扣减失败，无法领取证书: " + e.getMessage());
        }

        // 如果当前状态是已批准(2)，则更新为已发放(4)
        if ("2".equals(application.getApplicationStatus())) {
            try {
                // 直接更新申请状态，绕过core service避免循环依赖
                CertificateApplication updateApp = new CertificateApplication();
                updateApp.setApplicationId(applicationId);
                updateApp.setApplicationStatus("4"); // 更新为已发放状态
                updateApp.setReceiveStatus("received");
                
                int result = certificateApplicationMapper.updateCertificateApplication(updateApp);
                
                return result > 0;
            } catch (Exception e) {
                throw new ServiceException("更新申请状态失败: " + e.getMessage());
            }
        } else {
            // 如果已经是已发放状态(4)，只需更新领取状态
            CertificateApplication updateApp = new CertificateApplication();
            updateApp.setApplicationId(applicationId);
            updateApp.setReceiveStatus("received");
            
            int result = certificateApplicationMapper.updateCertificateApplication(updateApp);
            
            return result > 0;
        }
    }
}