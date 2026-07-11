package com.wx.fbsir.business.airobotmessage.mapper;

import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    List<WecomWebhook> selectWecomWebhookList(@Param("enterpriseId") Long enterpriseId);

    WecomWebhook selectWecomWebhookById(@Param("id") Long id,
                                        @Param("enterpriseId") Long enterpriseId);

    int insertWecomWebhook(WecomWebhook wecomWebhook);

    int updateWecomWebhook(WecomWebhook wecomWebhook);

    int deleteWecomWebhookById(@Param("id") Long id,
                               @Param("enterpriseId") Long enterpriseId,
                               @Param("version") Integer version);
}
