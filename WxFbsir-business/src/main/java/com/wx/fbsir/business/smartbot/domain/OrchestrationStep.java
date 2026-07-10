package com.wx.fbsir.business.smartbot.domain;

import lombok.Data;

import java.util.Date;

/** Versioned step/attempt record; executors never own run truth. */
@Data
public class OrchestrationStep {
    private String stepId;
    private String runId;
    private String stepKey;
    private Integer attempt;
    private String kind;
    private String executorType;
    private String executorRef;
    private String status;
    private String inputHash;
    private String outputRef;
    private String evidenceRef;
    private Integer version;
    private String leaseOwner;
    private Date leaseUntil;
    private String errorCode;
    private String errorMessage;
    private Date startedAt;
    private Date finishedAt;
    private Date createTime;
    private Date updateTime;
}
