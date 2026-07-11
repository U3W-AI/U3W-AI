package com.wx.fbsir.business.certificate.mapper;

import com.wx.fbsir.business.certificate.domain.CertPointsRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 认证积分规则Mapper接口（使用现有的wx_points_rule表）
 */
@Mapper
public interface CertPointsRuleMapper {

    /**
     * 根据业务类型查询认证积分规则
     *
     * @param certPointsRule 查询条件
     * @return 认证积分规则集合
     */
    List<CertPointsRule> selectCertPointsRuleList(@Param("certPointsRule") CertPointsRule certPointsRule);
    
    /**
     * 根据规则编码查询认证积分规则
     *
     * @param ruleCode 规则编码
     * @return 认证积分规则
     */
    List<CertPointsRule> selectCertPointsRuleByRuleCode(@Param("ruleCode") String ruleCode);
}