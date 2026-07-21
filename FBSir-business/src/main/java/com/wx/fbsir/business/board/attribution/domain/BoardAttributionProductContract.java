package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

@Data
public class BoardAttributionProductContract {
    private String contractId;
    private String productId;
    private String productVersion;
    private String hostType;
    private String connectorType;
    private String entrySurface;
    private String packageId;
    private String expertEntryId;
    private String registrationStatus;
    private boolean candidateEnabled;
    private boolean publicRouteEnabled;
    private boolean authoritativeCreditEnabled;
}
