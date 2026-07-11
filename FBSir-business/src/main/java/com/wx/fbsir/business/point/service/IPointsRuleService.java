package com.wx.fbsir.business.point.service;

import java.util.List;
import com.wx.fbsir.business.point.domain.PointsRule;

/**
 * 积分规则Service接口
 * 
 * @author FBSir
 * @date 2025-12-08
 */
public interface IPointsRuleService {
    /**
     * 查询积分规则
     * 
     * @param ruleId 积分规则ID
     * @return 积分规则
     */
    public PointsRule selectPointsRuleByRuleId(Long ruleId);

    /**
     * 根据规则编码获取积分规则配置
     * 
     * @param ruleCode 规则编码（唯一标识）
     * @return 积分规则
     */
    public PointsRule getRuleByCode(String ruleCode);

    /**
     * 查询积分规则列表
     * 
     * @param pointsRule 积分规则
     * @return 积分规则集合
     */
    public List<PointsRule> selectPointsRuleList(PointsRule pointsRule);

    /**
     * 查询所有启用的积分规则（用于任务清单）
     * 
     * @return 积分规则集合
     */
    public List<PointsRule> selectEnabledPointsRuleList();

    /**
     * 新增积分规则
     * 
     * @param pointsRule 积分规则
     * @return 结果
     */
    public int insertPointsRule(PointsRule pointsRule);

    /**
     * 修改积分规则
     * 
     * @param pointsRule 积分规则
     * @return 结果
     */
    public int updatePointsRule(PointsRule pointsRule);

    /**
     * 批量删除积分规则
     * 
     * @param ruleIds 需要删除的积分规则ID
     * @return 结果
     */
    public int deletePointsRuleByRuleIds(Long[] ruleIds);

    /**
     * 删除积分规则信息
     * 
     * @param ruleId 积分规则ID
     * @return 结果
     */
    public int deletePointsRuleByRuleId(Long ruleId);

    /**
     * 校验限频规则
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码
     * @param rule 规则对象
     * @return true-通过校验，false-未通过
     */
    public boolean checkLimit(Long userId, String ruleCode, PointsRule rule);

    /**
     * 业务成功后记录一次限频计数（避免失败也占用额度）
     */
    public void markLimit(Long userId, String ruleCode, PointsRule rule);

    /**
     * 校验累计上限
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码
     * @param amount 积分数量
     * @param rule 规则对象
     * @return true-通过校验，false-未通过
     */
    public boolean checkMaxAmount(Long userId, String ruleCode, Integer amount, PointsRule rule);
}

