package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.SmartBotInputArtifact;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SmartBotInputArtifactMapper {
    int insertArtifact(SmartBotInputArtifact artifact);

    SmartBotInputArtifact selectByInboundEventIdForUpdate(@Param("inboundEventId") Long inboundEventId);

    SmartBotInputArtifact selectByRunIdForUpdate(@Param("runId") String runId);
}
