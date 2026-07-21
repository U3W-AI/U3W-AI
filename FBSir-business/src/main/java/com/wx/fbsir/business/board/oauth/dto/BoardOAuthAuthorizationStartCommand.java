package com.wx.fbsir.business.board.oauth.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Internal, transport-neutral input for starting an authorization request. */
public final class BoardOAuthAuthorizationStartCommand {
    private final String responseType;
    private final String clientId;
    private final String redirectUri;
    private final String codeChallenge;
    private final String codeChallengeMethod;
    private final String state;
    private final String resourceUri;
    private final List<String> scopes;

    public BoardOAuthAuthorizationStartCommand(
            String responseType,
            String clientId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            String state,
            String resourceUri,
            List<String> scopes) {
        this.responseType = responseType;
        this.clientId = clientId;
        this.redirectUri = redirectUri;
        this.codeChallenge = codeChallenge;
        this.codeChallengeMethod = codeChallengeMethod;
        this.state = state;
        this.resourceUri = resourceUri;
        this.scopes = scopes == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(scopes));
    }

    public String responseType() {
        return responseType;
    }

    public String clientId() {
        return clientId;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public String codeChallenge() {
        return codeChallenge;
    }

    public String codeChallengeMethod() {
        return codeChallengeMethod;
    }

    public String state() {
        return state;
    }

    public String resourceUri() {
        return resourceUri;
    }

    public List<String> scopes() {
        return scopes;
    }

    @Override
    public String toString() {
        return "BoardOAuthAuthorizationStartCommand[REDACTED]";
    }
}
