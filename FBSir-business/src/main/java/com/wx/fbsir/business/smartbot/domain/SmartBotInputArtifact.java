package com.wx.fbsir.business.smartbot.domain;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * Encrypted, tenant-bound content artifact. Ciphertext and key metadata are
 * intentionally excluded from diagnostic string output.
 */
@Getter
@Setter
public class SmartBotInputArtifact {
    private Long id;
    private String inputRef;
    private String purpose;
    private Long inboundEventId;
    private String runId;
    private Long botBindingId;
    private Long enterpriseId;
    private Long enterpriseMemberId;
    private Long userId;
    private String msgType;
    private String sourcePayloadHash;
    private String contentHash;
    private String cipherAlgorithm;
    private String keyRef;
    private Integer keyVersion;
    private byte[] nonce;
    private byte[] ciphertext;
    private String aadHash;
    private Integer plaintextSize;
    private String status;
    private Date expiresAt;
    private Date createTime;
    private Date updateTime;

    @Override
    public String toString() {
        return "SmartBotInputArtifact[id=" + id + ", status=" + status + "]";
    }
}
