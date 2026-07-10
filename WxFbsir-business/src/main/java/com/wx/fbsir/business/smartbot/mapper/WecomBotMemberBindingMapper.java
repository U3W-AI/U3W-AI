package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.WecomBotMemberBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WecomBotMemberBindingMapper {
    WecomBotMemberBinding selectActiveByExternalHash(@Param("botBindingId") Long botBindingId,
                                                     @Param("externalUserHash") String externalUserHash);
}
