package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Durable orchestration run; MySQL is the state source of truth. */
@Data
public class OrchestrationRun {
    private String runId;
    private Long inboundEventId;
    private Long botBindingId;
    private Long enterpriseId;
    private Long enterpriseMemberId;
    private Long userId;
    private String definitionCode;
    private Integer definitionVersion;
    private String traceId;
    private String streamId;
    private String status;
    private Integer version;
    private Date nextWakeupAt;
    private String leaseOwner;
    private Date leaseUntil;
    private String errorCode;
    private String errorMessage;
    private Date createTime;
    private Date updateTime;
}
