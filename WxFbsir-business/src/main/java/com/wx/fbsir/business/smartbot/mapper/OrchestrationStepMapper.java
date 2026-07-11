package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrchestrationStepMapper {
    int insertStep(OrchestrationStep step);
    OrchestrationStep selectByRunStepAttemptForUpdate(@Param("runId") String runId,
                                                      @Param("stepKey") String stepKey,
                                                      @Param("attempt") Integer attempt);
}
