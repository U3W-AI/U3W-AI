package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrchestrationRunMapper {
    int insertRun(OrchestrationRun run);
    OrchestrationRun selectByRunId(@Param("runId") String runId);
    OrchestrationRun selectByRunIdForUpdate(@Param("runId") String runId);
    int markReady(@Param("runId") String runId,
                  @Param("expectedVersion") Integer expectedVersion);
    int markWebhookQueued(@Param("runId") String runId,
                          @Param("expectedVersion") Integer expectedVersion);
    int markWebhookResult(@Param("runId") String runId,
                          @Param("expectedVersion") Integer expectedVersion,
                          @Param("status") String status);
}
