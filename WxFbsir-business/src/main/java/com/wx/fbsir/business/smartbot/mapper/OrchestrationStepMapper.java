package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrchestrationStepMapper {
    int insertStep(OrchestrationStep step);
}
