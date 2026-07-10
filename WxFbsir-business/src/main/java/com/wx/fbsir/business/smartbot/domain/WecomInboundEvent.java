package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Minimal, privacy-preserving ledger row for one inbound provider event. */
@Data
public class WecomInboundEvent {
    private Long id;
    private Long botBindingId;
    private String msgIdHash;
    private String aibotId;
    private String traceId;
    private String runId;
    private String streamId;
    private String fromUserHash;
    private String chatType;
    private String chatIdHash;
    private String msgType;
    private String eventType;
    private String payloadHash;
    private String status;
    private Integer duplicateCount;
    private Date firstReceivedAt;
    private Date lastReceivedAt;
}
