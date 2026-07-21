package com.wx.fbsir.business.board.oauth.service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Locks and, only for a proven refresh replay, mutates the W4a authority rows.
 *
 * <p>The opaque lease is created by the production binding service and may only
 * be passed back to that same service in the same root transaction and thread.</p>
 */
public interface BoardOAuthRefreshAuthorityPort {
    LockResult lockForRefresh(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            String sourceCode,
            String connectorCode,
            Instant lockRequestedAt);

    /** Locks the complete W4a receipt prefix after OAuth tokens and before W4b receipts. */
    void lockReceiptsForRefresh(Lease lease);

    BindingRevocation revokeForRefreshReplay(
            Lease lease,
            String expectedBindingId,
            Long expectedBindingVersion,
            Instant transitionAt);

    /** Intentionally opaque capability. */
    interface Lease { }

    record LockResult(
            Lease lease,
            boolean enterpriseCurrent,
            boolean memberCurrent,
            boolean entitlementCurrent,
            boolean planCurrent,
            boolean bindingShapeCurrent,
            boolean bindingActive,
            String bindingId,
            Long bindingVersion,
            String bindingClientId,
            byte[] principalSubjectDigest,
            List<String> scopes,
            Instant observedAt,
            Instant authorityValidUntilExclusive) {
        public LockResult {
            Objects.requireNonNull(lease, "lease");
            Objects.requireNonNull(observedAt, "observedAt");
            principalSubjectDigest = copy(principalSubjectDigest);
            scopes = scopes == null ? List.of() : List.copyOf(scopes);
        }

        @Override
        public byte[] principalSubjectDigest() {
            return copy(principalSubjectDigest);
        }

        public boolean rotationAuthorityCurrentAt(Instant decisionAt) {
            return decisionAt != null
                    && !decisionAt.isBefore(observedAt)
                    && (authorityValidUntilExclusive == null
                            || decisionAt.isBefore(authorityValidUntilExclusive))
                    && enterpriseCurrent
                    && memberCurrent
                    && entitlementCurrent
                    && planCurrent
                    && bindingShapeCurrent
                    && bindingActive;
        }

        private static byte[] copy(byte[] value) {
            return value == null ? null : value.clone();
        }
    }

    record BindingRevocation(
            String bindingId,
            Long previousVersion,
            Long revokedVersion,
            Instant revokedAt,
            String receiptId,
            String receiptPayloadDigest) {
        public BindingRevocation {
            Objects.requireNonNull(bindingId, "bindingId");
            Objects.requireNonNull(previousVersion, "previousVersion");
            Objects.requireNonNull(revokedVersion, "revokedVersion");
            Objects.requireNonNull(revokedAt, "revokedAt");
            Objects.requireNonNull(receiptId, "receiptId");
            Objects.requireNonNull(receiptPayloadDigest, "receiptPayloadDigest");
        }
    }
}
