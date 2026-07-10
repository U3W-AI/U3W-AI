package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.WecomInboundEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WecomInboundEventMapper {
    /**
     * Atomically claims (bot_binding_id,msg_id_hash). MySQL returns 1 for the winner
     * and 2 for a duplicate because duplicate_count is updated.
     */
    int claimInboundEvent(WecomInboundEvent event);

    WecomInboundEvent selectById(@Param("id") Long id);
}
