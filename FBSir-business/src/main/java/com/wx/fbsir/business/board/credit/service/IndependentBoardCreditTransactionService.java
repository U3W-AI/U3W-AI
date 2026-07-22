package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditAuditRow;
import com.wx.fbsir.business.board.credit.domain.BoardCreditChainProof;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditOperation;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditEnvelope;
import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditRecord;
import com.wx.fbsir.business.board.credit.dto.BoardCreditCommandResult;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Transaction boundary for fresh immutable claims and post-rollback idempotent replays. */
@Service
public class IndependentBoardCreditTransactionService {
    static final String ZERO_HASH = "0".repeat(64);
    static final long MAX_COMPATIBLE_BALANCE = Integer.MAX_VALUE;
    static final int AUDIT_LIMIT = 100;
    static final int AUDIT_FETCH_LIMIT = AUDIT_LIMIT + 1;
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern SHA256_PATTERN = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern IDEMPOTENCY_PATTERN = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._:-]{15,127}");
    private static final Pattern GRANT_REASON_PATTERN = Pattern.compile(
            "CUSTOMER_SUPPORT|SERVICE_RECOVERY|MIGRATION_CORRECTION");
    private static final Pattern REVERSAL_REASON_PATTERN = Pattern.compile(
            "DUPLICATE_GRANT|OPERATOR_ERROR|POLICY_VIOLATION");

    private final IndependentBoardCreditMapper mapper;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    public IndependentBoardCreditTransactionService(IndependentBoardCreditMapper mapper) {
        this(mapper, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    IndependentBoardCreditTransactionService(
            IndependentBoardCreditMapper mapper, Clock clock, Supplier<String> idGenerator) {
        this.mapper = mapper;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** DuplicateKeyException deliberately escapes so the whole fresh transaction rolls back. */
    @Transactional(rollbackFor = Exception.class)
    public BoardCreditCommandResult grantFresh(
            BoardCreditGrantRequest request, Long actorUserId) {
        validateGrant(request, actorUserId);
        BoardCreditUserProjection user = lockUser(request.userId(), true);
        long projectedBalance = projectionBalance(user);
        Date now = Date.from(clock.instant());
        BoardCreditAccount account = lockOrOpenAccount(user.getUserId(), projectedBalance, now);
        requireExpectedAccountVersion(account, request.expectedAccountVersion());
        requireProjectionMatch(account, projectedBalance);

        long nextBalance = safeBalanceAdd(account.getBalance(), request.amount().longValue());
        String requestDigest = BoardCreditDigest.grantDigest(request, actorUserId);
        BoardCreditOperation operation = grantOperation(
                request, actorUserId, account, requestDigest, nextBalance, now);
        BoardCreditEntry entry = entry(operation, account, now);
        persistTransition(account, operation, entry, projectedBalance, nextBalance, now);
        return result(operation);
    }

    /**
     * Returns an already committed exact replay before any mutable user or account
     * state is consulted. Null means the idempotency key is not committed yet.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            rollbackFor = Exception.class)
    public BoardCreditCommandResult replayGrantIfPresent(
            BoardCreditGrantRequest request, Long actorUserId) {
        return replayGrantInternal(request, actorUserId, false);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            rollbackFor = Exception.class)
    public BoardCreditCommandResult replayGrant(
            BoardCreditGrantRequest request, Long actorUserId) {
        return replayGrantInternal(request, actorUserId, true);
    }

    private BoardCreditCommandResult replayGrantInternal(
            BoardCreditGrantRequest request, Long actorUserId, boolean requireExisting) {
        validateGrant(request, actorUserId);
        BoardCreditOperation existing = mapper.selectOperationByIdempotencyKey(
                request.idempotencyKey());
        if (existing == null && !requireExisting) {
            return null;
        }
        String digest = BoardCreditDigest.grantDigest(request, actorUserId);
        verifyGrantReplay(existing, request, actorUserId, digest);
        verifyEntry(existing, mapper.selectEntryByOperationId(existing.getOperationId()));
        return result(existing);
    }

    /** Reversal target and amount come only from the immutable original grant. */
    @Transactional(rollbackFor = Exception.class)
    public BoardCreditCommandResult reverseFresh(
            BoardCreditReversalRequest request, Long actorUserId) {
        validateReversal(request, actorUserId);
        BoardCreditOperation observed = requireReversibleGrant(
                mapper.selectOperationByOperationId(request.originalOperationId()));
        BoardCreditUserProjection user = lockUser(observed.getUserId(), false);
        long projectedBalance = projectionBalance(user);
        BoardCreditAccount account = lockExistingAccount(user.getUserId());
        requireExpectedAccountVersion(account, request.expectedAccountVersion());
        requireProjectionMatch(account, projectedBalance);
        BoardCreditOperation original = requireReversibleGrant(
                mapper.selectOperationByOperationIdForUpdate(request.originalOperationId()));
        requireSameOriginal(observed, original, account);
        BoardCreditEntry originalEntry = mapper.selectEntryByOperationId(
                original.getOperationId());
        verifyEntry(original, originalEntry);
        verifyCommittedMembership(account, originalEntry);

        long delta = -original.getDelta();
        long nextBalance = safeBalanceAdd(account.getBalance(), delta);
        Date now = Date.from(clock.instant());
        String requestDigest = BoardCreditDigest.reversalDigest(
                request, actorUserId, original.getUserId(), delta);
        BoardCreditOperation reversal = reversalOperation(
                request, actorUserId, original, account, requestDigest, nextBalance, now);
        BoardCreditEntry entry = entry(reversal, account, now);
        persistTransition(account, reversal, entry, projectedBalance, nextBalance, now);
        return result(reversal);
    }

    /**
     * Returns an already committed exact reversal replay before any mutable user
     * or account state is consulted. Null means the idempotency key is absent.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            rollbackFor = Exception.class)
    public BoardCreditCommandResult replayReversalIfPresent(
            BoardCreditReversalRequest request, Long actorUserId) {
        return replayReversalInternal(request, actorUserId, false);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            readOnly = true,
            rollbackFor = Exception.class)
    public BoardCreditCommandResult replayReversal(
            BoardCreditReversalRequest request, Long actorUserId) {
        return replayReversalInternal(request, actorUserId, true);
    }

    private BoardCreditCommandResult replayReversalInternal(
            BoardCreditReversalRequest request, Long actorUserId, boolean requireExisting) {
        validateReversal(request, actorUserId);
        BoardCreditOperation existing = mapper.selectOperationByIdempotencyKey(
                request.idempotencyKey());
        if (existing == null) {
            if (!requireExisting) {
                return null;
            }
            BoardCreditOperation winner = mapper.selectReversalByOriginalOperationId(
                    request.originalOperationId());
            if (winner != null) {
                throw new ServiceException("CREDIT_OPERATION_ALREADY_REVERSED", 409);
            }
            throw new ServiceException("CREDIT_LEDGER_UNEXPECTED_UNIQUE_CONFLICT", 500);
        }
        if (!Objects.equals(existing.getOperationType(), "REVERSAL")
                || !Objects.equals(existing.getIdempotencyKey(), request.idempotencyKey())
                || !Objects.equals(
                        existing.getReversalOfOperationId(), request.originalOperationId())) {
            throw new ServiceException("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        BoardCreditOperation original = requireReversibleGrant(
                mapper.selectOperationByOperationId(request.originalOperationId()));
        verifyEntry(original, mapper.selectEntryByOperationId(original.getOperationId()));
        long delta = -original.getDelta();
        String digest = BoardCreditDigest.reversalDigest(
                request, actorUserId, original.getUserId(), delta);
        verifyReversalReplay(existing, request, actorUserId, original, digest);
        verifyEntry(existing, mapper.selectEntryByOperationId(existing.getOperationId()));
        return result(existing);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public BoardCreditAuditEnvelope audit(Long userId) {
        requirePositive(userId, "CREDIT_USER_REQUIRED");
        BoardCreditAccount account = mapper.selectAccount(
                userId, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE);
        if (account == null) {
            throw new ServiceException("CREDIT_ACCOUNT_NOT_FOUND", 404);
        }
        validateAccount(account, userId);
        List<BoardCreditAuditRow> rows = mapper.selectAuditRowsByAccountId(
                account.getAccountId());
        if (rows == null || rows.size() > AUDIT_FETCH_LIMIT || rows.isEmpty()) {
            throw new ServiceException("CREDIT_AUDIT_CURRENT_READ_FAILED", 500);
        }
        verifyAuditChain(account, rows);
        boolean truncated = rows.size() > AUDIT_LIMIT;
        int resultSize = Math.min(rows.size(), AUDIT_LIMIT);
        List<BoardCreditAuditRecord> records = new ArrayList<>(resultSize);
        for (int index = 0; index < resultSize; index++) {
            BoardCreditAuditRow row = rows.get(index);
            records.add(new BoardCreditAuditRecord(
                    row.getOperationId(), row.getIdempotencyKey(), row.getRequestDigest(),
                    row.getOperationType(), row.getDelta(), row.getReasonCode(),
                    row.getActorUserId(), row.getReversalOfOperationId(),
                    row.getBalanceBefore(), row.getBalanceAfter(), row.getSequenceNo(),
                    row.getPreviousEntryHash(), row.getEntryHash(), row.getCreatedAt()));
        }
        return new BoardCreditAuditEnvelope(
                account.getAccountId(), account.getUserId(), account.getAccountScope(),
                account.getCurrencyCode(), account.getOpeningBalance(), account.getBalance(),
                account.getVersion(), account.getLastEntryHash(), account.getUpdatedAt(),
                List.copyOf(records), AUDIT_LIMIT, truncated);
    }

    private void persistTransition(
            BoardCreditAccount current,
            BoardCreditOperation operation,
            BoardCreditEntry entry,
            long projectedBalance,
            long nextBalance,
            Date now) {
        if (mapper.insertOperation(operation) != 1) {
            throw new ServiceException("CREDIT_OPERATION_WRITE_FAILED", 500);
        }
        if (mapper.insertEntry(entry) != 1) {
            throw new ServiceException("CREDIT_ENTRY_WRITE_FAILED", 500);
        }
        BoardCreditAccount next = nextAccount(current, nextBalance, entry.getEntryHash(), now);
        if (mapper.updateAccountIfVersion(
                next, current.getVersion(), current.getLastEntrySequence(),
                current.getBalance(), current.getLastEntryHash()) != 1) {
            throw new ServiceException("CREDIT_ACCOUNT_VERSION_CONFLICT", 409);
        }
        if (mapper.updateUserProjectionIfBalance(
                current.getUserId(), projectedBalance, nextBalance) != 1) {
            throw new ServiceException("CREDIT_USER_PROJECTION_CONFLICT", 409);
        }
    }

    private BoardCreditAccount lockOrOpenAccount(Long userId, long openingBalance, Date now) {
        BoardCreditAccount account = mapper.selectAccountForUpdate(
                userId, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE);
        if (account != null) {
            validateAccount(account, userId);
            return account;
        }
        BoardCreditAccount created = new BoardCreditAccount();
        created.setAccountId(nextId());
        created.setUserId(userId);
        created.setAccountScope(IndependentBoardCreditService.ACCOUNT_SCOPE);
        created.setCurrencyCode(IndependentBoardCreditService.CURRENCY_CODE);
        created.setOpeningBalance(openingBalance);
        created.setBalance(openingBalance);
        created.setVersion(0L);
        created.setLastEntrySequence(0L);
        created.setLastEntryHash(ZERO_HASH);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        if (mapper.insertAccount(created) != 1) {
            throw new ServiceException("CREDIT_ACCOUNT_WRITE_FAILED", 500);
        }
        return created;
    }

    private BoardCreditAccount lockExistingAccount(Long userId) {
        BoardCreditAccount account = mapper.selectAccountForUpdate(
                userId, IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE);
        validateAccount(account, userId);
        return account;
    }

    private BoardCreditUserProjection lockUser(Long userId, boolean requireActive) {
        BoardCreditUserProjection user = mapper.selectUserProjectionForUpdate(userId);
        if (user == null || !Objects.equals(user.getUserId(), userId)
                || !Objects.equals(user.getDelFlag(), "0")) {
            throw new ServiceException("CREDIT_TARGET_USER_NOT_FOUND", 404);
        }
        if (requireActive && !Objects.equals(user.getStatus(), "0")) {
            throw new ServiceException("CREDIT_TARGET_USER_INACTIVE", 409);
        }
        if (user.getPoints() != null && user.getPoints() < 0) {
            throw new ServiceException("CREDIT_USER_PROJECTION_DRIFT", 409);
        }
        return user;
    }

    private long projectionBalance(BoardCreditUserProjection user) {
        return user.getPoints() == null ? 0L : user.getPoints().longValue();
    }

    private void requireProjectionMatch(BoardCreditAccount account, long projectedBalance) {
        if (!Objects.equals(account.getBalance(), projectedBalance)) {
            throw new ServiceException("CREDIT_PROJECTION_DRIFT", 409);
        }
    }

    private BoardCreditOperation grantOperation(
            BoardCreditGrantRequest request,
            Long actorUserId,
            BoardCreditAccount account,
            String requestDigest,
            long nextBalance,
            Date now) {
        BoardCreditOperation operation = baseOperation(
                account, request.idempotencyKey(), requestDigest, request.reasonCode(),
                request.note(), actorUserId, request.amount().longValue(), nextBalance, now);
        operation.setOperationType("GRANT");
        return operation;
    }

    private BoardCreditOperation reversalOperation(
            BoardCreditReversalRequest request,
            Long actorUserId,
            BoardCreditOperation original,
            BoardCreditAccount account,
            String requestDigest,
            long nextBalance,
            Date now) {
        BoardCreditOperation operation = baseOperation(
                account, request.idempotencyKey(), requestDigest, request.reasonCode(),
                request.note(), actorUserId, -original.getDelta(), nextBalance, now);
        operation.setOperationType("REVERSAL");
        operation.setReversalOfOperationId(original.getOperationId());
        return operation;
    }

    private BoardCreditOperation baseOperation(
            BoardCreditAccount account,
            String idempotencyKey,
            String requestDigest,
            String reasonCode,
            String reasonNote,
            Long actorUserId,
            long delta,
            long nextBalance,
            Date now) {
        BoardCreditOperation operation = new BoardCreditOperation();
        operation.setOperationId(nextId());
        operation.setIdempotencyKey(idempotencyKey);
        operation.setRequestDigest(requestDigest);
        operation.setAccountId(account.getAccountId());
        operation.setUserId(account.getUserId());
        operation.setAccountScope(account.getAccountScope());
        operation.setCurrencyCode(account.getCurrencyCode());
        operation.setDelta(delta);
        operation.setReasonCode(reasonCode);
        operation.setReasonNote(reasonNote);
        operation.setActorUserId(actorUserId);
        operation.setBalanceBefore(account.getBalance());
        operation.setBalanceAfter(nextBalance);
        operation.setCreatedAt(now);
        return operation;
    }

    private BoardCreditEntry entry(
            BoardCreditOperation operation, BoardCreditAccount account, Date now) {
        BoardCreditEntry entry = new BoardCreditEntry();
        entry.setEntryId(nextId());
        entry.setOperationId(operation.getOperationId());
        entry.setRequestDigest(operation.getRequestDigest());
        entry.setAccountId(account.getAccountId());
        entry.setSequenceNo(safeNextVersion(account.getLastEntrySequence()));
        entry.setDelta(operation.getDelta());
        entry.setBalanceBefore(operation.getBalanceBefore());
        entry.setBalanceAfter(operation.getBalanceAfter());
        entry.setPreviousEntryHash(account.getLastEntryHash());
        entry.setCreatedAt(now);
        entry.setEntryHash(BoardCreditDigest.entryHash(entry));
        return entry;
    }

    private BoardCreditAccount nextAccount(
            BoardCreditAccount current, long nextBalance, String nextHash, Date now) {
        BoardCreditAccount next = new BoardCreditAccount();
        next.setId(current.getId());
        next.setAccountId(current.getAccountId());
        next.setUserId(current.getUserId());
        next.setAccountScope(current.getAccountScope());
        next.setCurrencyCode(current.getCurrencyCode());
        next.setOpeningBalance(current.getOpeningBalance());
        next.setBalance(nextBalance);
        next.setVersion(safeNextVersion(current.getVersion()));
        next.setLastEntrySequence(safeNextVersion(current.getLastEntrySequence()));
        next.setLastEntryHash(nextHash);
        next.setCreatedAt(current.getCreatedAt());
        next.setUpdatedAt(now);
        return next;
    }

    private void verifyGrantReplay(
            BoardCreditOperation existing,
            BoardCreditGrantRequest request,
            Long actorUserId,
            String digest) {
        if (existing == null) {
            throw new ServiceException("CREDIT_LEDGER_UNEXPECTED_UNIQUE_CONFLICT", 500);
        }
        boolean exact = Objects.equals(existing.getOperationType(), "GRANT")
                && UUID_PATTERN.matcher(String.valueOf(existing.getOperationId())).matches()
                && Objects.equals(existing.getIdempotencyKey(), request.idempotencyKey())
                && BoardCreditDigest.equal(existing.getRequestDigest(), digest)
                && UUID_PATTERN.matcher(String.valueOf(existing.getAccountId())).matches()
                && Objects.equals(existing.getUserId(), request.userId())
                && Objects.equals(existing.getAccountScope(), IndependentBoardCreditService.ACCOUNT_SCOPE)
                && Objects.equals(existing.getCurrencyCode(), IndependentBoardCreditService.CURRENCY_CODE)
                && Objects.equals(existing.getDelta(), request.amount().longValue())
                && Objects.equals(existing.getReasonCode(), request.reasonCode())
                && Objects.equals(existing.getReasonNote(), request.note())
                && Objects.equals(existing.getActorUserId(), actorUserId)
                && existing.getReversalOfOperationId() == null;
        if (!exact) {
            throw new ServiceException("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        validateOperationBalance(existing);
    }

    private void verifyReversalReplay(
            BoardCreditOperation existing,
            BoardCreditReversalRequest request,
            Long actorUserId,
            BoardCreditOperation original,
            String digest) {
        boolean exact = Objects.equals(existing.getOperationType(), "REVERSAL")
                && UUID_PATTERN.matcher(String.valueOf(existing.getOperationId())).matches()
                && Objects.equals(existing.getIdempotencyKey(), request.idempotencyKey())
                && BoardCreditDigest.equal(existing.getRequestDigest(), digest)
                && Objects.equals(existing.getAccountId(), original.getAccountId())
                && Objects.equals(existing.getUserId(), original.getUserId())
                && Objects.equals(existing.getAccountScope(), IndependentBoardCreditService.ACCOUNT_SCOPE)
                && Objects.equals(existing.getCurrencyCode(), IndependentBoardCreditService.CURRENCY_CODE)
                && Objects.equals(existing.getDelta(), -original.getDelta())
                && Objects.equals(existing.getReasonCode(), request.reasonCode())
                && Objects.equals(existing.getReasonNote(), request.note())
                && Objects.equals(existing.getActorUserId(), actorUserId)
                && Objects.equals(existing.getReversalOfOperationId(), original.getOperationId());
        if (!exact) {
            throw new ServiceException("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
        validateOperationBalance(existing);
    }

    private BoardCreditOperation requireReversibleGrant(BoardCreditOperation operation) {
        if (operation == null) {
            throw new ServiceException("CREDIT_ORIGINAL_OPERATION_NOT_FOUND", 404);
        }
        boolean exact = UUID_PATTERN.matcher(String.valueOf(operation.getOperationId())).matches()
                && UUID_PATTERN.matcher(String.valueOf(operation.getAccountId())).matches()
                && Objects.equals(operation.getOperationType(), "GRANT")
                && Objects.equals(operation.getAccountScope(), IndependentBoardCreditService.ACCOUNT_SCOPE)
                && Objects.equals(operation.getCurrencyCode(), IndependentBoardCreditService.CURRENCY_CODE)
                && operation.getReversalOfOperationId() == null
                && operation.getUserId() != null && operation.getUserId() > 0L
                && operation.getDelta() != null && operation.getDelta() > 0L
                && operation.getDelta() <= 100_000L
                && validIdempotencyKey(operation.getIdempotencyKey())
                && GRANT_REASON_PATTERN.matcher(
                        String.valueOf(operation.getReasonCode())).matches()
                && validNote(operation.getReasonNote())
                && operation.getActorUserId() != null && operation.getActorUserId() > 0L
                && SHA256_PATTERN.matcher(String.valueOf(operation.getRequestDigest())).matches();
        if (!exact) {
            throw new ServiceException("CREDIT_ORIGINAL_OPERATION_NOT_REVERSIBLE", 409);
        }
        validateOperationBalance(operation);
        BoardCreditGrantRequest snapshot = new BoardCreditGrantRequest(
                operation.getUserId(), 0L, operation.getDelta().intValue(),
                operation.getReasonCode(), operation.getReasonNote(),
                operation.getIdempotencyKey());
        if (!BoardCreditDigest.equal(
                operation.getRequestDigest(),
                BoardCreditDigest.grantDigest(snapshot, operation.getActorUserId()))) {
            throw new ServiceException("CREDIT_ORIGINAL_OPERATION_DIGEST_DRIFT", 500);
        }
        return operation;
    }

    private void requireSameOriginal(
            BoardCreditOperation observed,
            BoardCreditOperation locked,
            BoardCreditAccount account) {
        boolean exact = Objects.equals(observed.getOperationId(), locked.getOperationId())
                && BoardCreditDigest.equal(observed.getRequestDigest(), locked.getRequestDigest())
                && Objects.equals(locked.getAccountId(), account.getAccountId())
                && Objects.equals(locked.getUserId(), account.getUserId())
                && Objects.equals(observed.getDelta(), locked.getDelta())
                && Objects.equals(observed.getIdempotencyKey(), locked.getIdempotencyKey())
                && Objects.equals(observed.getReasonCode(), locked.getReasonCode())
                && Objects.equals(observed.getReasonNote(), locked.getReasonNote())
                && Objects.equals(observed.getActorUserId(), locked.getActorUserId())
                && Objects.equals(observed.getBalanceBefore(), locked.getBalanceBefore())
                && Objects.equals(observed.getBalanceAfter(), locked.getBalanceAfter())
                && Objects.equals(observed.getCreatedAt(), locked.getCreatedAt());
        if (!exact) {
            throw new ServiceException("CREDIT_ORIGINAL_OPERATION_DRIFT", 500);
        }
    }

    private void verifyEntry(BoardCreditOperation operation, BoardCreditEntry entry) {
        boolean exact = entry != null
                && UUID_PATTERN.matcher(String.valueOf(entry.getEntryId())).matches()
                && Objects.equals(entry.getOperationId(), operation.getOperationId())
                && BoardCreditDigest.equal(entry.getRequestDigest(), operation.getRequestDigest())
                && Objects.equals(entry.getAccountId(), operation.getAccountId())
                && entry.getSequenceNo() != null && entry.getSequenceNo() > 0L
                && Objects.equals(entry.getDelta(), operation.getDelta())
                && Objects.equals(entry.getBalanceBefore(), operation.getBalanceBefore())
                && Objects.equals(entry.getBalanceAfter(), operation.getBalanceAfter())
                && SHA256_PATTERN.matcher(String.valueOf(entry.getPreviousEntryHash())).matches()
                && SHA256_PATTERN.matcher(String.valueOf(entry.getEntryHash())).matches()
                && entry.getCreatedAt() != null
                && Objects.equals(entry.getCreatedAt(), operation.getCreatedAt())
                && BoardCreditDigest.equal(entry.getEntryHash(), BoardCreditDigest.entryHash(entry));
        if (!exact) {
            throw new ServiceException("CREDIT_ENTRY_CONTRACT_DRIFT", 500);
        }
    }

    private void verifyCommittedMembership(
            BoardCreditAccount account, BoardCreditEntry originalEntry) {
        if (originalEntry.getSequenceNo() == null
                || originalEntry.getSequenceNo() <= 0L
                || originalEntry.getSequenceNo() > account.getLastEntrySequence()) {
            throw new ServiceException("CREDIT_ORIGINAL_ENTRY_NOT_COMMITTED", 500);
        }
        long expectedCount = account.getLastEntrySequence() - originalEntry.getSequenceNo() + 1L;
        BoardCreditChainProof proof = mapper.selectCommittedChainProof(
                account.getAccountId(), originalEntry.getSequenceNo(),
                account.getLastEntrySequence());
        boolean exact = proof != null
                && Objects.equals(proof.getEntryCount(), expectedCount)
                && Objects.equals(proof.getValidTransitionCount(), expectedCount)
                && Objects.equals(proof.getMinSequence(), originalEntry.getSequenceNo())
                && Objects.equals(proof.getMaxSequence(), account.getLastEntrySequence())
                && BoardCreditDigest.equal(proof.getHeadHash(), account.getLastEntryHash())
                && Objects.equals(proof.getHeadBalance(), account.getBalance());
        if (!exact) {
            throw new ServiceException("CREDIT_ORIGINAL_ENTRY_NOT_COMMITTED", 500);
        }
    }

    private void verifyAuditChain(BoardCreditAccount account, List<BoardCreditAuditRow> rows) {
        BoardCreditAuditRow newest = rows.get(0);
        if (newest == null
                || !Objects.equals(account.getVersion(), newest.getSequenceNo())
                || !Objects.equals(account.getLastEntrySequence(), newest.getSequenceNo())
                || !Objects.equals(account.getBalance(), newest.getBalanceAfter())
                || !BoardCreditDigest.equal(account.getLastEntryHash(), newest.getEntryHash())) {
            throw new ServiceException("CREDIT_AUDIT_CHAIN_DRIFT", 500);
        }
        long expectedRows = Math.min(account.getVersion(), (long) AUDIT_FETCH_LIMIT);
        if (rows.size() != expectedRows) {
            throw new ServiceException("CREDIT_AUDIT_CHAIN_DRIFT", 500);
        }
        for (int index = 0; index < rows.size(); index++) {
            BoardCreditAuditRow row = rows.get(index);
            long expectedSequence = account.getVersion() - index;
            boolean scoped = row != null
                    && Objects.equals(row.getAccountId(), account.getAccountId())
                    && Objects.equals(row.getUserId(), account.getUserId())
                    && Objects.equals(row.getAccountScope(), IndependentBoardCreditService.ACCOUNT_SCOPE)
                    && Objects.equals(row.getCurrencyCode(), IndependentBoardCreditService.CURRENCY_CODE)
                    && Objects.equals(row.getSequenceNo(), expectedSequence)
                    && UUID_PATTERN.matcher(String.valueOf(row.getEntryId())).matches()
                    && UUID_PATTERN.matcher(String.valueOf(row.getOperationId())).matches()
                    && SHA256_PATTERN.matcher(String.valueOf(row.getRequestDigest())).matches()
                    && SHA256_PATTERN.matcher(String.valueOf(row.getPreviousEntryHash())).matches()
                    && SHA256_PATTERN.matcher(String.valueOf(row.getEntryHash())).matches()
                    && row.getCreatedAt() != null
                    && row.getDelta() != null && row.getBalanceBefore() != null
                    && row.getBalanceAfter() != null
                    && BoardCreditDigest.equal(
                            row.getEntryRequestDigest(), row.getRequestDigest())
                    && Objects.equals(row.getEntryAccountId(), row.getAccountId())
                    && Objects.equals(row.getEntryDelta(), row.getDelta())
                    && Objects.equals(row.getEntryBalanceBefore(), row.getBalanceBefore())
                    && Objects.equals(row.getEntryBalanceAfter(), row.getBalanceAfter())
                    && Objects.equals(row.getEntryCreatedAt(), row.getCreatedAt())
                    && validAuditOperation(row)
                    && validStoredBalanceTransition(
                            row.getBalanceBefore(), row.getDelta(), row.getBalanceAfter());
            if (!scoped) {
                throw new ServiceException("CREDIT_AUDIT_ROW_DRIFT", 500);
            }
            BoardCreditEntry entry = new BoardCreditEntry();
            entry.setEntryId(row.getEntryId());
            entry.setOperationId(row.getOperationId());
            entry.setRequestDigest(row.getEntryRequestDigest());
            entry.setAccountId(row.getEntryAccountId());
            entry.setSequenceNo(row.getSequenceNo());
            entry.setDelta(row.getEntryDelta());
            entry.setBalanceBefore(row.getEntryBalanceBefore());
            entry.setBalanceAfter(row.getEntryBalanceAfter());
            entry.setPreviousEntryHash(row.getPreviousEntryHash());
            entry.setEntryHash(row.getEntryHash());
            entry.setCreatedAt(row.getEntryCreatedAt());
            if (!BoardCreditDigest.equal(row.getEntryHash(), BoardCreditDigest.entryHash(entry))) {
                throw new ServiceException("CREDIT_AUDIT_ENTRY_HASH_DRIFT", 500);
            }
            if (index + 1 < rows.size()
                    && (!BoardCreditDigest.equal(
                            row.getPreviousEntryHash(), rows.get(index + 1).getEntryHash())
                    || !Objects.equals(
                            row.getBalanceBefore(), rows.get(index + 1).getBalanceAfter()))) {
                throw new ServiceException("CREDIT_AUDIT_CHAIN_DRIFT", 500);
            }
            if (row.getSequenceNo() == 1L
                    && (!BoardCreditDigest.equal(row.getPreviousEntryHash(), ZERO_HASH)
                    || !Objects.equals(row.getBalanceBefore(), account.getOpeningBalance()))) {
                throw new ServiceException("CREDIT_AUDIT_GENESIS_DRIFT", 500);
            }
        }
    }

    private boolean validAuditOperation(BoardCreditAuditRow row) {
        if (!validIdempotencyKey(row.getIdempotencyKey())
                || !validNote(row.getReasonNote())
                || row.getActorUserId() == null || row.getActorUserId() <= 0L) {
            return false;
        }
        if (Objects.equals(row.getOperationType(), "GRANT")) {
            if (!(row.getDelta() > 0L && row.getDelta() <= 100_000L
                    && row.getReversalOfOperationId() == null
                    && row.getOriginalOperationType() == null
                    && row.getOriginalAccountId() == null
                    && row.getOriginalUserId() == null
                    && row.getOriginalDelta() == null
                    && GRANT_REASON_PATTERN.matcher(
                            String.valueOf(row.getReasonCode())).matches())) {
                return false;
            }
            BoardCreditGrantRequest snapshot = new BoardCreditGrantRequest(
                    row.getUserId(), 0L, row.getDelta().intValue(), row.getReasonCode(),
                    row.getReasonNote(), row.getIdempotencyKey());
            return BoardCreditDigest.equal(
                    row.getRequestDigest(),
                    BoardCreditDigest.grantDigest(snapshot, row.getActorUserId()));
        }
        if (!(Objects.equals(row.getOperationType(), "REVERSAL")
                && row.getDelta() < 0L && row.getDelta() >= -100_000L
                && UUID_PATTERN.matcher(String.valueOf(row.getReversalOfOperationId())).matches()
                && Objects.equals(row.getOriginalOperationType(), "GRANT")
                && Objects.equals(row.getOriginalAccountId(), row.getAccountId())
                && Objects.equals(row.getOriginalUserId(), row.getUserId())
                && row.getOriginalDelta() != null
                && row.getOriginalDelta() > 0L
                && row.getOriginalDelta() <= 100_000L
                && Objects.equals(row.getDelta(), -row.getOriginalDelta())
                && REVERSAL_REASON_PATTERN.matcher(
                        String.valueOf(row.getReasonCode())).matches())) {
            return false;
        }
        BoardCreditReversalRequest snapshot = new BoardCreditReversalRequest(
                row.getReversalOfOperationId(), 0L, row.getReasonCode(), row.getReasonNote(),
                row.getIdempotencyKey());
        return BoardCreditDigest.equal(
                row.getRequestDigest(),
                BoardCreditDigest.reversalDigest(
                        snapshot, row.getActorUserId(), row.getUserId(), row.getDelta()));
    }

    private boolean validStoredBalanceTransition(Long before, Long delta, Long after) {
        if (before == null || delta == null || after == null
                || before < 0L || before > MAX_COMPATIBLE_BALANCE
                || after < 0L || after > MAX_COMPATIBLE_BALANCE) {
            return false;
        }
        try {
            return Math.addExact(before, delta) == after;
        } catch (ArithmeticException overflow) {
            return false;
        }
    }

    private void validateAccount(BoardCreditAccount account, Long expectedUserId) {
        boolean valid = account != null
                && UUID_PATTERN.matcher(String.valueOf(account.getAccountId())).matches()
                && Objects.equals(account.getUserId(), expectedUserId)
                && Objects.equals(account.getAccountScope(), IndependentBoardCreditService.ACCOUNT_SCOPE)
                && Objects.equals(account.getCurrencyCode(), IndependentBoardCreditService.CURRENCY_CODE)
                && account.getOpeningBalance() != null && account.getOpeningBalance() >= 0L
                && account.getOpeningBalance() <= MAX_COMPATIBLE_BALANCE
                && account.getBalance() != null && account.getBalance() >= 0L
                && account.getBalance() <= MAX_COMPATIBLE_BALANCE
                && account.getVersion() != null && account.getVersion() >= 0L
                && account.getLastEntrySequence() != null
                && account.getLastEntrySequence() >= 0L
                && Objects.equals(account.getVersion(), account.getLastEntrySequence())
                && SHA256_PATTERN.matcher(String.valueOf(account.getLastEntryHash())).matches()
                && account.getCreatedAt() != null && account.getUpdatedAt() != null
                && !account.getUpdatedAt().before(account.getCreatedAt());
        if (!valid) {
            throw new ServiceException("CREDIT_ACCOUNT_CONTRACT_DRIFT", 500);
        }
        if (account.getVersion() == 0L
                && (!Objects.equals(account.getOpeningBalance(), account.getBalance())
                || !BoardCreditDigest.equal(account.getLastEntryHash(), ZERO_HASH))) {
            throw new ServiceException("CREDIT_ACCOUNT_GENESIS_DRIFT", 500);
        }
        if (account.getVersion() > 0L
                && BoardCreditDigest.equal(account.getLastEntryHash(), ZERO_HASH)) {
            throw new ServiceException("CREDIT_ACCOUNT_CHAIN_HEAD_DRIFT", 500);
        }
    }

    private void validateOperationBalance(BoardCreditOperation operation) {
        if (operation.getBalanceBefore() == null || operation.getBalanceAfter() == null
                || operation.getCreatedAt() == null
                || !validStoredBalanceTransition(
                        operation.getBalanceBefore(), operation.getDelta(),
                        operation.getBalanceAfter())) {
            throw new ServiceException("CREDIT_OPERATION_BALANCE_DRIFT", 500);
        }
    }

    private void validateGrant(BoardCreditGrantRequest request, Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        if (request == null || request.userId() == null || request.userId() <= 0L
                || request.expectedAccountVersion() == null
                || request.expectedAccountVersion() < 0L
                || request.amount() == null || request.amount() <= 0
                || request.amount() > 100_000
                || !GRANT_REASON_PATTERN.matcher(String.valueOf(request.reasonCode())).matches()
                || !validNote(request.note())
                || !validIdempotencyKey(request.idempotencyKey())) {
            throw new ServiceException("CREDIT_GRANT_COMMAND_INVALID", 400);
        }
    }

    private void validateReversal(BoardCreditReversalRequest request, Long actorUserId) {
        requirePositive(actorUserId, "AUTHENTICATED_PRINCIPAL_REQUIRED");
        if (request == null
                || !UUID_PATTERN.matcher(String.valueOf(request.originalOperationId())).matches()
                || request.expectedAccountVersion() == null
                || request.expectedAccountVersion() < 0L
                || !REVERSAL_REASON_PATTERN.matcher(String.valueOf(request.reasonCode())).matches()
                || !validNote(request.note())
                || !validIdempotencyKey(request.idempotencyKey())) {
            throw new ServiceException("CREDIT_REVERSAL_COMMAND_INVALID", 400);
        }
    }

    private boolean validIdempotencyKey(String value) {
        return value != null && IDEMPOTENCY_PATTERN.matcher(value).matches();
    }

    private boolean validNote(String value) {
        if (!StringUtils.hasText(value) || value.length() < 8 || value.length() > 128
                || !value.equals(value.trim())) {
            return false;
        }
        return value.codePoints().noneMatch(codePoint -> {
            int type = Character.getType(codePoint);
            return type == Character.CONTROL || type == Character.FORMAT;
        });
    }

    private long safeBalanceAdd(Long current, long delta) {
        if (current == null) {
            throw new ServiceException("CREDIT_ACCOUNT_CONTRACT_DRIFT", 500);
        }
        final long next;
        try {
            next = Math.addExact(current, delta);
        } catch (ArithmeticException overflow) {
            throw new ServiceException("CREDIT_BALANCE_OUT_OF_RANGE", 409);
        }
        if (next < 0L || next > MAX_COMPATIBLE_BALANCE) {
            if (delta < 0L) {
                throw new ServiceException("CREDIT_REVERSAL_INSUFFICIENT_BALANCE", 409);
            }
            throw new ServiceException("CREDIT_BALANCE_OUT_OF_RANGE", 409);
        }
        return next;
    }

    private void requireExpectedAccountVersion(
            BoardCreditAccount account, Long expectedAccountVersion) {
        if (account == null || !Objects.equals(account.getVersion(), expectedAccountVersion)) {
            throw new ServiceException("CREDIT_ACCOUNT_VERSION_CONFLICT", 409);
        }
    }

    private long safeNextVersion(Long current) {
        if (current == null || current < 0L || current == Long.MAX_VALUE) {
            throw new ServiceException("CREDIT_ACCOUNT_VERSION_OUT_OF_RANGE", 409);
        }
        return current + 1L;
    }

    private String nextId() {
        String value = idGenerator.get();
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw new ServiceException("CREDIT_IDENTIFIER_GENERATION_FAILED", 500);
        }
        return value;
    }

    private void requirePositive(Long value, String error) {
        if (value == null || value <= 0L) {
            throw new ServiceException(error, 400);
        }
    }

    private BoardCreditCommandResult result(BoardCreditOperation operation) {
        return new BoardCreditCommandResult(
                operation.getOperationId(), operation.getOperationType(), operation.getUserId(),
                operation.getDelta(), operation.getBalanceAfter(),
                operation.getReversalOfOperationId(), operation.getCreatedAt());
    }
}
