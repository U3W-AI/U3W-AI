package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

import java.util.Date;

@Data
public class BoardHostForwardingChallenge {
    private String challengeId;
    private String contractId;
    private String serverBindingId;
    private String nonceHash;
    private Date issuedAt;
    private Date expiresAt;
    private Date retentionUntil;
    private String status;
}
