package com.wx.fbsir.business.certificate.service.impl;

import com.wx.fbsir.business.certificate.domain.CertPointsRule;
import com.wx.fbsir.business.certificate.mapper.CertPointsRuleMapper;
import com.wx.fbsir.business.certificate.service.ICertificatePointsService;
import com.wx.fbsir.business.point.domain.PointsResult;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.business.point.service.PointsPrecheckService;
import com.wx.fbsir.common.exception.ServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证申请积分服务
 *
 * @author fbsir
 * @date 2026-01-08
 */
@Service
public class CertificatePointsServiceImpl implements ICertificatePointsService {

    @Autowired
    private CertPointsRuleMapper baseMapper;

    @Autowired
    private com.wx.fbsir.system.mapper.SysUserMapper sysUserMapper; // 用于操作sys_user表的积分字段
    
    @Autowired
    private IPointsService pointsService;
    
    @Autowired
    private PointsPrecheckService pointsPrecheckService;

    @Override
    public CertPointsRule getCertPointsRuleByBusinessType(String businessType) {
        // 将业务类型转换为积分规则编码
        String ruleCode = getPointsRuleCodeByBusinessType(businessType);
        
        java.util.List<CertPointsRule> list = baseMapper.selectCertPointsRuleByRuleCode(ruleCode);
        if (list != null && !list.isEmpty()) {
            CertPointsRule rule = list.get(0);
            // 设置业务类型以保持兼容性
            rule.setBusinessType(businessType);
            return rule;
        }
        return null;
    }

    /**
     * 根据业务类型获取积分规则编码
     */
    private String getPointsRuleCodeByBusinessType(String businessType) {
        // 将业务类型转换为积分规则编码
        switch (businessType) {
            case "CERTIFICATE_APPLICATION":
                return "APPLY_CERTIFICATE"; // 申请证书
            case "CERT_ISSUE":
                return "ISSUE_CERTIFICATES"; // 发放证书
            case "CERT_LISTING":
                return "SHELF_CERTIFICATE_TEMPLATE"; // 上架证书模板
            case "RECEIVE_CERTIFICATE":
                return "RECEIVE_CERTIFICATE"; // 领取证书
            default:
                return businessType.toLowerCase().replaceAll("[^a-z0-9_]+", "_"); // 默认转换
        }
    }

    @Override
    public Integer getUserCurrentPoints(Long userId) {
        // 从sys_user表获取用户当前积分
        com.wx.fbsir.common.core.domain.entity.SysUser user = sysUserMapper.selectUserById(userId);
        if (user != null) {
            return user.getPoints();
        }
        return 0;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deductPointsForBusiness(Long userId, String businessType, String relatedId) {
        // 获取认证积分规则
        CertPointsRule rule = getCertPointsRuleByBusinessType(businessType);
        if (rule == null) {
            throw new ServiceException("未找到[" + businessType + "]对应的积分规则");
        }
        
        // 使用积分管理模块的规则编码
        String ruleCode = getPointsRuleCodeByBusinessType(businessType);
        
        // 检查用户当前积分是否足够
        Integer userPoints = getUserCurrentPoints(userId);
        // 积分规则中的pointsValue为负值表示扣减，正值表示奖励，这里需要取绝对值作为扣减量
        Integer requiredPoints = rule.getDeductPoints();
        if (requiredPoints == null) {
            // 如果没有找到规则，默认需要0积分
            requiredPoints = 0;
        }
        
        if (userPoints < requiredPoints) {
            throw new ServiceException("用户积分不足，当前积分为: " + userPoints + ", 需要积分: " + requiredPoints);
        }
        
        // 通过积分预检查服务进行扣减
        PointsResult result = pointsPrecheckService.tryChangePoints(userId, ruleCode, null);
        return result.isSuccess();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean refundPointsForBusiness(Long userId, String businessType, String relatedId) {
        // 使用积分管理模块的规则编码，但积分回退需要特殊处理
        String ruleCode = getPointsRuleCodeByBusinessType(businessType);
        
        // 获取原积分规则，以获得回退的积分数值
        CertPointsRule rule = getCertPointsRuleByBusinessType(businessType);
        if (rule == null) {
            throw new ServiceException("未配置[" + businessType + "]对应的积分规则");
        }
        
        // 获取原扣减积分数，并转换为正数（用于回退）
        Integer refundPoints = Math.abs(rule.getDeductPoints() != null ? rule.getDeductPoints() : 0);
        
        // 使用积分服务进行积分增加
        com.wx.fbsir.common.core.domain.AjaxResult result = pointsService.changePoints(
            userId, 
            ruleCode, 
            refundPoints  // 正数表示增加积分
        );
        
        return result.isSuccess();
    }
}