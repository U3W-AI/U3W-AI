package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.WecomBotBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WecomBotBindingMapper {
    WecomBotBinding selectActiveByCallbackKey(@Param("callbackKey") String callbackKey);
    WecomBotBinding selectActiveById(@Param("id") Long id);
}
