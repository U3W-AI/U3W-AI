package com.wx.fbsir.business.smartbot.mapper;

import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeliveryOutboxMapper {
    int insertOutbox(DeliveryOutbox outbox);
}
