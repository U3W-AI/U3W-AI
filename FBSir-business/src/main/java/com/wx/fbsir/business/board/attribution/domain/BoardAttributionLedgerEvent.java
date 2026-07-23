package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

import java.util.Date;

/** Immutable, content-free six-dimensional attribution ledger row. */
@Data
public class BoardAttributionLedgerEvent {
    private String eventId;
    private String receiptId;
    private String sameBindingKey;
    private String contractId;
    private String journeyId;
    private String serverBindingId;
    private String tenantSubjectDigest;
    private String eventType;
    private long sequenceNo;
    private Date occurredAt;
    private String productId;
    private String packageId;
    private String agentName;
    private String marketplace;
    private String listedSurface;
    private String listedManifestVersion;
    private String embeddedContractVersion;
    private String hostClientFamily;
    private String hostVersion;
    private String terminal;
    private String channel;
    private String requestSource;
    private String intentSignal;
    private String intentFamily;
    private String classificationSource;
    private String classifierVersion;
    private String confidenceBucket;
    private String reviewMode;
    private String trafficClass;
    private String trafficAuthority;
    private String outcome;
    private String previousEventDigest;
    private String traceparent;
    private String canonicalDigest;
    private String eventDigest;
    private String signerKeyId;
    private String signatureHex;
    private String nonceHash;
    private Date issuedAt;
    private Date expiresAt;
    private boolean productCreditEligible;
    private long eventWatermark;
    private Date createdAt;
}
