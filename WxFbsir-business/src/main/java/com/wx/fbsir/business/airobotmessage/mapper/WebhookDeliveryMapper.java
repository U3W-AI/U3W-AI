package com.wx.fbsir.business.airobotmessage.mapper;

import com.wx.fbsir.business.airobotmessage.domain.WebhookDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WebhookDeliveryMapper {
    WebhookDelivery selectByIdempotencyKey(@Param("enterpriseId") Long enterpriseId,
                                           @Param("idempotencyKey") String idempotencyKey);
    int insert(WebhookDelivery delivery);
    int updateResult(WebhookDelivery delivery);
    int markStalePendingUnknown(@Param("enterpriseId") Long enterpriseId,
                                @Param("idempotencyKey") String idempotencyKey);
    int markAllStalePendingUnknown();
}
