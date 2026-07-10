package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Transactional outbox row for dispatcher and later response_url delivery. */
@Data
public class DeliveryOutbox {
    private Long id;
    private String eventKey;
    private String runId;
    private String eventType;
    private String destinationType;
    private String destinationRef;
    private String payloadJson;
    private String status;
    private Integer attemptCount;
    private Date nextAttemptAt;
    private String leaseOwner;
    private Date leaseUntil;
    private Integer lastHttpStatus;
    private String lastError;
    private Date createTime;
    private Date updateTime;
}
