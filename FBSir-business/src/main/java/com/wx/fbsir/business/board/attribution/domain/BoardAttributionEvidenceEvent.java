package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

import java.util.Date;

@Data
public class BoardAttributionEvidenceEvent {
    private String eventId;
    private String receiptId;
    private String challengeId;
    private String contractId;
    private String serverBindingId;
    private String tenantSubjectDigest;
    private String stage;
    private String outcome;
    private String entrySurface;
    private String channelTrack;
    private Date observedAt;
    private long sequenceNo;
    private int sampleCount;
    private String canonicalDigest;
    private String signerKeyId;
    private String issuer;
    private String audience;
    private String receiptNonceHash;
    private String receiptSignature;
    private Date issuedAt;
    private Date expiresAt;
    private long eventWatermark;
}
