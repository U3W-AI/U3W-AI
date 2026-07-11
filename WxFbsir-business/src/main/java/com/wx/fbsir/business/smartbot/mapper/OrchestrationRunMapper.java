package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrchestrationRunMapper {
    int insertRun(OrchestrationRun run);
    OrchestrationRun selectByRunId(@Param("runId") String runId);
    OrchestrationRun selectByRunIdForUpdate(@Param("runId") String runId);
}
