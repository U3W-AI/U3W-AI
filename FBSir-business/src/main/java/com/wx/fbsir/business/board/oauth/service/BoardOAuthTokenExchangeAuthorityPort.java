package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import java.time.Instant;

/**
 * Locks the board-side authority slots needed by an OAuth code exchange.
 *
 * <p>The result is observational. Callers must retain it until the locked code
 * state is known: an inactive authority rejects fresh issuance, but must not
 * prevent containment of a genuinely replayed consumed code.</p>
 */
public interface BoardOAuthTokenExchangeAuthorityPort {
    LockResult lockForTokenExchange(
            Long tenantId,
            Long memberId,
            Long userId,
            String productCode,
            String sourceCode,
            String connectorCode,
            Instant observedAt);

    enum BindingTopology {
        ABSENT,
        PRESENT
    }

    record LockResult(
            boolean enterpriseCurrent,
            boolean memberCurrent,
            boolean entitlementCurrent,
            boolean planCurrent,
            BindingTopology bindingTopology,
            boolean bindingShapeCurrent,
            Instant observedAt,
            Instant entitlementValidUntilExclusive) {
        LockResult(
                boolean enterpriseCurrent,
                boolean memberCurrent,
                boolean entitlementCurrent,
                boolean planCurrent,
                BindingTopology bindingTopology,
                boolean bindingShapeCurrent) {
            this(
                    enterpriseCurrent,
                    memberCurrent,
                    entitlementCurrent,
                    planCurrent,
                    bindingTopology,
                    bindingShapeCurrent,
                    Instant.EPOCH,
                    null);
        }

        public LockResult {
            if (bindingTopology == null) {
                throw new IllegalArgumentException("bindingTopology is required");
            }
            if (observedAt == null) {
                throw new IllegalArgumentException("observedAt is required");
            }
        }

        public boolean issuanceAuthorityCurrent() {
            return enterpriseCurrent
                    && memberCurrent
                    && entitlementCurrent
                    && planCurrent
                    && bindingShapeCurrent;
        }

        public boolean permits(BoardOAuthConsentIntent intent) {
            return issuanceAuthorityCurrent()
                    && ((intent == BoardOAuthConsentIntent.FIRST_CONNECT
                                    && bindingTopology == BindingTopology.ABSENT)
                            || (intent == BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION
                                    && bindingTopology == BindingTopology.PRESENT));
        }

        public boolean issuanceAuthorityCurrentAt(Instant decisionAt) {
            return decisionAt != null
                    && !decisionAt.isBefore(observedAt)
                    && (entitlementValidUntilExclusive == null
                            || decisionAt.isBefore(entitlementValidUntilExclusive))
                    && issuanceAuthorityCurrent();
        }

        public boolean permits(BoardOAuthConsentIntent intent, Instant decisionAt) {
            return issuanceAuthorityCurrentAt(decisionAt)
                    && ((intent == BoardOAuthConsentIntent.FIRST_CONNECT
                                    && bindingTopology == BindingTopology.ABSENT)
                            || (intent == BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION
                                    && bindingTopology == BindingTopology.PRESENT));
        }
    }
}
