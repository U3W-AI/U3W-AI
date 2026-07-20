package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthClient;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthReceipt;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationRequest;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationResponse;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal registration service for the single locked WorkBuddy-compatible
 * public-client profile.
 *
 * <p>This service does not expose an HTTP route or prove the caller is a
 * WorkBuddy host. Raw registration metadata and source context are used only
 * long enough to calculate SHA-256 digests.</p>
 */
@Service
public class IndependentBoardOAuthClientRegistrationService {
    public static final String NEUTRAL_CLIENT_NAME = "未验证的本地公共客户端";
    public static final String TOKEN_ENDPOINT_AUTH_METHOD = "none";
    public static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    public static final String SOURCE_CODE = "WORKBUDDY";
    public static final String CONNECTOR_CODE = "fbs-connector";
    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String INVALID_CLIENT_METADATA = "OAUTH_DCR_INVALID_CLIENT_METADATA";
    public static final String INVALID_REDIRECT_URI = "OAUTH_DCR_INVALID_REDIRECT_URI";
    public static final String REGISTRATION_CONTEXT_INVALID =
            "OAUTH_DCR_REGISTRATION_CONTEXT_INVALID";
    public static final String RANDOM_GENERATION_FAILED = "OAUTH_DCR_RANDOM_GENERATION_FAILED";
    public static final String CLIENT_CONFLICT = "OAUTH_DCR_CLIENT_CONFLICT";
    public static final String CLIENT_WRITE_FAILED = "OAUTH_DCR_CLIENT_WRITE_FAILED";
    public static final String RECEIPT_WRITE_FAILED = "OAUTH_DCR_RECEIPT_WRITE_FAILED";

    private static final List<String> GRANT_TYPES =
            List.of("authorization_code", "refresh_token");
    private static final Set<String> GRANT_TYPE_SET = Set.copyOf(GRANT_TYPES);
    private static final List<String> RESPONSE_TYPES = List.of("code");
    private static final Set<String> RESPONSE_TYPE_SET = Set.copyOf(RESPONSE_TYPES);
    private static final String GRANT_TYPES_CANONICAL = String.join(" ", GRANT_TYPES);
    private static final String RESPONSE_TYPES_CANONICAL = String.join(" ", RESPONSE_TYPES);
    private static final Duration CLIENT_LIFETIME = Duration.ofDays(31);
    private static final int MAX_METADATA_BYTES = 65_536;
    private static final int MAX_REGISTRATION_SOURCE_BYTES = 1_024;
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final IndependentBoardOAuthMapper mapper;
    private final Clock clock;
    private final SecureRandom secureRandom;

    @Autowired
    public IndependentBoardOAuthClientRegistrationService(IndependentBoardOAuthMapper mapper) {
        this(mapper, Clock.systemUTC(), new SecureRandom());
    }

    IndependentBoardOAuthClientRegistrationService(
            IndependentBoardOAuthMapper mapper,
            Clock clock,
            SecureRandom secureRandom) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.secureRandom = Objects.requireNonNull(secureRandom, "secureRandom");
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardOAuthClientRegistrationResponse register(
            BoardOAuthClientRegistrationRequest request) {
        validateKnownMetadata(request);

        String redirectUri = request.redirectUris().get(0);
        int redirectPort = BoardOAuthProfile.requireLoopbackPort(redirectUri);
        byte[] metadataDocument = request.metadataDocument();
        byte[] registrationSource = request.registrationSource();
        validateEvidence(metadataDocument, registrationSource);

        Instant registeredAt = clock.instant();
        Instant expiresAt = registeredAt.plus(CLIENT_LIFETIME);
        String clientId = generateOpaqueId();
        String receiptId = "oauth_" + generateOpaqueId();

        BoardOAuthClient client = new BoardOAuthClient();
        client.setClientId(clientId);
        client.setClientName(NEUTRAL_CLIENT_NAME);
        client.setIssuerUri(BoardOAuthProfile.ISSUER);
        client.setResourceUri(BoardOAuthProfile.RESOURCE);
        client.setProductCode(PRODUCT_CODE);
        client.setSourceCode(SOURCE_CODE);
        client.setConnectorCode(CONNECTOR_CODE);
        client.setRedirectPort(redirectPort);
        client.setRedirectUri(redirectUri);
        client.setTokenEndpointAuthMethod(TOKEN_ENDPOINT_AUTH_METHOD);
        client.setGrantTypesCanonical(GRANT_TYPES_CANONICAL);
        client.setResponseTypesCanonical(RESPONSE_TYPES_CANONICAL);
        client.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        client.setScopeDigest(BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE));
        client.setMetadataDigest(BoardOAuthCrypto.sha256(metadataDocument));
        client.setRegistrationSourceDigest(BoardOAuthCrypto.sha256(registrationSource));
        client.setStatus(STATUS_ACTIVE);
        client.setRegisteredAt(Date.from(registeredAt));
        client.setExpiresAt(Date.from(expiresAt));
        client.setTerminatedAt(null);
        client.setVersion(0L);

        int inserted;
        try {
            inserted = mapper.insertClient(client);
        } catch (DuplicateKeyException conflict) {
            throw new ServiceException(CLIENT_CONFLICT, 409);
        } catch (DataAccessException failure) {
            throw new ServiceException(CLIENT_WRITE_FAILED, 500);
        }
        if (inserted != 1) {
            throw new ServiceException(CLIENT_WRITE_FAILED, 500);
        }

        BoardOAuthReceipt receipt = registrationReceipt(
                client, receiptId, registeredAt, expiresAt);
        try {
            inserted = mapper.insertReceipt(receipt);
        } catch (DataAccessException failure) {
            throw new ServiceException(RECEIPT_WRITE_FAILED, 500);
        }
        if (inserted != 1) {
            throw new ServiceException(RECEIPT_WRITE_FAILED, 500);
        }

        return new BoardOAuthClientRegistrationResponse(
                clientId,
                registeredAt.getEpochSecond(),
                expiresAt.getEpochSecond(),
                List.of(redirectUri),
                GRANT_TYPES,
                RESPONSE_TYPES,
                TOKEN_ENDPOINT_AUTH_METHOD,
                NEUTRAL_CLIENT_NAME,
                BoardOAuthProfile.CANONICAL_SCOPE);
    }

    private void validateKnownMetadata(BoardOAuthClientRegistrationRequest request) {
        if (request == null) {
            throw new ServiceException(INVALID_CLIENT_METADATA, 400);
        }
        if (!Objects.equals(TOKEN_ENDPOINT_AUTH_METHOD, request.tokenEndpointAuthMethod())
                || !isExactSet(request.grantTypes(), GRANT_TYPE_SET)
                || !isExactSet(request.responseTypes(), RESPONSE_TYPE_SET)
                || !BoardOAuthProfile.hasExactScopeSet(request.scopes())) {
            throw new ServiceException(INVALID_CLIENT_METADATA, 400);
        }
        List<String> redirectUris = request.redirectUris();
        if (redirectUris == null
                || redirectUris.size() != 1
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(redirectUris.get(0))) {
            throw new ServiceException(INVALID_REDIRECT_URI, 400);
        }
    }

    private void validateEvidence(byte[] metadataDocument, byte[] registrationSource) {
        if (metadataDocument == null
                || metadataDocument.length == 0
                || metadataDocument.length > MAX_METADATA_BYTES) {
            throw new ServiceException(INVALID_CLIENT_METADATA, 400);
        }
        if (registrationSource == null
                || registrationSource.length == 0
                || registrationSource.length > MAX_REGISTRATION_SOURCE_BYTES) {
            throw new ServiceException(REGISTRATION_CONTEXT_INVALID, 500);
        }
    }

    private BoardOAuthReceipt registrationReceipt(
            BoardOAuthClient client,
            String receiptId,
            Instant registeredAt,
            Instant expiresAt) {
        BoardOAuthReceipt receipt = new BoardOAuthReceipt();
        receipt.setReceiptId(receiptId);
        receipt.setAction("OAUTH_CLIENT_REGISTERED");
        receipt.setClientId(client.getClientId());
        receipt.setActorType("CLIENT");
        receipt.setActorUserId(null);
        receipt.setActorSubjectDigest(client.getRegistrationSourceDigest());
        receipt.setCorrelationId(receiptId);
        String canonicalPayload = String.join("|",
                "OAUTH_CLIENT_REGISTERED",
                client.getClientId(),
                client.getIssuerUri(),
                client.getResourceUri(),
                client.getRedirectUri(),
                HexFormat.of().formatHex(client.getScopeDigest()),
                HexFormat.of().formatHex(client.getMetadataDigest()),
                HexFormat.of().formatHex(client.getRegistrationSourceDigest()),
                Long.toString(registeredAt.toEpochMilli()),
                Long.toString(expiresAt.toEpochMilli()));
        receipt.setPayloadDigest(BoardOAuthCrypto.sha256Ascii(canonicalPayload));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(Date.from(registeredAt));
        return receipt;
    }

    private String generateOpaqueId() {
        byte[] randomBytes = new byte[BoardOAuthCrypto.SECRET_BYTES];
        try {
            secureRandom.nextBytes(randomBytes);
        } catch (RuntimeException failure) {
            throw new ServiceException(RANDOM_GENERATION_FAILED, 500);
        }
        return BASE64_URL.encodeToString(randomBytes);
    }

    private static boolean isExactSet(List<String> candidate, Set<String> required) {
        if (candidate == null || candidate.size() != required.size()) {
            return false;
        }
        Set<String> values = new HashSet<>();
        for (String value : candidate) {
            if (value == null || !values.add(value)) {
                return false;
            }
        }
        return values.equals(required);
    }
}
