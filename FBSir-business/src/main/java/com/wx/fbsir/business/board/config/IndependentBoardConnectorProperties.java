package com.wx.fbsir.business.board.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fbsir.independent-board.connector")
public class IndependentBoardConnectorProperties {
    private String issuerUri = "";
    private String resourceUri = "https://api2.u3w.com/fbs-mcp/mcp";

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getResourceUri() {
        return resourceUri;
    }

    public void setResourceUri(String resourceUri) {
        this.resourceUri = resourceUri;
    }
}
