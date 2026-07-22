package com.wx.fbsir.business.board.plan.service;

import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyName;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyReceipt;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyAuditEnvelope;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionView;
import com.wx.fbsir.business.board.plan.mapper.IndependentBoardPlanPolicyMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializable-by-lock-order policy transition and immutable receipt verification. */
@Service
public class IndependentBoardPlanPolicyTransactionService {
    static final int AUDIT_LIMIT = 100;
    static final int AUDIT_FETCH_LIMIT = AUDIT_LIMIT + 1;
    private static final String ADMIN_ACTOR = "ADMIN_USER";
    private static final Pattern IDENTIFIER = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern IDEMPOTENCY = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}");

    private final IndependentBoardPlanPolicyMapper mapper;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    public IndependentBoardPlanPolicyTransactionService(
            IndependentBoardPlanPolicyMapper mapper) {
        this(mapper, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    IndependentBoardPlanPolicyTransactionService(
            IndependentBoardPlanPolicyMapper mapper,
            Clock clock,
            Supplier<String> idGenerator) {
        this.mapper = mapper;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            rollbackFor = Exception.class)
    public BoardPlanPolicyRevisionView replayIfPresent(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        return replay(request, actorUserId, false);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            rollbackFor = Exception.class)
    public BoardPlanPolicyRevisionView replayRequired(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        return replay(request, actorUserId, true);
    }

    private BoardPlanPolicyRevisionView replay(
            BoardPlanPolicyRevisionRequest request,
            Long actorUserId,
            boolean requireExisting) {
        BoardPlanPolicyRevisionRequest normalized = normalizeAndValidate(request, actorUserId);
        String idempotencyDigest = BoardPlanPolicyDigest.idempotencyKeyDigest(
                normalized.idempotencyKey());
        BoardPlanPolicyReceipt existing =
                mapper.selectReceiptByActorAndIdempotencyDigest(
                        IndependentBoardPlanPolicyService.PRODUCT_CODE,
                        ADMIN_ACTOR, actorUserId, idempotencyDigest);
        if (existing == null) {
            if (requireExisting) {
                throw new ServiceException(
                        "BOARD_PLAN_POLICY_UNEXPECTED_UNIQUE_CONFLICT", 500);
            }
            return null;
        }
        verifyExactReplay(existing, normalized, actorUserId, idempotencyDigest);
        return toView(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public BoardPlanPolicyRevisionView reviseFresh(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        BoardPlanPolicyRevisionRequest normalized = normalizeAndValidate(request, actorUserId);
        requireLockedPolicyHeads(mapper.selectPolicyHeadCodesForUpdate(
                IndependentBoardPlanPolicyService.PRODUCT_CODE));
        List<BoardPlanPolicySnapshot> catalog = requireCatalog(
                mapper.selectCurrentPolicies(IndependentBoardPlanPolicyService.PRODUCT_CODE));
        String idempotencyDigest = BoardPlanPolicyDigest.idempotencyKeyDigest(
                normalized.idempotencyKey());
        BoardPlanPolicyReceipt replay =
                mapper.selectReceiptByActorAndIdempotencyDigest(
                        IndependentBoardPlanPolicyService.PRODUCT_CODE,
                        ADMIN_ACTOR, actorUserId, idempotencyDigest);
        if (replay != null) {
            verifyExactReplay(replay, normalized, actorUserId, idempotencyDigest);
            return toView(replay);
        }

        BoardPlanPolicySnapshot current = catalog.stream()
                .filter(value -> normalized.planCode().equals(value.getPlanCode()))
                .findFirst()
                .orElseThrow(() -> new ServiceException(
                        "BOARD_PLAN_POLICY_CATALOG_DRIFT", 500));
        if (!Objects.equals(current.getPolicyVersion(), normalized.expectedVersion())) {
            throw new ServiceException("BOARD_PLAN_POLICY_VERSION_CONFLICT", 409);
        }
        if (samePolicy(current, normalized)) {
            throw new ServiceException("BOARD_PLAN_POLICY_NO_CHANGE", 409);
        }
        BoardPlanPolicyReceipt rollbackTarget = requireRollbackTarget(normalized);
        if (rollbackTarget != null && !samePolicy(rollbackTarget, normalized)) {
            throw new ServiceException("BOARD_PLAN_POLICY_ROLLBACK_TARGET_MISMATCH", 409);
        }
        requireCandidateCatalogInvariant(catalog, normalized);

        long nextVersion = safeNextVersion(current.getPolicyVersion());
        Date now = Date.from(clock.instant());
        BoardPlanPolicyReceipt next = nextReceipt(
                current, normalized, rollbackTarget, actorUserId,
                idempotencyDigest, nextVersion, now);
        mapper.transitionReceiptThroughControlledProcedure(next);
        BoardPlanPolicySnapshot committed = mapper.selectCurrentPolicy(
                next.getProductCode(), next.getPlanCode());
        verifyCommittedRead(committed, next);
        return toView(committed);
    }

    @Transactional(readOnly = true)
    public BoardPlanPolicyAuditEnvelope audit() {
        List<BoardPlanPolicyReceipt> rows = mapper.selectPolicyReceipts(
                IndependentBoardPlanPolicyService.PRODUCT_CODE, AUDIT_FETCH_LIMIT);
        if (rows == null || rows.size() > AUDIT_FETCH_LIMIT) {
            throw new ServiceException("BOARD_PLAN_POLICY_AUDIT_READ_FAILED", 500);
        }
        int resultSize = Math.min(rows.size(), AUDIT_LIMIT);
        List<BoardPlanPolicyRevisionView> records = new ArrayList<>(resultSize);
        for (int index = 0; index < rows.size(); index++) {
            BoardPlanPolicyReceipt receipt = rows.get(index);
            validateReceipt(receipt);
            if (index < resultSize) {
                records.add(toView(receipt));
            }
        }
        return new BoardPlanPolicyAuditEnvelope(
                List.copyOf(records), AUDIT_LIMIT, rows.size() > AUDIT_LIMIT);
    }

    private BoardPlanPolicyReceipt requireRollbackTarget(
            BoardPlanPolicyRevisionRequest request) {
        if (request.rollbackOfReceiptId() == null) {
            return null;
        }
        BoardPlanPolicyReceipt target = mapper.selectReceiptByReceiptId(
                IndependentBoardPlanPolicyService.PRODUCT_CODE,
                request.rollbackOfReceiptId());
        validateReceipt(target);
        if (!Objects.equals(target.getReceiptId(), request.rollbackOfReceiptId())
                || !Objects.equals(target.getProductCode(),
                    IndependentBoardPlanPolicyService.PRODUCT_CODE)
                || !Objects.equals(target.getPlanCode(), request.planCode())) {
            throw new ServiceException("BOARD_PLAN_POLICY_ROLLBACK_TARGET_INVALID", 409);
        }
        return target;
    }

    private BoardPlanPolicyReceipt nextReceipt(
            BoardPlanPolicySnapshot current,
            BoardPlanPolicyRevisionRequest request,
            BoardPlanPolicyReceipt rollbackTarget,
            Long actorUserId,
            String idempotencyDigest,
            long nextVersion,
            Date now) {
        BoardPlanPolicyReceipt next = new BoardPlanPolicyReceipt();
        next.setReceiptId(nextIdentifier());
        next.setProductCode(current.getProductCode());
        next.setPlanCode(current.getPlanCode());
        next.setPolicyVersion(nextVersion);
        next.setPreviousReceiptId(current.getReceiptId());
        next.setRollbackOfReceiptId(
                rollbackTarget == null ? null : rollbackTarget.getReceiptId());
        next.setAction(rollbackTarget == null
                ? "PLAN_POLICY_REVISED" : "PLAN_POLICY_ROLLED_BACK");
        next.setActorType(ADMIN_ACTOR);
        next.setActorUserId(actorUserId);
        next.setIdempotencyKeyDigest(idempotencyDigest);
        next.setCommandDigest(BoardPlanPolicyDigest.commandDigest(request, actorUserId));
        next.setPreviousPolicyDigest(current.getPolicyDigest());
        next.setPlanName(request.planName());
        next.setVip(current.getVip());
        next.setConnectorRequired(current.getConnectorRequired());
        next.setDailyMeetingLimit(request.dailyMeetingLimit());
        next.setAgendaLimit(request.agendaLimit());
        next.setSeatLimit(request.seatLimit());
        next.setSecretaryEnabled(request.secretaryEnabled());
        next.setStatus(current.getStatus());
        next.setEvidenceLevel("ACTION_COMPLETED");
        next.setCreatedAt(now);
        next.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(next));
        return next;
    }

    private List<BoardPlanPolicySnapshot> requireCatalog(
            List<BoardPlanPolicySnapshot> rows) {
        if (rows == null || rows.size() != 2
                || !IndependentBoardPlanPolicyService.FREE_PLAN.equals(rows.get(0).getPlanCode())
                || !IndependentBoardPlanPolicyService.VIP_PLAN.equals(rows.get(1).getPlanCode())) {
            throw new ServiceException("BOARD_PLAN_POLICY_CATALOG_DRIFT", 500);
        }
        validateSnapshot(rows.get(0), false, false);
        validateSnapshot(rows.get(1), true, true);
        requireCatalogInvariant(rows.get(0), rows.get(1));
        return rows;
    }

    private void validateSnapshot(
            BoardPlanPolicySnapshot value,
            boolean expectedVip,
            boolean expectedConnector) {
        validateReceipt(value);
        if (!Objects.equals(value.getIdentityProductCode(), value.getProductCode())
                || !Objects.equals(value.getIdentityPlanCode(), value.getPlanCode())
                || !Objects.equals(value.getIdentityVip(), expectedVip)
                || !Objects.equals(value.getIdentityConnectorRequired(), expectedConnector)
                || !Objects.equals(value.getIdentityStatus(), "ACTIVE")
                || !Objects.equals(value.getVip(), value.getIdentityVip())
                || !Objects.equals(
                        value.getConnectorRequired(), value.getIdentityConnectorRequired())
                || !Objects.equals(value.getStatus(), value.getIdentityStatus())) {
            throw new ServiceException("BOARD_PLAN_POLICY_IDENTITY_DRIFT", 500);
        }
    }

    private void validateReceipt(BoardPlanPolicyReceipt value) {
        boolean fixedIdentity = value != null
                && ((IndependentBoardPlanPolicyService.FREE_PLAN.equals(value.getPlanCode())
                    && Boolean.FALSE.equals(value.getVip())
                    && Boolean.FALSE.equals(value.getConnectorRequired()))
                || (IndependentBoardPlanPolicyService.VIP_PLAN.equals(value.getPlanCode())
                    && Boolean.TRUE.equals(value.getVip())
                    && Boolean.TRUE.equals(value.getConnectorRequired())));
        if (value == null
                || !IndependentBoardPlanPolicyService.PRODUCT_CODE.equals(value.getProductCode())
                || !(IndependentBoardPlanPolicyService.FREE_PLAN.equals(value.getPlanCode())
                    || IndependentBoardPlanPolicyService.VIP_PLAN.equals(value.getPlanCode()))
                || !fixedIdentity
                || !isIdentifier(value.getReceiptId())
                || value.getPolicyVersion() == null || value.getPolicyVersion() < 1L
                || !isDigest(value.getIdempotencyKeyDigest())
                || !isDigest(value.getCommandDigest())
                || !isDigest(value.getPolicyDigest())
                || value.getCreatedAt() == null
                || !"ACTIVE".equals(value.getStatus())
                || !"ACTION_COMPLETED".equals(value.getEvidenceLevel())
                || !validName(value.getPlanName())
                || !validLimits(value)
                || !BoardPlanPolicyDigest.equal(
                        value.getPolicyDigest(), BoardPlanPolicyDigest.policyDigest(value))) {
            throw new ServiceException("BOARD_PLAN_POLICY_RECEIPT_DRIFT", 500);
        }
        if (value.getPolicyVersion() == 1L) {
            if (!"PLAN_POLICY_BASELINED".equals(value.getAction())
                    || !"SYSTEM_MIGRATION".equals(value.getActorType())
                    || value.getActorUserId() != null
                    || value.getPreviousReceiptId() != null
                    || value.getPreviousPolicyDigest() != null
                    || value.getRollbackOfReceiptId() != null) {
                throw new ServiceException("BOARD_PLAN_POLICY_RECEIPT_DRIFT", 500);
            }
        } else if (!ADMIN_ACTOR.equals(value.getActorType())
                || value.getActorUserId() == null || value.getActorUserId() <= 0L
                || !isIdentifier(value.getPreviousReceiptId())
                || !isDigest(value.getPreviousPolicyDigest())
                || !("PLAN_POLICY_REVISED".equals(value.getAction())
                    || "PLAN_POLICY_ROLLED_BACK".equals(value.getAction()))
                || ("PLAN_POLICY_REVISED".equals(value.getAction())
                    && value.getRollbackOfReceiptId() != null)
                || ("PLAN_POLICY_ROLLED_BACK".equals(value.getAction())
                    && !isIdentifier(value.getRollbackOfReceiptId()))) {
            throw new ServiceException("BOARD_PLAN_POLICY_RECEIPT_DRIFT", 500);
        }
    }

    private void requireCandidateCatalogInvariant(
            List<BoardPlanPolicySnapshot> catalog,
            BoardPlanPolicyRevisionRequest request) {
        BoardPlanPolicyReceipt free = IndependentBoardPlanPolicyService.FREE_PLAN
                .equals(request.planCode()) ? candidate(catalog.get(0), request) : catalog.get(0);
        BoardPlanPolicyReceipt vip = IndependentBoardPlanPolicyService.VIP_PLAN
                .equals(request.planCode()) ? candidate(catalog.get(1), request) : catalog.get(1);
        requireCatalogInvariant(free, vip);
    }

    private static BoardPlanPolicyReceipt candidate(
            BoardPlanPolicyReceipt identity,
            BoardPlanPolicyRevisionRequest request) {
        BoardPlanPolicyReceipt value = new BoardPlanPolicyReceipt();
        value.copyFrom(identity);
        value.setPlanName(request.planName());
        value.setDailyMeetingLimit(request.dailyMeetingLimit());
        value.setAgendaLimit(request.agendaLimit());
        value.setSeatLimit(request.seatLimit());
        value.setSecretaryEnabled(request.secretaryEnabled());
        return value;
    }

    private static void requireCatalogInvariant(
            BoardPlanPolicyReceipt free, BoardPlanPolicyReceipt vip) {
        boolean seatsValid = vip.getSeatLimit() == null
                || vip.getSeatLimit() >= free.getSeatLimit();
        if (vip.getDailyMeetingLimit() < free.getDailyMeetingLimit()
                || vip.getAgendaLimit() < free.getAgendaLimit()
                || !seatsValid
                || (Boolean.TRUE.equals(free.getSecretaryEnabled())
                    && !Boolean.TRUE.equals(vip.getSecretaryEnabled()))) {
            throw new ServiceException("BOARD_PLAN_POLICY_CATALOG_INVARIANT", 409);
        }
    }

    private void verifyExactReplay(
            BoardPlanPolicyReceipt receipt,
            BoardPlanPolicyRevisionRequest request,
            Long actorUserId,
            String idempotencyDigest) {
        validateReceipt(receipt);
        String commandDigest = BoardPlanPolicyDigest.commandDigest(request, actorUserId);
        if (!Objects.equals(receipt.getActorType(), ADMIN_ACTOR)
                || !Objects.equals(receipt.getActorUserId(), actorUserId)
                || !BoardPlanPolicyDigest.equal(
                        receipt.getIdempotencyKeyDigest(), idempotencyDigest)
                || !BoardPlanPolicyDigest.equal(receipt.getCommandDigest(), commandDigest)) {
            throw new ServiceException("BOARD_PLAN_POLICY_IDEMPOTENCY_CONFLICT", 409);
        }
    }

    private void verifyCommittedRead(
            BoardPlanPolicySnapshot committed, BoardPlanPolicyReceipt expected) {
        validateSnapshot(
                committed,
                IndependentBoardPlanPolicyService.VIP_PLAN.equals(expected.getPlanCode()),
                IndependentBoardPlanPolicyService.VIP_PLAN.equals(expected.getPlanCode()));
        if (!Objects.equals(committed.getReceiptId(), expected.getReceiptId())
                || !Objects.equals(committed.getPolicyVersion(), expected.getPolicyVersion())
                || !BoardPlanPolicyDigest.equal(
                        committed.getPolicyDigest(), expected.getPolicyDigest())
                || !BoardPlanPolicyDigest.equal(
                        committed.getCommandDigest(), expected.getCommandDigest())) {
            throw new ServiceException("BOARD_PLAN_POLICY_COMMITTED_READ_DRIFT", 500);
        }
    }

    private static BoardPlanPolicyRevisionRequest normalizeAndValidate(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        if (request == null || actorUserId == null || actorUserId <= 0L
                || !(IndependentBoardPlanPolicyService.FREE_PLAN.equals(request.planCode())
                    || IndependentBoardPlanPolicyService.VIP_PLAN.equals(request.planCode()))
                || request.expectedVersion() == null || request.expectedVersion() < 1L
                || request.expectedVersion() == Long.MAX_VALUE
                || request.planName() == null
                || request.dailyMeetingLimit() == null
                || request.agendaLimit() == null
                || request.secretaryEnabled() == null
                || !IDEMPOTENCY.matcher(
                        Objects.toString(request.idempotencyKey(), "")).matches()
                || (request.rollbackOfReceiptId() != null
                    && !isIdentifier(request.rollbackOfReceiptId()))) {
            throw new ServiceException("BOARD_PLAN_POLICY_REQUEST_INVALID", 400);
        }
        String name = request.planName();
        if (!validName(name)
                || request.dailyMeetingLimit() < 1 || request.dailyMeetingLimit() > 10_000
                || request.agendaLimit() < 1 || request.agendaLimit() > 30
                || (request.seatLimit() != null
                    && (request.seatLimit() < 1 || request.seatLimit() > 100))
                || (IndependentBoardPlanPolicyService.FREE_PLAN.equals(request.planCode())
                    && request.seatLimit() == null)) {
            throw new ServiceException("BOARD_PLAN_POLICY_REQUEST_INVALID", 400);
        }
        return new BoardPlanPolicyRevisionRequest(
                request.planCode(), request.expectedVersion(), name,
                request.dailyMeetingLimit(), request.agendaLimit(), request.seatLimit(),
                request.secretaryEnabled(), request.rollbackOfReceiptId(),
                request.idempotencyKey());
    }

    private static void requireLockedPolicyHeads(List<String> lockedPlanCodes) {
        if (!List.of(
                IndependentBoardPlanPolicyService.FREE_PLAN,
                IndependentBoardPlanPolicyService.VIP_PLAN).equals(lockedPlanCodes)) {
            throw new ServiceException("BOARD_PLAN_POLICY_HEAD_LOCK_DRIFT", 500);
        }
    }

    private static boolean validLimits(BoardPlanPolicyReceipt value) {
        return value.getDailyMeetingLimit() != null
                && value.getDailyMeetingLimit() >= 1
                && value.getDailyMeetingLimit() <= 10_000
                && value.getAgendaLimit() != null
                && value.getAgendaLimit() >= 1
                && value.getAgendaLimit() <= 30
                && (value.getSeatLimit() == null
                    || (value.getSeatLimit() >= 1 && value.getSeatLimit() <= 100))
                && (!IndependentBoardPlanPolicyService.FREE_PLAN.equals(value.getPlanCode())
                    || value.getSeatLimit() != null)
                && value.getVip() != null
                && value.getConnectorRequired() != null
                && value.getSecretaryEnabled() != null;
    }

    private static boolean validName(String value) {
        return BoardPlanPolicyName.isValid(value);
    }

    private static boolean samePolicy(
            BoardPlanPolicyReceipt current,
            BoardPlanPolicyRevisionRequest request) {
        return Objects.equals(current.getPlanName(), request.planName())
                && Objects.equals(
                        current.getDailyMeetingLimit(), request.dailyMeetingLimit())
                && Objects.equals(current.getAgendaLimit(), request.agendaLimit())
                && Objects.equals(current.getSeatLimit(), request.seatLimit())
                && Objects.equals(
                        current.getSecretaryEnabled(), request.secretaryEnabled());
    }

    private static BoardPlanPolicyRevisionView toView(BoardPlanPolicyReceipt value) {
        return new BoardPlanPolicyRevisionView(
                value.getReceiptId(), value.getPlanCode(), value.getPolicyVersion(),
                value.getPreviousReceiptId(), value.getRollbackOfReceiptId(),
                value.getAction(), value.getActorType(), value.getActorUserId(),
                value.getPlanName(), value.getDailyMeetingLimit(), value.getAgendaLimit(),
                value.getSeatLimit(), value.getSecretaryEnabled(),
                value.getPreviousPolicyDigest(), value.getPolicyDigest(),
                value.getEvidenceLevel(), value.getCreatedAt());
    }

    private String nextIdentifier() {
        String value = idGenerator.get();
        if (!isIdentifier(value)) {
            throw new ServiceException("BOARD_PLAN_POLICY_IDENTIFIER_FAILED", 500);
        }
        return value;
    }

    private static long safeNextVersion(long current) {
        if (current < 1L || current == Long.MAX_VALUE) {
            throw new ServiceException("BOARD_PLAN_POLICY_VERSION_EXHAUSTED", 409);
        }
        return current + 1L;
    }

    private static boolean isIdentifier(String value) {
        return value != null && IDENTIFIER.matcher(value).matches();
    }

    private static boolean isDigest(String value) {
        return value != null && SHA256.matcher(value).matches();
    }
}
