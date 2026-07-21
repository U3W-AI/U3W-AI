package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

import java.util.Date;

@Data
public class BoardAttributionSnapshot {
    private String snapshotId;
    private String contractId;
    private Date windowStart;
    private Date windowEnd;
    private Date retentionUntil;
    private Date watermarkAt;
    private long eventHighWatermark;
    private long rowCount;
    private long parseErrorCount;
    private long gapCount;
    private long invalidCount;
    private String canonicalizationVersion;
    private String eventDigest;
    private String runtimeRelease;
    private String embeddedRelease;
    private String signerKeyId;
    private String issuer;
    private String audience;
    private String snapshotSignature;
    private String status;
}
