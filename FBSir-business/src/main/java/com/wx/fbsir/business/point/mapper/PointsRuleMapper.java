package com.wx.fbsir.business.point.mapper;

import java.util.List;
import com.wx.fbsir.business.point.domain.PointsRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 积分规则Mapper接口
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Mapper
public interface PointsRuleMapper {
    /**
     * 查询积分规则
     * 
     * @param ruleId 积分规则ID
     * @return 积分规则
     */
    public PointsRule selectPointsRuleByRuleId(Long ruleId);

    /**
     * 根据规则编码查询积分规则
     * 
     * @param ruleCode 规则编码
     * @return 积分规则
     */
    public PointsRule selectPointsRuleByRuleCode(String ruleCode);

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
     * 删除积分规则
     * 
     * @param ruleId 积分规则ID
     * @return 结果
     */
    public int deletePointsRuleByRuleId(Long ruleId);

    /**
     * 批量删除积分规则
     * 
     * @param ruleIds 需要删除的数据ID
     * @return 结果
     */
    public int deletePointsRuleByRuleIds(Long[] ruleIds);
}

