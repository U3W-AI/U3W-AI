package com.wx.fbsir.business.certificate.service.impl;

import com.wx.fbsir.business.certificate.domain.CertificateTemplate;
import com.wx.fbsir.business.certificate.mapper.CertificateTemplateMapper;
import com.wx.fbsir.business.certificate.service.ICertificatePointsService;
import com.wx.fbsir.business.certificate.service.ICertificateTemplateService;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 证书模板Service业务层处理
 * 
 * @author wxfbsir
 */
@Service
public class CertificateTemplateServiceImpl implements ICertificateTemplateService
{
    @Autowired
    private CertificateTemplateMapper certificateTemplateMapper;
    
    @Autowired
    private ICertificatePointsService certificatePointsService;

    /**
     * 查询证书模板
     * 
     * @param templateId 证书模板ID
     * @return 证书模板
     */
    @Override
    public CertificateTemplate selectCertificateTemplateById(Long templateId)
    {
        return certificateTemplateMapper.selectCertificateTemplateById(templateId);
    }

    /**
     * 查询证书模板列表
     * 
     * @param certificateTemplate 证书模板
     * @return 证书模板
     */
    @Override
    public List<CertificateTemplate> selectCertificateTemplateList(CertificateTemplate certificateTemplate)
    {
        return certificateTemplateMapper.selectCertificateTemplateList(certificateTemplate);
    }

    /**
     * 新增证书模板
     * 
     * @param certificateTemplate 证书模板
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insertCertificateTemplate(CertificateTemplate certificateTemplate)
    {
        // 在新增证书模板前扣除积分
        Long userId = SecurityUtils.getUserId();
        boolean deducted = certificatePointsService.deductPointsForBusiness(
            userId, 
            "CERT_LISTING", // 业务类型为证书模板上架
            String.valueOf(System.currentTimeMillis()) // 使用当前时间戳作为关联ID
        );
        
        if (!deducted) {
            throw new RuntimeException("积分不足，无法上架证书模板");
        }
        
        return certificateTemplateMapper.insertCertificateTemplate(certificateTemplate);
    }

    /**
     * 修改证书模板
     * 
     * @param certificateTemplate 证书模板
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateCertificateTemplate(CertificateTemplate certificateTemplate)
    {
        return certificateTemplateMapper.updateCertificateTemplate(certificateTemplate);
    }

    /**
     * 批量删除证书模板
     * 
     * @param templateIds 需要删除的证书模板ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCertificateTemplateByIds(Long[] templateIds)
    {
        return certificateTemplateMapper.deleteCertificateTemplateByIds(templateIds);
    }

    /**
     * 删除证书模板信息
     * 
     * @param templateId 证书模板ID
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteCertificateTemplateById(Long templateId)
    {
        return certificateTemplateMapper.deleteCertificateTemplateById(templateId);
    }
}