package com.wx.fbsir.business.board.oauth.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Internal, transport-neutral input for the locked profile-constrained DCR flow.
 *
 * <p>Unknown wire metadata is deliberately not modeled. The transport may pass
 * the original metadata bytes for audit digesting, but the registration service
 * never interprets or fetches any URL carried by those bytes.</p>
 */
public record BoardOAuthClientRegistrationRequest(
        List<String> redirectUris,
        String tokenEndpointAuthMethod,
        List<String> grantTypes,
        List<String> responseTypes,
        List<String> scopes,
        byte[] metadataDocument,
        byte[] registrationSource) {

    public BoardOAuthClientRegistrationRequest {
        redirectUris = immutableNullableCopy(redirectUris);
        grantTypes = immutableNullableCopy(grantTypes);
        responseTypes = immutableNullableCopy(responseTypes);
        scopes = immutableNullableCopy(scopes);
        metadataDocument = cloneNullable(metadataDocument);
        registrationSource = cloneNullable(registrationSource);
    }

    @Override
    public byte[] metadataDocument() {
        return cloneNullable(metadataDocument);
    }

    @Override
    public byte[] registrationSource() {
        return cloneNullable(registrationSource);
    }

    private static List<String> immutableNullableCopy(List<String> values) {
        return values == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static byte[] cloneNullable(byte[] value) {
        return value == null ? null : value.clone();
    }
}
