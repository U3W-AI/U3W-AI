package com.wx.fbsir.business.board.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Internal OAuth cryptographic configuration. Empty defaults keep authorization
 * request creation fail closed until an operator provides a repository-external key.
 */
@Component
@ConfigurationProperties(prefix = "fbsir.independent-board.oauth")
public class IndependentBoardOAuthProperties {
    private String stateKeyRef = "";
    private String stateKeyBase64 = "";

    public String getStateKeyRef() {
        return stateKeyRef;
    }

    public void setStateKeyRef(String stateKeyRef) {
        this.stateKeyRef = stateKeyRef;
    }

    public String getStateKeyBase64() {
        return stateKeyBase64;
    }

    public void setStateKeyBase64(String stateKeyBase64) {
        this.stateKeyBase64 = stateKeyBase64;
    }
}
