package com.wx.fbsir.business.certificate.service;

import com.wx.fbsir.business.certificate.domain.CertPointsRule;

/**
 * 认证申请积分服务接口
 *
 * @author fbsir
 * @date 2026-01-08
 */
public interface ICertificatePointsService {

    /**
     * 根据业务类型获取积分规则
     */
    CertPointsRule getCertPointsRuleByBusinessType(String businessType);

    /**
     * 获取用户当前积分
     */
    Integer getUserCurrentPoints(Long userId);

    /**
     * 扣减业务积分
     */
    boolean deductPointsForBusiness(Long userId, String businessType, String relatedId);

    /**
     * 积分回退
     */
    boolean refundPointsForBusiness(Long userId, String businessType, String relatedId);
}