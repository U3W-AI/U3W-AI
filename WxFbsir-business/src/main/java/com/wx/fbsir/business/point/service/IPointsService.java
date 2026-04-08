package com.wx.fbsir.business.point.service;

import java.util.List;
import java.util.Map;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.business.point.domain.PointsRecord;

/**
 * 积分Service接口
 * 
 * @author wxfbsir
 * @date 2025-12-08
 */
public interface IPointsService {
    /**
     * 积分发放/扣减
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码（唯一标识）
     * @param changeAmount 积分变动值（可选，不传则使用规则默认值）
     * @return 结果
     */
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount);

    /**
     * Skill 消费场景包的轻量重载：复用现有积分扣减逻辑，并写入 FBS 关联字段。
     *
     * 不做冻结预占，不做并发大改造。若 ruleCode 为空（免费包），直接返回成功。
     *
     * @param userId 用户ID
     * @param ruleCode 规则编码（空表示免费包）
     * @param changeAmount 积分变动值
     * @param scenePackId 场景包ID
     * @param usageRecordId 使用记录幂等键
     * @return 结果
     */
    public AjaxResult changePoints(Long userId, String ruleCode, Integer changeAmount,
                                   Long scenePackId, String usageRecordId);
    
    /**
     * 查询用户积分余额
     * 
     * @param userId 用户ID
     * @return 积分余额
     */
    public Integer getUserPoints(Long userId);
    
    /**
     * 查询积分明细记录
     * 
     * @param pointsRecord 积分记录查询条件
     * @return 积分记录列表
     */
    public List<PointsRecord> getPointsRecord(PointsRecord pointsRecord);
    
    /**
     * 获取积分概览
     * 
     * @param userId 用户ID
     * @return 积分概览信息
     */
    public Map<String, Object> getPointsSummary(Long userId);
    
    /**
     * 获取积分任务清单
     * 
     * @param userId 用户ID（可选，用于判断任务完成状态）
     * @return 积分任务列表
     */
    public List<Map<String, Object>> getPointsTaskList(Long userId);
    
    /**
     * 管理员给用户发放积分
     * 
     * @param userId 用户ID
     * @param pointsAmount 积分数量
     * @param remark 备注
     * @return 结果
     */
    public AjaxResult grantPointsByAdmin(Long userId, Integer pointsAmount, String remark);
    
    /**
     * 获取粉丝列表
     * 
     * @param user 用户查询条件
     * @return 粉丝列表
     */
    public List<SysUser> getPointsFansList(SysUser user);
}

