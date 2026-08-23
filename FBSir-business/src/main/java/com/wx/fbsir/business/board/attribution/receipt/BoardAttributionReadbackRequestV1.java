package com.wx.fbsir.business.board.attribution.receipt;

import lombok.Data;

/** Minimal signed lookup tuple; it carries no tenant, binding or raw payload. */
@Data
public class BoardAttributionReadbackRequestV1 {
    private String schemaVersion;
    private String eventId;
    private String receiptId;
    private String eventDigest;
    private String issuedAt;
    private String expiresAt;
    private String nonce;
    private String keyId;
    private String signature;
}
