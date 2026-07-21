package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.domain.BoardConnectorBindingReceipt;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test-source-only bridge for the pre-OAuth MySQL transaction fixtures.
 *
 * <p>The production service deliberately refuses to create a binding from a
 * caller-supplied attestation. These older database tests still need their
 * historical fixture in order to verify entitlement, locking and immutable
 * receipt behavior. Keeping the fixture in {@code src/test} prevents it from
 * becoming a production entrypoint.</p>
 */
public class LegacyConnectorBindingTestAdapter {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final IndependentBoardMapper mapper;
    private final IndependentBoardConnectorBindingService service;

    public LegacyConnectorBindingTestAdapter(
            IndependentBoardMapper mapper,
            IndependentBoardConnectorBindingService service) {
        this.mapper = mapper;
        this.service = service;
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardConnectorBindingSnapshot confirmLegacyProtectedRequest(
            BoardConnectorProtectedRequestAttestation attestation,
            Long actorUserId) {
        validate(attestation, actorUserId);
        BoardEnterpriseMemberScope member = mapper.selectExactActiveMemberForUpdate(
                attestation.tenantId(), attestation.memberId(), attestation.userId());
        if (member == null
                || !Objects.equals(member.getTenantId(), attestation.tenantId())
                || !Objects.equals(member.getMemberId(), attestation.memberId())
                || !Objects.equals(member.getUserId(), attestation.userId())
                || !Objects.equals(member.getStatus(), 1)
                || !Objects.equals(member.getDelFlag(), "0")) {
            throw new ServiceException("TENANT_MEMBER_USER_SCOPE_INVALID", 403);
        }

        Date now = new Date();
        BoardProductEntitlement entitlement = mapper.selectEntitlementForUpdate(
                attestation.tenantId(), attestation.memberId(),
                IndependentBoardEntitlementService.PRODUCT_CODE);
        if (entitlement == null
                || !Objects.equals(entitlement.getTenantId(), attestation.tenantId())
                || !Objects.equals(entitlement.getMemberId(), attestation.memberId())
                || !Objects.equals(entitlement.getUserId(), attestation.userId())
                || !Objects.equals(entitlement.getProductCode(),
                        IndependentBoardEntitlementService.PRODUCT_CODE)
                || !Objects.equals(entitlement.getPlanCode(),
                        IndependentBoardEntitlementService.VIP_PLAN)
                || !Objects.equals(entitlement.getStatus(),
                        IndependentBoardConnectorBindingService.STATUS_ACTIVE)
                || entitlement.getValidFrom() == null
                || entitlement.getValidFrom().after(now)
                || (entitlement.getValidUntil() != null
                        && !entitlement.getValidUntil().after(now))) {
            throw new ServiceException("CONNECTOR_VIP_ENTITLEMENT_NOT_CURRENT", 403);
        }

        BoardConnectorBinding existing = mapper.selectConnectorBindingForUpdate(
                attestation.tenantId(), attestation.memberId(), attestation.userId(),
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardConnectorBindingService.SOURCE_CODE,
                IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        if (existing != null) {
            return service.confirmProtectedRequest(attestation, actorUserId);
        }

        List<String> scopes = new ArrayList<>(attestation.scopes());
        scopes.sort(String::compareTo);
        BoardConnectorBinding binding = new BoardConnectorBinding();
        binding.setBindingId(UUID.randomUUID().toString());
        binding.setTenantId(attestation.tenantId());
        binding.setMemberId(attestation.memberId());
        binding.setUserId(attestation.userId());
        binding.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        binding.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        binding.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        binding.setIssuerUri(attestation.issuerUri());
        binding.setResourceUri(attestation.resourceUri());
        binding.setClientId(attestation.clientId());
        binding.setPrincipalSubjectDigest(attestation.principalSubjectDigest());
        binding.setStatus(IndependentBoardConnectorBindingService.STATUS_ACTIVE);
        binding.setVerificationMethod(attestation.verificationMethod());
        binding.setEvidenceDigest(attestation.evidenceDigest());
        binding.setVerifiedAt(now);
        binding.setLastSeenAt(now);
        binding.setValidUntil(attestation.validUntil());
        binding.setVersion(1L);
        binding.setScopes(scopes);
        if (mapper.insertConnectorBinding(binding) != 1) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_WRITE_FAILED", 500);
        }
        for (String scope : scopes) {
            if (mapper.insertConnectorBindingScope(binding.getBindingId(), scope, now) != 1) {
                throw new ServiceException("BOARD_CONNECTOR_BINDING_SCOPE_WRITE_FAILED", 500);
            }
        }
        insertReceipt(binding, actorUserId, now);
        return snapshot(binding);
    }

    private void validate(
            BoardConnectorProtectedRequestAttestation attestation,
            Long actorUserId) {
        if (attestation == null || actorUserId == null || actorUserId <= 0L) {
            throw new ServiceException("CONNECTOR_ATTESTATION_REQUIRED", 400);
        }
        if (!Objects.equals(actorUserId, attestation.userId())
                || !Objects.equals(attestation.issuerUri(), "https://api2.u3w.com")
                || !Objects.equals(attestation.resourceUri(),
                        IndependentBoardConnectorBindingService.RESOURCE_URI)
                || attestation.clientId() == null || attestation.clientId().isBlank()
                || !SHA256.matcher(attestation.principalSubjectDigest()).matches()
                || !SHA256.matcher(attestation.evidenceDigest()).matches()
                || (!Objects.equals(attestation.verificationMethod(),
                            IndependentBoardConnectorBindingService.VERIFY_INITIALIZE)
                    && !Objects.equals(attestation.verificationMethod(),
                            IndependentBoardConnectorBindingService.VERIFY_TOOLS_LIST))
                || attestation.scopes() == null
                || attestation.scopes().size()
                        != IndependentBoardConnectorBindingService.REQUIRED_SCOPES.size()
                || new HashSet<>(attestation.scopes()).size() != attestation.scopes().size()
                || !new HashSet<>(attestation.scopes()).equals(
                        IndependentBoardConnectorBindingService.REQUIRED_SCOPES)
                || attestation.validUntil() == null
                || !attestation.validUntil().after(new Date())) {
            throw new ServiceException("CONNECTOR_ATTESTATION_INVALID", 400);
        }
    }

    private void insertReceipt(
            BoardConnectorBinding binding,
            Long actorUserId,
            Date createdAt) {
        String action = "CONNECTOR_BINDING_VERIFIED";
        BoardConnectorBindingReceipt receipt = new BoardConnectorBindingReceipt();
        receipt.setReceiptId(UUID.randomUUID().toString());
        receipt.setBindingId(binding.getBindingId());
        receipt.setTenantId(binding.getTenantId());
        receipt.setMemberId(binding.getMemberId());
        receipt.setUserId(binding.getUserId());
        receipt.setActorUserId(actorUserId);
        receipt.setAction(action);
        receipt.setPayloadDigest(bindingDigest(binding, actorUserId, action));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(createdAt);
        if (mapper.insertConnectorBindingReceipt(receipt) != 1) {
            throw new ServiceException("BOARD_CONNECTOR_BINDING_RECEIPT_WRITE_FAILED", 500);
        }
    }

    private String bindingDigest(
            BoardConnectorBinding binding,
            Long actorUserId,
            String action) {
        String canonical = String.join("\n",
                binding.getBindingId(),
                String.valueOf(binding.getTenantId()),
                String.valueOf(binding.getMemberId()),
                String.valueOf(binding.getUserId()),
                binding.getProductCode(),
                binding.getSourceCode(),
                binding.getConnectorCode(),
                binding.getIssuerUri(),
                binding.getResourceUri(),
                binding.getClientId(),
                binding.getPrincipalSubjectDigest(),
                String.join(" ", binding.getScopes()),
                binding.getVerificationMethod(),
                binding.getEvidenceDigest(),
                binding.getStatus(),
                String.valueOf(binding.getVerifiedAt().getTime()),
                String.valueOf(binding.getLastSeenAt().getTime()),
                String.valueOf(binding.getValidUntil().getTime()),
                "",
                String.valueOf(binding.getVersion()),
                String.valueOf(actorUserId),
                action);
        return BoardDigest.sha256(canonical);
    }

    private BoardConnectorBindingSnapshot snapshot(BoardConnectorBinding binding) {
        return new BoardConnectorBindingSnapshot(
                binding.getBindingId(), binding.getTenantId(), binding.getMemberId(),
                binding.getUserId(), binding.getProductCode(), binding.getStatus(),
                new Date(binding.getVerifiedAt().getTime()),
                new Date(binding.getValidUntil().getTime()), binding.getVersion());
    }
}
