package com.wx.fbsir.business.board.attribution.receipt;

import lombok.Data;

/**
 * Lossless wire envelope for the Wave 1 API2 to U3W attribution event.
 *
 * All fields are finite identifiers, enums, timestamps or digests. Raw prompt,
 * conversation and enterprise content have no field in this contract.
 */
@Data
public class BoardAttributionEventV1 {
    private String schemaVersion;
    private String eventId;
    private String receiptId;
    private String contractId;
    private String eventType;
    private long sequenceNo;
    private String occurredAt;
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
    private String classificationSource;
    private String classifierVersion;
    private String confidenceBucket;
    private String reviewMode;
    private String journeyId;
    private String serverBindingId;
    private String sameBindingKey;
    private String tenantSubjectDigest;
    private String trafficClass;
    private String trafficAuthority;
    private String outcome;
    private String previousEventDigest;
    private String traceparent;
    private boolean rawContentStored;
    private String issuedAt;
    private String expiresAt;
    private String nonce;
    private String keyId;
    private String signatureAlgorithm;
    private String signature;
}
