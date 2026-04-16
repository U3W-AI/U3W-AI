package com.wx.fbsir.business.point.mapper;

import java.util.List;
import com.wx.fbsir.business.point.domain.PointsRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 积分明细记录Mapper接口
 * 
 * @author wxfbsir
 * @date 2025-12-08
 */
@Mapper
public interface PointsRecordMapper {
    /**
     * 查询积分明细记录
     * 
     * @param recordId 积分明细记录ID
     * @return 积分明细记录
     */
    public PointsRecord selectPointsRecordByRecordId(Long recordId);

    /**
     * 查询积分明细记录列表
     * 
     * @param pointsRecord 积分明细记录
     * @return 积分明细记录集合
     */
    public List<PointsRecord> selectPointsRecordList(PointsRecord pointsRecord);

    /**
     * 根据用户ID查询积分明细记录列表
     * 
     * @param userId 用户ID
     * @return 积分明细记录集合
     */
    public List<PointsRecord> selectPointsRecordListByUserId(Long userId);

    /**
     * 检查用户是否完成过某个任务（查询积分记录表）
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码
     * @return 完成次数
     */
    public int checkUserTaskCompleted(@Param("userId") Long userId, @Param("ruleCode") String ruleCode);

    /**
     * 统计从指定时间开始的积分获取总额（仅统计正向积分）
     * 
     * @param userId 用户ID
     * @param startTime 开始时间
     * @return 积分总额
     */
    public Integer sumUserPointsChangeSince(@Param("userId") Long userId, @Param("startTime") java.time.LocalDateTime startTime);

    /**
     * 获取最近一次积分变动记录
     * 
     * @param userId 用户ID
     * @return 积分记录
     */
    public PointsRecord getLastPointChange(Long userId);

    /**
     * 根据事件ID查询积分记录（幂等检查）
     * 
     * @param eventId 事件幂等键
     * @return 积分记录
     */
    public PointsRecord selectByEventId(@Param("eventId") String eventId);

    /**
     * 统计某用户在指定时间之后针对某规则的正向积分次数
     * 
     * @param userId 用户ID
     * @param ruleCode 规则编码
     * @param startTime 开始时间（包含）
     * @return 次数
     */
    public int countUserRuleSince(@Param("userId") Long userId, @Param("ruleCode") String ruleCode,
                                  @Param("startTime") java.time.LocalDateTime startTime);

    /**
     * 新增积分明细记录
     * 
     * @param pointsRecord 积分明细记录
     * @return 结果
     */
    public int insertPointsRecord(PointsRecord pointsRecord);

    /**
     * 修改积分明细记录
     * 
     * @param pointsRecord 积分明细记录
     * @return 结果
     */
    public int updatePointsRecord(PointsRecord pointsRecord);

    /**
     * 删除积分明细记录
     * 
     * @param recordId 积分明细记录ID
     * @return 结果
     */
    public int deletePointsRecordByRecordId(Long recordId);

    /**
     * 批量删除积分明细记录
     * 
     * @param recordIds 需要删除的数据ID
     * @return 结果
     */
    public int deletePointsRecordByRecordIds(Long[] recordIds);
}

