package com.wx.fbsir.business.fbs.mapper;

import com.wx.fbsir.business.fbs.domain.entity.FbsWecomSyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 企微同步日志 Mapper 接口
 *
 * @author FBSir
 * @date 2026-04-13
 */
@Mapper
public interface FbsWecomSyncLogMapper {

    /**
     * 新增同步日志
     *
     * @param log 同步日志
     * @return 影响行数
     */
    int insertSyncLog(@Param("log") FbsWecomSyncLog log);
}
