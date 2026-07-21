package com.wx.fbsir.business.board.portal;

import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthClientRegistrationService;
import com.wx.fbsir.business.board.portal.dto.BoardPortalConnectorBindingView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalConnectorView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthClientView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthFamilyView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalReadEnvelope;
import com.wx.fbsir.business.board.portal.mapper.IndependentBoardPortalReadMapper;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalConnectorBindingRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthClientRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthFamilyRow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class IndependentBoardPortalReadService {

    public static final int PAGE_LIMIT = 100;
    static final int ROW_LIMIT = PAGE_LIMIT + 1;

    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String SOURCE_CODE = "WORKBUDDY";
    private static final String CONNECTOR_CODE = "fbs-connector";
    private static final String CLIENT_PATH =
            "/business/independent-board/oauth/clients";
    private static final String FAMILY_PATH =
            "/business/independent-board/oauth/families";
    private static final String BINDING_PATH =
            "/business/independent-board/connector-bindings";
    private static final String GRANTS_CANONICAL =
            "authorization_code refresh_token";
    private static final String RESPONSES_CANONICAL = "code";
    private static final String TOKEN_AUTH_METHOD = "none";
    private static final Duration CLIENT_LIFETIME = Duration.ofDays(31);
    private static final Duration MAX_FAMILY_LIFETIME = Duration.ofDays(30);
    private static final long MAX_REFRESH_GENERATION = 0xffff_ffffL;
    private static final Pattern REFERENCE =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{3,191}");
    private static final Pattern HEX_DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> CLIENT_STATUSES =
            Set.of("ACTIVE", "REVOKED", "EXPIRED");
    private static final Set<String> FAMILY_STATUSES = Set.of(
            "PENDING_BINDING", "ACTIVE", "REVOKED", "COMPROMISED", "EXPIRED");
    private static final Set<String> BINDING_STATUSES =
            Set.of("ACTIVE", "REVOKED", "COMPROMISED");
    private static final Set<String> CONSENT_INTENTS =
            Set.of("FIRST_CONNECT", "EXPLICIT_REAUTHORIZATION");
    private static final Set<String> VERIFICATION_METHODS =
            Set.of("MCP_INITIALIZE", "MCP_TOOLS_LIST");

    private final IndependentBoardPortalReadMapper mapper;
    private final IndependentBoardMapper boardMapper;
    private final BoardPortalReadCursor cursorCodec;
    private final BoardPortalDigestRef digestRef;
    private final Clock clock;

    public IndependentBoardPortalReadService(
            IndependentBoardPortalReadMapper mapper,
            IndependentBoardMapper boardMapper,
            BoardPortalReadCursor cursorCodec,
            BoardPortalDigestRef digestRef,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.boardMapper = Objects.requireNonNull(boardMapper, "boardMapper");
        this.cursorCodec = Objects.requireNonNull(cursorCodec, "cursorCodec");
        this.digestRef = Objects.requireNonNull(digestRef, "digestRef");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public BoardPortalReadEnvelope<BoardPortalOAuthClientView> listOAuthClients(
            long principalId, String status, String cursor) {
        requirePositive(principalId, "INVALID_PRINCIPAL");
        String normalizedStatus = normalizeStatus(status, CLIENT_STATUSES);
        BoardPortalReadCursor.Context context = new BoardPortalReadCursor.Context(
                BoardPortalReadCursor.Kind.CLIENT,
                CLIENT_PATH,
                principalId,
                null,
                normalizedStatus);
        PagePosition position = decodePosition(context, cursor);
        Instant now = clock.instant();
        List<BoardPortalOAuthClientRow> rows = mapper.selectOAuthClients(
                mapperStatus(normalizedStatus),
                Date.from(now),
                position.highWaterId(),
                position.lastId(),
                ROW_LIMIT);
        return page(rows, position, context,
                row -> requireRowId(row == null ? null : row.getRowId()),
                row -> toClientView(row, now),
                BoardPortalOAuthClientView::clientRef);
    }

    public BoardPortalReadEnvelope<BoardPortalOAuthFamilyView> listOAuthFamilies(
            long principalId, long tenantId, String status, String cursor) {
        requirePositive(principalId, "INVALID_PRINCIPAL");
        requirePositive(tenantId, "INVALID_TENANT");
        String normalizedStatus = normalizeStatus(status, FAMILY_STATUSES);
        BoardPortalReadCursor.Context context = new BoardPortalReadCursor.Context(
                BoardPortalReadCursor.Kind.FAMILY,
                FAMILY_PATH,
                principalId,
                tenantId,
                normalizedStatus);
        PagePosition position = decodePosition(context, cursor);
        Instant now = clock.instant();
        List<BoardPortalOAuthFamilyRow> rows = mapper.selectOAuthFamilies(
                tenantId,
                null,
                null,
                mapperStatus(normalizedStatus),
                Date.from(now),
                position.highWaterId(),
                position.lastId(),
                ROW_LIMIT);
        return page(rows, position, context,
                row -> requireRowId(row == null ? null : row.getRowId()),
                row -> toFamilyView(row, tenantId, now),
                BoardPortalOAuthFamilyView::familyRef);
    }

    public BoardPortalReadEnvelope<BoardPortalConnectorBindingView> listConnectorBindings(
            long principalId, long tenantId, String status, String cursor) {
        requirePositive(principalId, "INVALID_PRINCIPAL");
        requirePositive(tenantId, "INVALID_TENANT");
        String normalizedStatus = normalizeStatus(status, BINDING_STATUSES);
        BoardPortalReadCursor.Context context = new BoardPortalReadCursor.Context(
                BoardPortalReadCursor.Kind.BINDING,
                BINDING_PATH,
                principalId,
                tenantId,
                normalizedStatus);
        PagePosition position = decodePosition(context, cursor);
        Instant now = clock.instant();
        List<BoardPortalConnectorBindingRow> rows = mapper.selectConnectorBindings(
                tenantId,
                null,
                null,
                mapperStatus(normalizedStatus),
                Date.from(now),
                position.highWaterId(),
                position.lastId(),
                ROW_LIMIT);
        return page(rows, position, context,
                row -> requireRowId(row == null ? null : row.getRowId()),
                row -> toBindingView(row, tenantId, now),
                BoardPortalConnectorBindingView::bindingRef);
    }

    public BoardPortalConnectorView getConnector(long principalId, long tenantId) {
        requirePositive(principalId, "INVALID_PRINCIPAL");
        requirePositive(tenantId, "INVALID_TENANT");
        BoardEnterpriseMemberScope context = boardMapper.selectActiveContext(
                tenantId, principalId);
        if (context == null) {
            throw new BoardPortalForbiddenException("TENANT_MEMBERSHIP_REQUIRED");
        }
        if (!Objects.equals(context.getTenantId(), tenantId)
                || !Objects.equals(context.getUserId(), principalId)
                || !isPositive(context.getMemberId())
                || !Objects.equals(context.getStatus(), 1)
                || !"0".equals(context.getDelFlag())) {
            throw drift("MEMBERSHIP_SCOPE_DRIFT");
        }

        long memberId = context.getMemberId();
        Instant now = clock.instant();
        List<BoardPortalOAuthFamilyRow> familyRows = mapper.selectOAuthFamilies(
                tenantId, memberId, principalId, null, Date.from(now),
                null, null, 3);
        List<BoardPortalConnectorBindingRow> bindingRows = mapper.selectConnectorBindings(
                tenantId, memberId, principalId, null, Date.from(now),
                null, null, 2);
        if (familyRows == null || familyRows.size() > 3
                || bindingRows == null || bindingRows.size() > 1) {
            return unknownConnector(tenantId, memberId);
        }

        try {
            List<FamilyCandidate> families = currentFamilies(
                    familyRows, tenantId, memberId, principalId, now);
            List<BoardPortalConnectorBindingView> bindings = currentBindings(
                    bindingRows, tenantId, memberId, principalId, now);
            List<FamilyCandidate> activeFamilies = families.stream()
                    .filter(candidate -> "ACTIVE".equals(candidate.view().status()))
                    .toList();
            List<FamilyCandidate> pendingFamilies = families.stream()
                    .filter(candidate -> "PENDING_BINDING".equals(candidate.view().status()))
                    .toList();
            if (activeFamilies.size() > 1 || pendingFamilies.size() > 1) {
                return unknownConnector(tenantId, memberId);
            }
            if (activeFamilies.size() == 1) {
                FamilyCandidate activeFamily = activeFamilies.get(0);
                BoardPortalConnectorBindingView activeBinding = bindings.stream()
                        .filter(BoardPortalConnectorBindingView::vipEffective)
                        .filter(binding -> Objects.equals(
                                binding.bindingRef(), activeFamily.view().bindingRef()))
                        .filter(binding -> Objects.equals(
                                binding.clientRef(), activeFamily.view().clientRef()))
                        .findFirst()
                        .orElse(null);
                return activeBinding == null
                        ? unknownConnector(tenantId, memberId)
                        : activeConnector(
                                tenantId, memberId, activeFamily.view(), activeBinding);
            }
            if (pendingFamilies.size() == 1) {
                FamilyCandidate pending = pendingFamilies.get(0);
                return Boolean.TRUE.equals(pending.row().getPendingActivationProven())
                        ? pendingConnector(tenantId, memberId, pending.view())
                        : reauthConnector(
                                tenantId, memberId, pending.view(), bindings);
            }
            if (families.isEmpty()) {
                return bindings.isEmpty()
                        ? notConnected(tenantId, memberId)
                        : unknownConnector(tenantId, memberId);
            }
            return reauthConnector(tenantId, memberId, families.get(0).view(), bindings);
        } catch (BoardPortalDataDriftException exception) {
            return unknownConnector(tenantId, memberId);
        }
    }

    private List<FamilyCandidate> currentFamilies(
            List<BoardPortalOAuthFamilyRow> rows,
            long tenantId,
            long memberId,
            long userId,
            Instant now) {
        List<FamilyCandidate> result = new ArrayList<>(rows.size());
        long previousId = Long.MAX_VALUE;
        for (BoardPortalOAuthFamilyRow row : rows) {
            long rowId = requireRowId(row == null ? null : row.getRowId());
            if (rowId >= previousId
                    || !Objects.equals(row.getMemberId(), memberId)
                    || !Objects.equals(row.getUserId(), userId)) {
                throw drift("CONNECTOR_FAMILY_SCOPE_DRIFT");
            }
            previousId = rowId;
            result.add(new FamilyCandidate(row, toFamilyView(row, tenantId, now)));
        }
        return List.copyOf(result);
    }

    private List<BoardPortalConnectorBindingView> currentBindings(
            List<BoardPortalConnectorBindingRow> rows,
            long tenantId,
            long memberId,
            long userId,
            Instant now) {
        List<BoardPortalConnectorBindingView> result = new ArrayList<>(rows.size());
        long previousId = Long.MAX_VALUE;
        for (BoardPortalConnectorBindingRow row : rows) {
            long rowId = requireRowId(row == null ? null : row.getRowId());
            if (rowId >= previousId
                    || !Objects.equals(row.getMemberId(), memberId)
                    || !Objects.equals(row.getUserId(), userId)) {
                throw drift("CONNECTOR_BINDING_SCOPE_DRIFT");
            }
            previousId = rowId;
            result.add(toBindingView(row, tenantId, now));
        }
        return List.copyOf(result);
    }

    private BoardPortalConnectorView activeConnector(
            long tenantId,
            long memberId,
            BoardPortalOAuthFamilyView family,
            BoardPortalConnectorBindingView binding) {
        Instant expiresAt = family.expiresAt().isBefore(binding.validUntil())
                ? family.expiresAt() : binding.validUntil();
        if (family.issuedAt().isAfter(binding.lastSeenAt())
                || !binding.lastSeenAt().isBefore(expiresAt)) {
            return unknownConnector(tenantId, memberId);
        }
        return new BoardPortalConnectorView(
                tenantId,
                memberId,
                "ACTIVE",
                "BOARD_VIP",
                family.clientRef(),
                family.familyRef(),
                binding.bindingRef(),
                BoardOAuthProfile.REQUIRED_SCOPES,
                family.issuedAt(),
                expiresAt,
                binding.lastSeenAt(),
                Math.max(family.version(), binding.version()),
                "ACTION_COMPLETED");
    }

    private BoardPortalConnectorView pendingConnector(
            long tenantId,
            long memberId,
            BoardPortalOAuthFamilyView family) {
        return new BoardPortalConnectorView(
                tenantId,
                memberId,
                "PENDING_ACTIVATION",
                "BOARD_FREE",
                family.clientRef(),
                family.familyRef(),
                null,
                BoardOAuthProfile.REQUIRED_SCOPES,
                family.issuedAt(),
                family.expiresAt(),
                null,
                family.version(),
                "ACTION_COMPLETED");
    }

    private BoardPortalConnectorView reauthConnector(
            long tenantId,
            long memberId,
            BoardPortalOAuthFamilyView family,
            List<BoardPortalConnectorBindingView> bindings) {
        BoardPortalConnectorBindingView matchingBinding = bindings.stream()
                .filter(binding -> Objects.equals(binding.bindingRef(), family.bindingRef()))
                .filter(binding -> Objects.equals(binding.clientRef(), family.clientRef()))
                .filter(binding -> !family.issuedAt().isAfter(binding.lastSeenAt()))
                .filter(binding -> binding.lastSeenAt().isBefore(family.expiresAt()))
                .findFirst()
                .orElse(null);
        return new BoardPortalConnectorView(
                tenantId,
                memberId,
                "REAUTH_REQUIRED",
                "BOARD_FREE",
                family.clientRef(),
                family.familyRef(),
                matchingBinding == null ? null : matchingBinding.bindingRef(),
                BoardOAuthProfile.REQUIRED_SCOPES,
                family.issuedAt(),
                family.expiresAt(),
                matchingBinding == null ? null : matchingBinding.lastSeenAt(),
                family.version(),
                "CURRENT_READ_COMPLETE");
    }

    private static BoardPortalConnectorView notConnected(long tenantId, long memberId) {
        return emptyConnector(
                tenantId, memberId, "NOT_CONNECTED", "CURRENT_READ_COMPLETE");
    }

    private static BoardPortalConnectorView unknownConnector(long tenantId, long memberId) {
        return emptyConnector(
                tenantId, memberId, "UNKNOWN", "CURRENT_READ_INCOMPLETE");
    }

    private static BoardPortalConnectorView emptyConnector(
            long tenantId, long memberId, String state, String evidenceLevel) {
        return new BoardPortalConnectorView(
                tenantId,
                memberId,
                state,
                "BOARD_FREE",
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null,
                0L,
                evidenceLevel);
    }

    private BoardPortalOAuthClientView toClientView(
            BoardPortalOAuthClientRow row, Instant now) {
        if (row == null
                || !isReference(row.getClientRef())
                || !IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME
                        .equals(row.getDisplayName())
                || !hasFixedProfile(
                        row.getIssuerUri(), row.getResourceUri(), row.getProductCode(),
                        row.getSourceCode(), row.getConnectorCode(), row.getScopeCanonical(),
                        row.getScopeDigest())
                || !TOKEN_AUTH_METHOD.equals(row.getTokenEndpointAuthMethod())
                || !GRANTS_CANONICAL.equals(row.getGrantTypesCanonical())
                || !RESPONSES_CANONICAL.equals(row.getResponseTypesCanonical())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(row.getRedirectUri())
                || row.getRedirectPort() == null
                || BoardOAuthProfile.requireLoopbackPort(row.getRedirectUri())
                        != row.getRedirectPort()
                || row.getVersion() == null || row.getVersion() < 0
                || !isDigest(row.getMetadataDigest())
                || !isDigest(row.getRegistrationSourceDigest())) {
            throw drift("CLIENT_PROFILE_DRIFT");
        }
        Instant registeredAt = requireInstant(row.getRegisteredAt(), "CLIENT_TIME_DRIFT");
        Instant expiresAt = requireInstant(row.getExpiresAt(), "CLIENT_TIME_DRIFT");
        Instant terminatedAt = optionalInstant(row.getEffectiveTerminatedAt());
        if (!registeredAt.isBefore(expiresAt)
                || !registeredAt.plus(CLIENT_LIFETIME).equals(expiresAt)) {
            throw drift("CLIENT_TIME_DRIFT");
        }
        validateClientLifecycle(row.getStoredStatus(), row.getEffectiveStatus(),
                registeredAt, expiresAt, terminatedAt, now);
        return new BoardPortalOAuthClientView(
                row.getClientRef(),
                row.getDisplayName(),
                row.getEffectiveStatus(),
                row.getRedirectUri(),
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                BoardOAuthProfile.REQUIRED_SCOPES,
                registeredAt,
                expiresAt,
                terminatedAt,
                row.getVersion(),
                digestRef.reference(
                        "client-metadata", row.getClientRef(), row.getMetadataDigest()),
                digestRef.reference(
                        "client-source", row.getClientRef(),
                        row.getRegistrationSourceDigest()));
    }

    private BoardPortalOAuthFamilyView toFamilyView(
            BoardPortalOAuthFamilyRow row, long expectedTenantId, Instant now) {
        if (row == null
                || !Objects.equals(row.getTenantId(), expectedTenantId)
                || !isPositive(row.getMemberId())
                || !isPositive(row.getUserId())
                || !isReference(row.getFamilyRef())
                || !isReference(row.getClientRef())
                || !contains(CONSENT_INTENTS, row.getConsentIntent())
                || !hasFixedProfile(
                        row.getIssuerUri(), row.getResourceUri(), row.getProductCode(),
                        row.getSourceCode(), row.getConnectorCode(), row.getScopeCanonical(),
                        row.getScopeDigest())
                || row.getCurrentRefreshGeneration() == null
                || row.getCurrentRefreshGeneration() < 0
                || row.getCurrentRefreshGeneration() > MAX_REFRESH_GENERATION
                || row.getVersion() == null || row.getVersion() < 0) {
            throw drift("FAMILY_PROFILE_DRIFT");
        }
        validateFamilyClient(row, now);
        Instant issuedAt = requireInstant(row.getIssuedAt(), "FAMILY_TIME_DRIFT");
        Instant expiresAt = requireInstant(row.getExpiresAt(), "FAMILY_TIME_DRIFT");
        Instant activatedAt = optionalInstant(row.getActivatedAt());
        Instant terminatedAt = optionalInstant(row.getEffectiveTerminatedAt());
        Instant clientExpiresAt = requireInstant(
                row.getClientExpiresAt(), "FAMILY_CLIENT_DRIFT");
        if (!issuedAt.isBefore(expiresAt)
                || expiresAt.isAfter(issuedAt.plus(MAX_FAMILY_LIFETIME))
                || expiresAt.isAfter(clientExpiresAt)) {
            throw drift("FAMILY_TIME_DRIFT");
        }
        String effectiveStatus = effectiveFamilyStatus(
                row.getStoredStatus(), row.getEffectiveStatus(),
                expiresAt, terminatedAt, now);
        validateFamilyLifecycle(row, effectiveStatus, issuedAt, activatedAt,
                expiresAt, terminatedAt, now);
        return new BoardPortalOAuthFamilyView(
                row.getFamilyRef(),
                expectedTenantId,
                tenantLabel(row.getTenantName(), expectedTenantId),
                "成员 #" + row.getMemberId(),
                "用户 #" + row.getUserId(),
                row.getClientRef(),
                row.getConsentIntent(),
                effectiveStatus,
                row.getCurrentRefreshGeneration(),
                row.getBindingRef(),
                issuedAt,
                activatedAt,
                expiresAt,
                terminatedAt,
                row.getVersion());
    }

    private BoardPortalConnectorBindingView toBindingView(
            BoardPortalConnectorBindingRow row, long expectedTenantId, Instant now) {
        if (row == null
                || !Objects.equals(row.getTenantId(), expectedTenantId)
                || !isPositive(row.getMemberId())
                || !isPositive(row.getUserId())
                || !isReference(row.getBindingRef())
                || !isReference(row.getClientRef())
                || !hasFixedProfile(
                        row.getIssuerUri(), row.getResourceUri(), row.getProductCode(),
                        row.getSourceCode(), row.getConnectorCode(),
                        BoardOAuthProfile.CANONICAL_SCOPE,
                        BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE))
                || !contains(BINDING_STATUSES, row.getStatus())
                || !contains(VERIFICATION_METHODS, row.getVerificationMethod())
                || row.getVersion() == null || row.getVersion() <= 0
                || row.getEntitlementActive() == null || row.getFamilyActive() == null
                || row.getPrincipalSubjectDigest() == null
                || !HEX_DIGEST.matcher(row.getPrincipalSubjectDigest()).matches()
                || !BoardOAuthProfile.hasExactScopeSet(row.getScopes())) {
            throw drift("BINDING_PROFILE_DRIFT");
        }
        Instant verifiedAt = requireInstant(row.getVerifiedAt(), "BINDING_TIME_DRIFT");
        Instant lastSeenAt = requireInstant(row.getLastSeenAt(), "BINDING_TIME_DRIFT");
        Instant validUntil = requireInstant(row.getValidUntil(), "BINDING_TIME_DRIFT");
        Instant revokedAt = optionalInstant(row.getRevokedAt());
        if (verifiedAt.isAfter(lastSeenAt) || !lastSeenAt.isBefore(validUntil)) {
            throw drift("BINDING_TIME_DRIFT");
        }
        boolean active = "ACTIVE".equals(row.getStatus());
        if ((active && revokedAt != null)
                || (!active && (revokedAt == null || revokedAt.isBefore(lastSeenAt)))
                || (!active && Boolean.TRUE.equals(row.getFamilyActive()))
                || (Boolean.TRUE.equals(row.getFamilyActive())
                        && (!active || !now.isBefore(validUntil)))) {
            throw drift("BINDING_LIFECYCLE_DRIFT");
        }
        boolean entitlementActive = Boolean.TRUE.equals(row.getEntitlementActive());
        boolean familyActive = Boolean.TRUE.equals(row.getFamilyActive());
        boolean vipEffective = active && now.isBefore(validUntil)
                && entitlementActive && familyActive;
        String digestScope = expectedTenantId + ":" + row.getBindingRef();
        byte[] storedDigest;
        try {
            storedDigest = HexFormat.of().parseHex(row.getPrincipalSubjectDigest());
        } catch (IllegalArgumentException exception) {
            throw drift("BINDING_PROFILE_DRIFT");
        }
        return new BoardPortalConnectorBindingView(
                row.getBindingRef(),
                expectedTenantId,
                tenantLabel(row.getTenantName(), expectedTenantId),
                "成员 #" + row.getMemberId(),
                "用户 #" + row.getUserId(),
                row.getProductCode(),
                row.getSourceCode(),
                row.getConnectorCode(),
                row.getStatus(),
                BoardOAuthProfile.REQUIRED_SCOPES,
                row.getVerificationMethod(),
                verifiedAt,
                lastSeenAt,
                validUntil,
                revokedAt,
                row.getClientRef(),
                digestRef.reference("binding-subject", digestScope, storedDigest),
                row.getVersion(),
                entitlementActive,
                familyActive,
                vipEffective);
    }

    private void validateFamilyClient(BoardPortalOAuthFamilyRow row, Instant now) {
        if (!IndependentBoardOAuthClientRegistrationService.NEUTRAL_CLIENT_NAME
                    .equals(row.getClientDisplayName())
                || !contains(CLIENT_STATUSES, row.getClientStatus())
                || !hasFixedProfile(
                        row.getClientIssuerUri(), row.getClientResourceUri(),
                        row.getClientProductCode(), row.getClientSourceCode(),
                        row.getClientConnectorCode(), row.getClientScopeCanonical(),
                        row.getClientScopeDigest())
                || !TOKEN_AUTH_METHOD.equals(row.getClientTokenEndpointAuthMethod())
                || !GRANTS_CANONICAL.equals(row.getClientGrantTypesCanonical())
                || !RESPONSES_CANONICAL.equals(row.getClientResponseTypesCanonical())
                || !BoardOAuthProfile.isAllowedLoopbackRedirect(row.getClientRedirectUri())
                || row.getClientExpiresAt() == null) {
            throw drift("FAMILY_CLIENT_DRIFT");
        }
        if (isLiveFamilyStatus(row.getEffectiveStatus())
                && (!"ACTIVE".equals(row.getClientStatus())
                    || !now.isBefore(row.getClientExpiresAt().toInstant())
                    || row.getClientTerminatedAt() != null)) {
            throw drift("FAMILY_CLIENT_DRIFT");
        }
    }

    private void validateFamilyLifecycle(
            BoardPortalOAuthFamilyRow row,
            String status,
            Instant issuedAt,
            Instant activatedAt,
            Instant expiresAt,
            Instant terminatedAt,
            Instant now) {
        boolean hasBinding = row.getBindingRef() != null;
        if (hasBinding != (row.getBindingVersion() != null)
                || (row.getBindingRef() != null && !isReference(row.getBindingRef()))) {
            throw drift("FAMILY_LIFECYCLE_DRIFT");
        }
        if ("PENDING_BINDING".equals(status)) {
            if (hasBinding || activatedAt != null || terminatedAt != null
                    || row.getCurrentRefreshGeneration() != 0
                    || !now.isBefore(expiresAt)) {
                throw drift("FAMILY_LIFECYCLE_DRIFT");
            }
            return;
        }
        if ("ACTIVE".equals(status)) {
            if (!hasBinding || activatedAt == null || terminatedAt != null
                    || activatedAt.isBefore(issuedAt) || !activatedAt.isBefore(expiresAt)
                    || !now.isBefore(expiresAt)
                    || !Objects.equals(row.getCurrentBindingTenantId(), row.getTenantId())
                    || !Objects.equals(row.getCurrentBindingMemberId(), row.getMemberId())
                    || !Objects.equals(row.getCurrentBindingUserId(), row.getUserId())
                    || !Objects.equals(row.getCurrentBindingClientRef(), row.getClientRef())
                    || !"ACTIVE".equals(row.getCurrentBindingStatus())
                    || !Objects.equals(row.getCurrentBindingVersion(), row.getBindingVersion())
                    || row.getCurrentBindingValidUntil() == null
                    || !now.isBefore(row.getCurrentBindingValidUntil().toInstant())) {
                throw drift("FAMILY_LIFECYCLE_DRIFT");
            }
            return;
        }
        if (terminatedAt == null
                || (activatedAt == null) != !hasBinding
                || (activatedAt == null && terminatedAt.isBefore(issuedAt))
                || (activatedAt != null
                    && (activatedAt.isBefore(issuedAt)
                        || !activatedAt.isBefore(expiresAt)
                        || terminatedAt.isBefore(activatedAt)))
                || ("EXPIRED".equals(status) && terminatedAt.isBefore(expiresAt))) {
            throw drift("FAMILY_LIFECYCLE_DRIFT");
        }
    }

    private String effectiveFamilyStatus(
            String storedStatus,
            String effectiveStatus,
            Instant expiresAt,
            Instant terminatedAt,
            Instant now) {
        if (!contains(FAMILY_STATUSES, storedStatus)
                || !contains(FAMILY_STATUSES, effectiveStatus)) {
            throw drift("FAMILY_LIFECYCLE_DRIFT");
        }
        boolean naturallyExpired = isLiveFamilyStatus(storedStatus)
                && !now.isBefore(expiresAt);
        String expected = naturallyExpired ? "EXPIRED" : storedStatus;
        if (!expected.equals(effectiveStatus)
                || (naturallyExpired && !Objects.equals(terminatedAt, expiresAt))) {
            throw drift("FAMILY_LIFECYCLE_DRIFT");
        }
        return effectiveStatus;
    }

    private void validateClientLifecycle(
            String storedStatus,
            String effectiveStatus,
            Instant registeredAt,
            Instant expiresAt,
            Instant terminatedAt,
            Instant now) {
        if (!contains(CLIENT_STATUSES, storedStatus)
                || !contains(CLIENT_STATUSES, effectiveStatus)) {
            throw drift("CLIENT_LIFECYCLE_DRIFT");
        }
        boolean naturallyExpired = "ACTIVE".equals(storedStatus)
                && !now.isBefore(expiresAt);
        String expected = naturallyExpired ? "EXPIRED" : storedStatus;
        if (!expected.equals(effectiveStatus)
                || (naturallyExpired && !Objects.equals(terminatedAt, expiresAt))
                || ("ACTIVE".equals(effectiveStatus)
                    && (terminatedAt != null || !now.isBefore(expiresAt)))
                || ("REVOKED".equals(effectiveStatus)
                    && (terminatedAt == null || terminatedAt.isBefore(registeredAt)))
                || ("EXPIRED".equals(effectiveStatus)
                    && (terminatedAt == null || terminatedAt.isBefore(expiresAt)))) {
            throw drift("CLIENT_LIFECYCLE_DRIFT");
        }
    }

    private <R, V> BoardPortalReadEnvelope<V> page(
            List<R> rows,
            PagePosition position,
            BoardPortalReadCursor.Context cursorContext,
            ToLongFunction<R> rowId,
            Function<R, V> mapperFunction,
            Function<V, String> publicIdentity) {
        if (rows == null || rows.size() > ROW_LIMIT) {
            throw drift("PAGE_CARDINALITY_DRIFT");
        }
        List<V> mapped = new ArrayList<>(rows.size());
        Set<String> identities = new HashSet<>();
        long previousId = Long.MAX_VALUE;
        Long highWaterId = position.highWaterId();
        for (int index = 0; index < rows.size(); index++) {
            R row = rows.get(index);
            long currentId = rowId.applyAsLong(row);
            if (index == 0 && highWaterId == null) {
                highWaterId = currentId;
            }
            if (currentId >= previousId
                    || (position.highWaterId() != null
                        && currentId > position.highWaterId())
                    || (position.lastId() != null && currentId >= position.lastId())) {
                throw drift("PAGE_ORDER_DRIFT");
            }
            previousId = currentId;
            V view = mapperFunction.apply(row);
            String identity = publicIdentity.apply(view);
            if (identity == null || !identities.add(identity)) {
                throw drift("PAGE_IDENTITY_DRIFT");
            }
            mapped.add(view);
        }
        boolean truncated = rows.size() == ROW_LIMIT;
        String nextCursor = null;
        List<V> records = mapped;
        if (truncated) {
            long lastReturnedId = rowId.applyAsLong(rows.get(PAGE_LIMIT - 1));
            nextCursor = cursorCodec.encode(
                    cursorContext, highWaterId, lastReturnedId);
            records = mapped.subList(0, PAGE_LIMIT);
        }
        return new BoardPortalReadEnvelope<>(records, PAGE_LIMIT, truncated, nextCursor);
    }

    private PagePosition decodePosition(
            BoardPortalReadCursor.Context context, String cursor) {
        if (cursor == null) {
            return new PagePosition(null, null);
        }
        try {
            BoardPortalReadCursor.Position decoded = cursorCodec.decode(context, cursor);
            return new PagePosition(
                    decoded.highWaterRowId(), decoded.lastReturnedRowId());
        } catch (IllegalArgumentException exception) {
            throw new BoardPortalBadRequestException("INVALID_CURSOR");
        }
    }

    private static String normalizeStatus(String status, Set<String> allowed) {
        if (status == null) {
            return "ALL";
        }
        if (!allowed.contains(status)) {
            throw new BoardPortalBadRequestException("INVALID_STATUS");
        }
        return status;
    }

    private static String mapperStatus(String normalizedStatus) {
        return "ALL".equals(normalizedStatus) ? null : normalizedStatus;
    }

    private static boolean hasFixedProfile(
            String issuer,
            String resource,
            String productCode,
            String sourceCode,
            String connectorCode,
            String scopeCanonical,
            byte[] scopeDigest) {
        return BoardOAuthProfile.isExpectedIssuer(issuer)
                && BoardOAuthProfile.isExpectedResource(resource)
                && PRODUCT_CODE.equals(productCode)
                && SOURCE_CODE.equals(sourceCode)
                && CONNECTOR_CODE.equals(connectorCode)
                && BoardOAuthProfile.CANONICAL_SCOPE.equals(scopeCanonical)
                && BoardOAuthCrypto.constantTimeEquals(
                        BoardOAuthCrypto.sha256Ascii(BoardOAuthProfile.CANONICAL_SCOPE),
                        scopeDigest);
    }

    private static String tenantLabel(String tenantName, long tenantId) {
        if (tenantName == null || tenantName.isBlank()) {
            return "企业 #" + tenantId;
        }
        String normalized = tenantName.trim();
        if (normalized.length() > 128) {
            throw drift("TENANT_LABEL_DRIFT");
        }
        return normalized;
    }

    private static long requireRowId(Long rowId) {
        if (!isPositive(rowId)) {
            throw drift("PAGE_ROW_ID_DRIFT");
        }
        return rowId;
    }

    private static boolean isReference(String value) {
        return value != null && REFERENCE.matcher(value).matches();
    }

    private static boolean contains(Set<String> allowed, String value) {
        return value != null && allowed.contains(value);
    }

    private static boolean isLiveFamilyStatus(String value) {
        return "ACTIVE".equals(value) || "PENDING_BINDING".equals(value);
    }

    private static boolean isDigest(byte[] digest) {
        return digest != null && digest.length == BoardOAuthCrypto.SHA256_BYTES;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private static void requirePositive(long value, String code) {
        if (value <= 0) {
            throw new BoardPortalBadRequestException(code);
        }
    }

    private static Instant requireInstant(Date value, String code) {
        if (value == null) {
            throw drift(code);
        }
        return value.toInstant();
    }

    private static Instant optionalInstant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private static BoardPortalDataDriftException drift(String code) {
        return new BoardPortalDataDriftException(code);
    }

    private record PagePosition(Long highWaterId, Long lastId) {
    }

    private record FamilyCandidate(
            BoardPortalOAuthFamilyRow row,
            BoardPortalOAuthFamilyView view) {
    }
}
