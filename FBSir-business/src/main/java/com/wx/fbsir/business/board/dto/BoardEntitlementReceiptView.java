package com.wx.fbsir.business.board.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;

/** Safe receipt projection; the internal row id and payload digest never cross the API. */
public record BoardEntitlementReceiptView(
        String receiptId,
        Long tenantId,
        Long actorUserId,
        Long targetMemberId,
        String action,
        String evidenceLevel,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Shanghai")
        Date createdAt) {
}
