package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

import java.util.Date;

/** Mutable sequence head for one independently derived WorkBuddy journey. */
@Data
public class BoardAttributionJourneyHead {
    private String sameBindingKey;
    private String contractId;
    private String tenantSubjectDigest;
    private String serverBindingId;
    private String journeyId;
    private String productId;
    private String listedManifestVersion;
    private String embeddedContractVersion;
    private String channel;
    private String terminal;
    private String hostVersion;
    private String trafficClass;
    private String intentFamily;
    private long lastSequenceNo;
    private String lastEventDigest;
    private long headVersion;
    private Date createdAt;
    private Date updatedAt;
}
