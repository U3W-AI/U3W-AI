package com.wx.fbsir.business.certificate.service;

import com.wx.fbsir.business.certificate.domain.CertificateTemplate;

import java.util.List;

/**
 * 证书模板Service接口
 * 
 * @author wxfbsir
 */
public interface ICertificateTemplateService 
{
    /**
     * 查询证书模板
     * 
     * @param templateId 证书模板ID
     * @return 证书模板
     */
    public CertificateTemplate selectCertificateTemplateById(Long templateId);

    /**
     * 查询证书模板列表
     * 
     * @param certificateTemplate 证书模板
     * @return 证书模板集合
     */
    public List<CertificateTemplate> selectCertificateTemplateList(CertificateTemplate certificateTemplate);

    /**
     * 新增证书模板
     * 
     * @param certificateTemplate 证书模板
     * @return 结果
     */
    public int insertCertificateTemplate(CertificateTemplate certificateTemplate);

    /**
     * 修改证书模板
     * 
     * @param certificateTemplate 证书模板
     * @return 结果
     */
    public int updateCertificateTemplate(CertificateTemplate certificateTemplate);

    /**
     * 批量删除证书模板
     * 
     * @param templateIds 需要删除的证书模板ID
     * @return 结果
     */
    public int deleteCertificateTemplateByIds(Long[] templateIds);

    /**
     * 删除证书模板信息
     * 
     * @param templateId 证书模板ID
     * @return 结果
     */
    public int deleteCertificateTemplateById(Long templateId);
}