package com.wx.fbsir.business.board.attribution.domain;

import lombok.Data;

/** One six-dimensional and traffic-class aggregate in a bounded window. */
@Data
public class BoardAttributionSummaryRow {
    private String productId;
    private String listedManifestVersion;
    private String channel;
    private String terminal;
    private String intentFamily;
    private String reviewMode;
    private String trafficClass;
    private long entryCount;
    private long classifiedCount;
    private long firstValueCount;
    private long distinctBindingCount;
    private long unknownDebtCount;
    private long eventHighWatermark;
}
