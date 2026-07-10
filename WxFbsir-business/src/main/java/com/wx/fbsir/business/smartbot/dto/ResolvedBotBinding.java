package com.wx.fbsir.business.smartbot.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Decrypted credentials are transient and must never be logged or persisted. */
public record ResolvedBotBinding(
        Long bindingId,
        @JsonIgnore String callbackKey,
        String aibotId,
        Long enterpriseId,
        String mode,
        int credentialVersion,
        @JsonIgnore String token,
        @JsonIgnore String encodingAesKey) {

    @Override
    public String toString() {
        return "ResolvedBotBinding[bindingId=" + bindingId
            + ", callbackKey=***"
            + ", aibotId=" + aibotId
            + ", enterpriseId=" + enterpriseId
            + ", mode=" + mode
            + ", credentialVersion=" + credentialVersion
            + ", token=***, encodingAesKey=***]";
    }
}
