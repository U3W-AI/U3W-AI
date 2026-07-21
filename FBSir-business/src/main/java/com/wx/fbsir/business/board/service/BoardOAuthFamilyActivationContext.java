package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import java.util.Arrays;
import java.util.Date;

/**
 * Non-secret, lease-generated values that the W4b coordinator must use for
 * the activation receipt.  Instances can only be created by the W4a binding
 * service after it has locked and captured the OAuth family before-image.
 */
final class BoardOAuthFamilyActivationContext {
    private final BoardConnectorBindingSnapshot binding;
    private final String receiptId;
    private final String action;
    private final String clientId;
    private final String familyId;
    private final BoardOAuthConsentIntent consentIntent;
    private final BoardConnectorBindingActivationLease.Mode bindingTopology;
    private final byte[] principalSubjectDigest;
    private final String actorType;
    private final Long actorUserId;
    private final byte[] actorSubjectDigest;
    private final String correlationId;
    private final byte[] payloadDigest;
    private final String evidenceLevel;
    private final Date createdAt;

    BoardOAuthFamilyActivationContext(
            BoardConnectorBindingSnapshot binding,
            String receiptId,
            String action,
            String clientId,
            String familyId,
            BoardOAuthConsentIntent consentIntent,
            BoardConnectorBindingActivationLease.Mode bindingTopology,
            byte[] principalSubjectDigest,
            Long actorUserId,
            String correlationId,
            byte[] payloadDigest,
            Date createdAt) {
        this.binding = binding;
        this.receiptId = receiptId;
        this.action = action;
        this.clientId = clientId;
        this.familyId = familyId;
        this.consentIntent = consentIntent;
        this.bindingTopology = bindingTopology;
        this.principalSubjectDigest = copy(principalSubjectDigest);
        this.actorType = "USER";
        this.actorUserId = actorUserId;
        this.actorSubjectDigest = copy(principalSubjectDigest);
        this.correlationId = correlationId;
        this.payloadDigest = copy(payloadDigest);
        this.evidenceLevel = "ACTION_COMPLETED";
        this.createdAt = copy(createdAt);
    }

    BoardConnectorBindingSnapshot binding() { return binding; }
    String receiptId() { return receiptId; }
    String action() { return action; }
    String clientId() { return clientId; }
    String familyId() { return familyId; }
    BoardOAuthConsentIntent consentIntent() { return consentIntent; }
    BoardConnectorBindingActivationLease.Mode bindingTopology() { return bindingTopology; }
    String bindingId() { return binding.bindingId(); }
    Long bindingVersion() { return binding.version(); }
    Long tenantId() { return binding.tenantId(); }
    Long memberId() { return binding.memberId(); }
    Long userId() { return binding.userId(); }
    byte[] principalSubjectDigest() { return copy(principalSubjectDigest); }
    String actorType() { return actorType; }
    Long actorUserId() { return actorUserId; }
    byte[] actorSubjectDigest() { return copy(actorSubjectDigest); }
    String correlationId() { return correlationId; }
    byte[] payloadDigest() { return copy(payloadDigest); }
    String evidenceLevel() { return evidenceLevel; }
    Date createdAt() { return copy(createdAt); }

    @Override
    public String toString() {
        return "BoardOAuthFamilyActivationContext[redacted]";
    }

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    private static Date copy(Date value) {
        return value == null ? null : new Date(value.getTime());
    }
}
