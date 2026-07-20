package com.wx.fbsir.business.board.dto;

import java.util.Date;
import java.util.List;

public record BoardConnectorProtectedRequestAttestation(
        Long tenantId,
        Long memberId,
        Long userId,
        String issuerUri,
        String resourceUri,
        String clientId,
        String principalSubjectDigest,
        List<String> scopes,
        String verificationMethod,
        String evidenceDigest,
        Date validUntil) {

    public BoardConnectorProtectedRequestAttestation {
        scopes = scopes == null ? null : List.copyOf(scopes);
        validUntil = copy(validUntil);
    }

    @Override
    public Date validUntil() {
        return copy(validUntil);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
