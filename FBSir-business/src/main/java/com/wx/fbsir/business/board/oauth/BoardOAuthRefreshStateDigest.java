package com.wx.fbsir.business.board.oauth;

import com.wx.fbsir.business.board.oauth.domain.BoardOAuthToken;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthRefreshAuthorityPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Canonical, digest-only snapshot of refresh-family security state.
 *
 * <p>The input deliberately excludes raw bearer material. Rows must already be
 * locked and current-read by the caller; this class only gives that image one
 * deterministic, versioned representation.</p>
 */
public final class BoardOAuthRefreshStateDigest {
    private static final String DOMAIN = "FBSIR:OAUTH:REFRESH_STATE:v1";

    private BoardOAuthRefreshStateDigest() {
    }

    public static byte[] digest(
            String phase,
            BoardOAuthTokenFamily family,
            List<BoardOAuthToken> tokens,
            BoardOAuthRefreshAuthorityPort.LockResult authority,
            BoardOAuthRefreshAuthorityPort.BindingRevocation bindingRevocation) {
        if (!("BEFORE".equals(phase) || "AFTER".equals(phase))
                || family == null
                || tokens == null
                || authority == null) {
            throw invalid();
        }
        CanonicalWriter writer = new CanonicalWriter();
        writer.text("domain", DOMAIN);
        // BEFORE/AFTER is a call-site guard, not digest input. Equal row images
        // must hash equally so the receipt contract can reject zero-change
        // security events instead of manufacturing a difference from a label.
        appendFamily(writer, family);
        appendTokens(writer, tokens, family.getFamilyId());
        appendAuthority(writer, authority);
        appendRevocation(writer, bindingRevocation);
        return BoardOAuthCrypto.sha256Ascii(writer.value());
    }

    private static void appendFamily(
            CanonicalWriter writer,
            BoardOAuthTokenFamily family) {
        writer.number("family.row_id", requirePositive(family.getId()));
        writer.text("family.id", requireText(family.getFamilyId()));
        writer.number("family.origin_code", requirePositive(
                family.getOriginAuthorizationCodeId()));
        writer.text("family.client", requireText(family.getClientId()));
        writer.number("family.tenant", requirePositive(family.getTenantId()));
        writer.number("family.member", requirePositive(family.getMemberId()));
        writer.number("family.user", requirePositive(family.getUserId()));
        writer.text("family.product", requireText(family.getProductCode()));
        writer.text("family.source", requireText(family.getSourceCode()));
        writer.text("family.connector", requireText(family.getConnectorCode()));
        writer.text("family.issuer", requireText(family.getIssuerUri()));
        writer.text("family.resource", requireText(family.getResourceUri()));
        writer.text("family.scope", requireText(family.getScopeCanonical()));
        writer.digest("family.scope_digest", requireDigest(family.getScopeDigest()));
        writer.digest("family.principal", requireDigest(
                family.getPrincipalSubjectDigest()));
        writer.text("family.consent", family.getConsentIntent() == null
                ? null : family.getConsentIntent().name());
        writer.text("family.binding", requireText(family.getBindingId()));
        writer.number("family.binding_version", requirePositive(
                family.getBindingVersion()));
        writer.text("family.status", requireText(family.getStatus()));
        writer.number("family.generation", requireUnsignedInt(
                family.getCurrentRefreshGeneration()));
        writer.time("family.issued", requireDate(family.getIssuedAt()));
        writer.time("family.activated", requireDate(family.getActivatedAt()));
        writer.time("family.expires", requireDate(family.getExpiresAt()));
        writer.time("family.terminated", family.getTerminatedAt());
        writer.number("family.version", requireNonNegative(family.getVersion()));
    }

    private static void appendTokens(
            CanonicalWriter writer,
            List<BoardOAuthToken> source,
            String expectedFamilyId) {
        List<BoardOAuthToken> tokens = new ArrayList<>(source);
        if (tokens.stream().anyMatch(Objects::isNull)) {
            throw invalid();
        }
        tokens.sort(Comparator.comparing(BoardOAuthToken::getId,
                Comparator.nullsFirst(Long::compareTo)));
        writer.number("tokens.count", (long) tokens.size());
        Long prior = null;
        for (int index = 0; index < tokens.size(); index++) {
            BoardOAuthToken token = tokens.get(index);
            Long tokenId = requirePositive(token.getId());
            if (prior != null && tokenId <= prior) {
                throw invalid();
            }
            prior = tokenId;
            String prefix = "token." + index + ".";
            if (!Objects.equals(expectedFamilyId, token.getFamilyId())) {
                throw invalid();
            }
            writer.number(prefix + "id", tokenId);
            writer.digest(prefix + "digest", requireDigest(token.getTokenDigest()));
            writer.text(prefix + "family", requireText(token.getFamilyId()));
            writer.text(prefix + "type", requireText(token.getTokenType()));
            writer.number(prefix + "generation", requireUnsignedInt(
                    token.getGeneration()));
            writer.text(prefix + "resource", requireText(token.getResourceUri()));
            writer.text(prefix + "scope", requireText(token.getScopeCanonical()));
            writer.digest(prefix + "scope_digest", requireDigest(
                    token.getScopeDigest()));
            writer.text(prefix + "status", requireText(token.getStatus()));
            writer.time(prefix + "issued", requireDate(token.getIssuedAt()));
            writer.time(prefix + "used", token.getUsedAt());
            writer.time(prefix + "revoked", token.getRevokedAt());
            writer.time(prefix + "expires", requireDate(token.getExpiresAt()));
            writer.number(prefix + "version", requireNonNegative(token.getVersion()));
        }
    }

    private static void appendAuthority(
            CanonicalWriter writer,
            BoardOAuthRefreshAuthorityPort.LockResult authority) {
        writer.bool("authority.enterprise", authority.enterpriseCurrent());
        writer.bool("authority.member", authority.memberCurrent());
        writer.bool("authority.entitlement", authority.entitlementCurrent());
        writer.bool("authority.plan", authority.planCurrent());
        writer.bool("authority.binding_shape", authority.bindingShapeCurrent());
        writer.bool("authority.binding_active", authority.bindingActive());
        writer.text("authority.binding", authority.bindingId());
        writer.number("authority.binding_version", authority.bindingVersion());
        writer.text("authority.client", authority.bindingClientId());
        writer.digest("authority.principal", authority.principalSubjectDigest());
        List<String> scopes = new ArrayList<>(authority.scopes());
        scopes.sort(String::compareTo);
        writer.number("authority.scope_count", (long) scopes.size());
        String prior = null;
        for (int index = 0; index < scopes.size(); index++) {
            String scope = requireText(scopes.get(index));
            if (Objects.equals(prior, scope)) {
                throw invalid();
            }
            prior = scope;
            writer.text("authority.scope." + index, scope);
        }
        writer.time("authority.observed", authority.observedAt());
        writer.time("authority.valid_until", authority.authorityValidUntilExclusive());
    }

    private static void appendRevocation(
            CanonicalWriter writer,
            BoardOAuthRefreshAuthorityPort.BindingRevocation revocation) {
        writer.bool("binding_revocation.present", revocation != null);
        if (revocation == null) {
            return;
        }
        writer.text("binding_revocation.binding", revocation.bindingId());
        writer.number("binding_revocation.previous_version",
                revocation.previousVersion());
        writer.number("binding_revocation.revoked_version",
                revocation.revokedVersion());
        writer.time("binding_revocation.at", revocation.revokedAt());
        writer.text("binding_revocation.receipt", revocation.receiptId());
        writer.text("binding_revocation.payload_digest",
                revocation.receiptPayloadDigest());
    }

    private static String requireText(String value) {
        if (value == null || value.isEmpty()) {
            throw invalid();
        }
        return value;
    }

    private static Long requirePositive(Long value) {
        if (value == null || value <= 0L) {
            throw invalid();
        }
        return value;
    }

    private static Long requireNonNegative(Long value) {
        if (value == null || value < 0L) {
            throw invalid();
        }
        return value;
    }

    private static Long requireUnsignedInt(Long value) {
        if (value == null || value < 0L || value > 4_294_967_295L) {
            throw invalid();
        }
        return value;
    }

    private static byte[] requireDigest(byte[] value) {
        if (value == null || value.length != BoardOAuthCrypto.SHA256_BYTES) {
            throw invalid();
        }
        return value;
    }

    private static Date requireDate(Date value) {
        if (value == null) {
            throw invalid();
        }
        return value;
    }

    private static BoardOAuthProtocolException invalid() {
        return BoardOAuthProtocolException.serverError(
                BoardOAuthRefreshReceiptFactory.RECEIPT_INVALID);
    }

    private static final class CanonicalWriter {
        private final StringBuilder value = new StringBuilder();

        private void text(String name, String field) {
            append(name, field);
        }

        private void number(String name, Long field) {
            append(name, field == null ? null : Long.toString(field));
        }

        private void bool(String name, boolean field) {
            append(name, field ? "1" : "0");
        }

        private void time(String name, Instant field) {
            append(name, field == null ? null : Long.toString(field.toEpochMilli()));
        }

        private void time(String name, Date field) {
            time(name, field == null ? null : field.toInstant());
        }

        private void digest(String name, byte[] field) {
            append(name, field == null ? null : HexFormat.of().formatHex(field));
        }

        private void append(String name, String field) {
            value.append(name.length()).append(':').append(name).append('=');
            if (field == null) {
                value.append("-1:");
            } else {
                value.append(field.length()).append(':').append(field);
            }
            value.append(';');
        }

        private String value() {
            return value.toString();
        }
    }
}
