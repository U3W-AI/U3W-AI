package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Skill使用记录Mapper接口
 *
 * @author FBSir
 * @date 2026-04-08
 */
@Mapper
public interface FbsSkillUsageRecordMapper {

    /**
     * 根据使用记录幂等键查询
     *
     * @param usageRecordId 使用记录幂等键
     * @return 使用记录（NULL=不存在）
     */
    FbsSkillUsageRecord selectByRecordId(@Param("usageRecordId") String usageRecordId);

    /**
     * 新增使用记录
     *
     * @param record 使用记录
     * @return 影响行数
     */
    int insertUsageRecord(FbsSkillUsageRecord record);

    /**
     * 更新使用记录状态（成功/失败），同时更新 end_time 和 duration_seconds
     *
     * @param usageRecordId   使用记录幂等键
     * @param status          新状态（1=成功, 2=失败）
     * @param errorMessage    失败原因（成功时传NULL）
     * @return 影响行数
     */
    int updateStatusByRecordId(@Param("usageRecordId") String usageRecordId,
                               @Param("status") Integer status,
                               @Param("errorMessage") String errorMessage);
}
