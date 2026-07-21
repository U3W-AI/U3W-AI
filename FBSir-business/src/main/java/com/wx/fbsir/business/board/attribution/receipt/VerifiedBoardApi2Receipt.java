package com.wx.fbsir.business.board.attribution.receipt;

import java.time.Instant;

/** Marker that can only be constructed by a verifier in this package. */
public final class VerifiedBoardApi2Receipt {
    private final BoardApi2HostForwardingAckV2 rawAck;
    private final String tenantSubjectDigest;
    private final String nonceHash;
    private final String signatureHex;
    private final String canonicalDigest;
    private final String productSignatureDigest;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final String coveredEventId;

    VerifiedBoardApi2Receipt(
            BoardApi2HostForwardingAckV2 rawAck,
            String tenantSubjectDigest,
            String nonceHash,
            String signatureHex,
            String canonicalDigest,
            String productSignatureDigest,
            Instant issuedAt,
            Instant expiresAt,
            String coveredEventId) {
        this.rawAck = rawAck;
        this.tenantSubjectDigest = tenantSubjectDigest;
        this.nonceHash = nonceHash;
        this.signatureHex = signatureHex;
        this.canonicalDigest = canonicalDigest;
        this.productSignatureDigest = productSignatureDigest;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.coveredEventId = coveredEventId;
    }

    public BoardApi2HostForwardingAckV2 rawAck() { return rawAck; }
    public String ackId() { return rawAck.ackId(); }
    public String ackStage() { return rawAck.ackStage(); }
    public String challengeId() { return rawAck.challengeId(); }
    public String serverBindingId() { return rawAck.serverBindingId(); }
    public String tenantSubjectDigest() { return tenantSubjectDigest; }
    public String keyId() { return rawAck.keyId(); }
    public String nonceHash() { return nonceHash; }
    public String signatureHex() { return signatureHex; }
    public String canonicalDigest() { return canonicalDigest; }
    public String productSignatureDigest() { return productSignatureDigest; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public String coveredEventId() { return coveredEventId; }
}
