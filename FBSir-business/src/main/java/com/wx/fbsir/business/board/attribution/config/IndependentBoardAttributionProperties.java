package com.wx.fbsir.business.board.attribution.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** W4b.2d internal evidence writer settings. Public routes are intentionally absent. */
@Data
@Component
@ConfigurationProperties(prefix = "fbsir.independent-board.attribution")
public class IndependentBoardAttributionProperties {
    private boolean enabled = false;
    private boolean candidateEnabled = false;
    private boolean publicRouteEnabled = false;
    private boolean authoritativeCreditEnabled = false;
    /** Wave 1 append-only observation writer. */
    private boolean observationWriterEnabled = false;
    /** Finite server-side intent classifier. */
    private boolean intentClassifierEnabled = false;
    /** Remains off until natural-traffic evidence is independently approved. */
    private boolean productCreditEnabled = false;
    private String issuer = "api2.u3w.com";
    private String audience = "independent-board-attribution";
    private String keyRef = "fbs.w4b2d.api2.keyring";
    private int receiptTtlSeconds = 120;
    private int retentionHours = 26;
}
