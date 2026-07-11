package com.wx.fbsir.business.certificate.mapper;

import com.wx.fbsir.business.certificate.domain.CertificateApplication;
import com.wx.fbsir.business.certificate.domain.CertificateApplicationManagementVO;

import java.util.List;

/**
 * 认证申请Mapper接口
 * 
 * @author FBSir
 */
public interface CertificateApplicationMapper 
{
    /**
     * 查询认证申请
     * 
     * @param applicationId 认证申请ID
     * @return 认证申请
     */
    public CertificateApplication selectCertificateApplicationById(Long applicationId);

    /**
     * 查询认证申请列表
     * 
     * @param certificateApplication 认证申请
     * @return 认证申请集合
     */
    public List<CertificateApplication> selectCertificateApplicationList(CertificateApplication certificateApplication);

    /**
     * 新增认证申请
     * 
     * @param certificateApplication 认证申请
     * @return 结果
     */
    public int insertCertificateApplication(CertificateApplication certificateApplication);

    /**
     * 修改认证申请
     * 
     * @param certificateApplication 认证申请
     * @return 结果
     */
    public int updateCertificateApplication(CertificateApplication certificateApplication);

    /**
     * 删除认证申请
     * 
     * @param applicationId 认证申请ID
     * @return 结果
     */
    public int deleteCertificateApplicationById(Long applicationId);

    /**
     * 批量删除认证申请
     * 
     * @param applicationIds 需要删除的认证申请ID
     * @return 结果
     */
    public int deleteCertificateApplicationByIds(Long[] applicationIds);

    /**
     * 根据用户ID查询认证申请列表
     *
     * @param userId 用户ID
     * @return 认证申请集合
     */
    public List<CertificateApplication> selectCertificateApplicationByUserId(Long userId);

    /**
     * 根据日期和前缀查询当天最大序号
     *
     * @param dateStr 日期字符串
     * @param prefix 前缀
     * @return 最大序号
     */
    public long selectMaxSequenceForDate(String dateStr, String prefix);

    /**
     * 根据日期和前缀查询当天证书编号最大序号
     *
     * @param dateStr 日期字符串
     * @param prefix 前缀
     * @return 最大序号
     */
    public long selectMaxCertificateSequenceForDate(String dateStr, String prefix);

    /**
     * 查询证书发放列表（基于申请表）
     *
     * @param certificateApplication 查询条件
     * @return 证书发放集合
     */
    public List<CertificateApplicationManagementVO> selectCertificateIssuanceList(CertificateApplication certificateApplication);

    /**
     * 查询证书发放详情（基于申请表）
     *
     * @param applicationId 申请ID
     * @return 证书发放信息
     */
    public CertificateApplicationManagementVO selectCertificateIssuanceById(Long applicationId);
}