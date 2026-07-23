package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import com.wx.fbsir.business.board.credit.domain.SkillCreditOperation;
import com.wx.fbsir.business.board.credit.domain.SkillCreditProjectionBridge;
import com.wx.fbsir.business.board.credit.mapper.SkillConsumeCreditLedgerMapper;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 042 fresh claims and replays. The public coordinator invokes each method through
 * this separate Spring bean so a duplicate fresh transaction rolls back before the
 * committed winner is inspected in a new transaction.
 */
@Service
class SkillConsumeCreditTransactionService {
    private static final String ZERO_HASH = "0".repeat(64);
    private static final long MAX_BALANCE = Integer.MAX_VALUE;

    private final SkillConsumeCreditLedgerMapper mapper;
    private final FbsSkillUsageRecordMapper usageRecordMapper;
    private final Clock clock;
    private final Supplier<String> idGenerator;

    @Autowired
    SkillConsumeCreditTransactionService(
            SkillConsumeCreditLedgerMapper mapper, FbsSkillUsageRecordMapper usageRecordMapper) {
        this(mapper, usageRecordMapper, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    SkillConsumeCreditTransactionService(
            SkillConsumeCreditLedgerMapper mapper,
            FbsSkillUsageRecordMapper usageRecordMapper,
            Clock clock,
            Supplier<String> idGenerator) {
        this.mapper = mapper;
        this.usageRecordMapper = usageRecordMapper;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, rollbackFor = Exception.class)
    public ConsumeResult replayIfPresent(SkillConsumeCreditCommand command) {
        SkillCreditOperation operation = mapper.selectOperationByIdempotencyKey(
                command.idempotencyKey());
        return operation == null ? null : replay(command, operation);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true, rollbackFor = Exception.class)
    public ConsumeResult requireReplay(SkillConsumeCreditCommand command) {
        SkillCreditOperation operation = mapper.selectOperationByIdempotencyKey(
                command.idempotencyKey());
        if (operation == null) {
            throw failure("SKILL_CREDIT_LEDGER_UNEXPECTED_UNIQUE_CONFLICT", 500);
        }
        return replay(command, operation);
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.REPEATABLE_READ,
            rollbackFor = Exception.class)
    public ConsumeResult consumeFresh(SkillConsumeCreditCommand command, String rawHostSessionId) {
        BoardCreditUserProjection user = lockActiveUser(command.userId());
        long projectedBalance = user.getPoints() == null ? 0L : user.getPoints().longValue();
        if (projectedBalance < 0 || projectedBalance > MAX_BALANCE) {
            throw failure("SKILL_CREDIT_LEDGER_PROJECTION_DRIFT", 409);
        }
        if (mapper.countLegacyCreditAccounts(command.userId()) != 0) {
            throw failure("SKILL_CREDIT_LEDGER_LEGACY_AUTHORITY_PRESENT", 409);
        }

        FbsSkillUsageRecord usage = lockOrCreateUsage(command, rawHostSessionId);
        requireInProgressUsage(command, usage);

        Date now = Date.from(clock.instant());
        BoardCreditAccount account = mapper.selectAccountForUpdate(
                command.userId(), SkillConsumeCreditCommand.ACCOUNT_SCOPE,
                SkillConsumeCreditCommand.CURRENCY_CODE);
        SkillCreditProjectionBridge bridge;
        if (account == null) {
            account = openAccount(command.userId(), projectedBalance, now);
            if (mapper.insertAccount(account) != 1) {
                throw failure("SKILL_CREDIT_LEDGER_ACCOUNT_WRITE_FAILED", 500);
            }
            bridge = openBridge(account, now);
            if (mapper.insertBridge(bridge) != 1) {
                throw failure("SKILL_CREDIT_LEDGER_BRIDGE_WRITE_FAILED", 500);
            }
        } else {
            bridge = mapper.selectBridgeForUpdate(
                    account.getAccountId(), command.userId(), SkillConsumeCreditCommand.ACCOUNT_SCOPE,
                    SkillConsumeCreditCommand.CURRENCY_CODE);
        }
        requireCurrentProjection(command, user, account, bridge);

        long amount = command.amount();
        if (account.getBalance() < amount) {
            throw failure("SKILL_CREDIT_LEDGER_INSUFFICIENT_BALANCE", 409);
        }
        long nextBalance = account.getBalance() - amount;
        SkillCreditOperation operation = operation(command, account, nextBalance, now);
        BoardCreditEntry entry = entry(operation, account, now);
        if (mapper.insertOperation(operation) != 1) {
            throw failure("SKILL_CREDIT_LEDGER_OPERATION_WRITE_FAILED", 500);
        }
        if (mapper.insertEntry(entry) != 1) {
            throw failure("SKILL_CREDIT_LEDGER_ENTRY_WRITE_FAILED", 500);
        }
        BoardCreditAccount nextAccount = nextAccount(account, nextBalance, entry.getEntryHash(), now);
        if (mapper.updateAccountIfVersion(nextAccount, account.getVersion(),
                account.getLastEntrySequence(), account.getBalance(), account.getLastEntryHash()) != 1) {
            throw failure("SKILL_CREDIT_LEDGER_ACCOUNT_VERSION_CONFLICT", 409);
        }
        SkillCreditProjectionBridge nextBridge = nextBridge(bridge, nextBalance, now);
        if (mapper.updateBridgeIfVersion(nextBridge, bridge.getProjectionVersion(),
                bridge.getProjectedBalance()) != 1) {
            throw failure("SKILL_CREDIT_LEDGER_BRIDGE_VERSION_CONFLICT", 409);
        }
        if (mapper.updateUserProjectionIfBalance(command.userId(), projectedBalance, nextBalance) != 1) {
            throw failure("SKILL_CREDIT_LEDGER_USER_PROJECTION_CONFLICT", 409);
        }
        if (usageRecordMapper.updateStatusByRecordId(command.usageRecordId(),
                UsageStatus.SUCCESS.getCode(), null) != 1) {
            throw failure("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", 409);
        }
        return ConsumeResult.success(command.usageRecordId(), Math.toIntExact(nextBalance));
    }

    private ConsumeResult replay(SkillConsumeCreditCommand command, SkillCreditOperation operation) {
        requireExactOperation(command, operation);
        BoardCreditEntry entry = mapper.selectEntryByOperationId(operation.getOperationId());
        requireExactEntry(operation, entry);
        FbsSkillUsageRecord usage = usageRecordMapper.selectByRecordId(command.usageRecordId());
        requireSuccessfulUsage(command, usage);
        return ConsumeResult.success(command.usageRecordId(), Math.toIntExact(operation.getBalanceAfter()));
    }

    private BoardCreditUserProjection lockActiveUser(long userId) {
        BoardCreditUserProjection user = mapper.selectUserProjectionForUpdate(userId);
        if (user == null || !Objects.equals(user.getUserId(), userId)
                || !Objects.equals(user.getDelFlag(), "0")) {
            throw failure("SKILL_CREDIT_LEDGER_TARGET_USER_NOT_FOUND", 404);
        }
        if (!Objects.equals(user.getStatus(), "0")) {
            throw failure("SKILL_CREDIT_LEDGER_TARGET_USER_INACTIVE", 409);
        }
        return user;
    }

    private FbsSkillUsageRecord lockOrCreateUsage(
            SkillConsumeCreditCommand command, String rawHostSessionId) {
        FbsSkillUsageRecord usage = usageRecordMapper.selectByRecordIdForUpdate(command.usageRecordId());
        if (usage != null) {
            return usage;
        }
        FbsSkillUsageRecord created = new FbsSkillUsageRecord();
        created.setUsageRecordId(command.usageRecordId());
        created.setUserId(command.userId());
        created.setHostType(command.hostType());
        created.setHostSessionId(rawHostSessionId);
        created.setSkillCode(command.skillCode());
        created.setPackId(command.packId());
        created.setPackVersion(command.packVersion());
        created.setPointsAmount(command.amount());
        created.setStatus(UsageStatus.IN_PROGRESS.getCode());
        created.setStartTime(Date.from(clock.instant()));
        if (usageRecordMapper.insertUsageRecord(created) != 1) {
            throw failure("SKILL_USAGE_RECORD_WRITE_FAILED", 500);
        }
        usage = usageRecordMapper.selectByRecordIdForUpdate(command.usageRecordId());
        if (usage == null) {
            throw failure("SKILL_USAGE_RECORD_CURRENT_READ_FAILED", 500);
        }
        return usage;
    }

    private void requireInProgressUsage(SkillConsumeCreditCommand command, FbsSkillUsageRecord usage) {
        requireUsageScope(command, usage);
        if (Objects.equals(usage.getStatus(), UsageStatus.SUCCESS.getCode())) {
            throw failure("SKILL_CREDIT_LEDGER_REPLAY_REQUIRED", 409);
        }
        if (!Objects.equals(usage.getStatus(), UsageStatus.IN_PROGRESS.getCode())) {
            throw failure("SKILL_USAGE_RECORD_STATE_CONFLICT", 409);
        }
    }

    private void requireSuccessfulUsage(SkillConsumeCreditCommand command, FbsSkillUsageRecord usage) {
        requireUsageScope(command, usage);
        if (!Objects.equals(usage.getStatus(), UsageStatus.SUCCESS.getCode())) {
            throw failure("SKILL_CREDIT_LEDGER_CURRENT_READ_FAILED", 500);
        }
    }

    private void requireUsageScope(SkillConsumeCreditCommand command, FbsSkillUsageRecord usage) {
        if (usage == null) {
            throw failure("SKILL_USAGE_RECORD_CURRENT_READ_FAILED", 500);
        }
        if (!Objects.equals(usage.getUsageRecordId(), command.usageRecordId())
                || !Objects.equals(usage.getUserId(), command.userId())
                || !Objects.equals(usage.getPackId(), command.packId())
                || !Objects.equals(usage.getPackVersion(), command.packVersion())
                || !Objects.equals(usage.getSkillCode(), command.skillCode())
                || !Objects.equals(usage.getHostType(), command.hostType())
                || !Objects.equals(usage.getPointsAmount(), command.amount())
                || !SkillConsumeCreditDigest.equal(sha256(usage.getHostSessionId()),
                        command.hostSessionDigest())) {
            throw failure("SKILL_USAGE_RECORD_SCOPE_MISMATCH", 409);
        }
    }

    private BoardCreditAccount openAccount(long userId, long openingBalance, Date now) {
        BoardCreditAccount account = new BoardCreditAccount();
        account.setAccountId(nextId());
        account.setUserId(userId);
        account.setAccountScope(SkillConsumeCreditCommand.ACCOUNT_SCOPE);
        account.setCurrencyCode(SkillConsumeCreditCommand.CURRENCY_CODE);
        account.setOpeningBalance(openingBalance);
        account.setBalance(openingBalance);
        account.setVersion(0L);
        account.setLastEntrySequence(0L);
        account.setLastEntryHash(ZERO_HASH);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        return account;
    }

    private SkillCreditProjectionBridge openBridge(BoardCreditAccount account, Date now) {
        SkillCreditProjectionBridge bridge = new SkillCreditProjectionBridge();
        bridge.setAccountId(account.getAccountId());
        bridge.setUserId(account.getUserId());
        bridge.setAccountScope(account.getAccountScope());
        bridge.setCurrencyCode(account.getCurrencyCode());
        bridge.setProjectedBalance(account.getBalance());
        bridge.setProjectionVersion(0L);
        bridge.setCreatedAt(now);
        bridge.setUpdatedAt(now);
        return bridge;
    }

    private SkillCreditOperation operation(
            SkillConsumeCreditCommand command, BoardCreditAccount account, long nextBalance, Date now) {
        SkillCreditOperation operation = new SkillCreditOperation();
        operation.setOperationId(nextId());
        operation.setUsageRecordId(command.usageRecordId());
        operation.setIdempotencyKey(command.idempotencyKey());
        operation.setRequestDigest(command.requestDigest());
        operation.setAccountId(account.getAccountId());
        operation.setUserId(command.userId());
        operation.setAccountScope(SkillConsumeCreditCommand.ACCOUNT_SCOPE);
        operation.setCurrencyCode(SkillConsumeCreditCommand.CURRENCY_CODE);
        operation.setOperationType(SkillConsumeCreditCommand.OPERATION_TYPE);
        operation.setReasonCode(SkillConsumeCreditCommand.REASON_CODE);
        operation.setDelta(command.deltaAmount());
        operation.setBalanceBefore(account.getBalance());
        operation.setBalanceAfter(nextBalance);
        operation.setIssuerType(SkillConsumeCreditCommand.ISSUER_TYPE);
        operation.setIssuerId(SkillConsumeCreditCommand.ISSUER_ID);
        operation.setPackId(command.packId());
        operation.setPackVersion(command.packVersion());
        operation.setSkillCode(command.skillCode());
        operation.setHostType(command.hostType());
        operation.setHostSessionDigest(command.hostSessionDigest());
        operation.setCreatedAt(now);
        return operation;
    }

    private BoardCreditEntry entry(SkillCreditOperation operation, BoardCreditAccount account, Date now) {
        BoardCreditEntry entry = new BoardCreditEntry();
        entry.setEntryId(nextId());
        entry.setOperationId(operation.getOperationId());
        entry.setRequestDigest(operation.getRequestDigest());
        entry.setAccountId(account.getAccountId());
        entry.setSequenceNo(account.getLastEntrySequence() + 1);
        entry.setDelta(operation.getDelta());
        entry.setBalanceBefore(operation.getBalanceBefore());
        entry.setBalanceAfter(operation.getBalanceAfter());
        entry.setPreviousEntryHash(account.getLastEntryHash());
        entry.setCreatedAt(now);
        entry.setEntryHash(SkillConsumeCreditDigest.entryHash(entry));
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
        next.setVersion(current.getVersion() + 1);
        next.setLastEntrySequence(current.getLastEntrySequence() + 1);
        next.setLastEntryHash(nextHash);
        next.setCreatedAt(current.getCreatedAt());
        next.setUpdatedAt(now);
        return next;
    }

    private SkillCreditProjectionBridge nextBridge(
            SkillCreditProjectionBridge current, long nextBalance, Date now) {
        SkillCreditProjectionBridge next = new SkillCreditProjectionBridge();
        next.setId(current.getId());
        next.setAccountId(current.getAccountId());
        next.setUserId(current.getUserId());
        next.setAccountScope(current.getAccountScope());
        next.setCurrencyCode(current.getCurrencyCode());
        next.setProjectedBalance(nextBalance);
        next.setProjectionVersion(current.getProjectionVersion() + 1);
        next.setCreatedAt(current.getCreatedAt());
        next.setUpdatedAt(now);
        return next;
    }

    private void requireExactOperation(SkillConsumeCreditCommand command, SkillCreditOperation operation) {
        if (operation == null || !Objects.equals(operation.getUsageRecordId(), command.usageRecordId())
                || !Objects.equals(operation.getIdempotencyKey(), command.idempotencyKey())
                || !SkillConsumeCreditDigest.equal(operation.getRequestDigest(), command.requestDigest())
                || !Objects.equals(operation.getUserId(), command.userId())
                || !Objects.equals(operation.getAccountScope(), SkillConsumeCreditCommand.ACCOUNT_SCOPE)
                || !Objects.equals(operation.getCurrencyCode(), SkillConsumeCreditCommand.CURRENCY_CODE)
                || !Objects.equals(operation.getOperationType(), SkillConsumeCreditCommand.OPERATION_TYPE)
                || !Objects.equals(operation.getReasonCode(), SkillConsumeCreditCommand.REASON_CODE)
                || !Objects.equals(operation.getDelta(), command.deltaAmount())
                || !Objects.equals(operation.getIssuerType(), SkillConsumeCreditCommand.ISSUER_TYPE)
                || !Objects.equals(operation.getIssuerId(), SkillConsumeCreditCommand.ISSUER_ID)
                || !Objects.equals(operation.getPackId(), command.packId())
                || !Objects.equals(operation.getPackVersion(), command.packVersion())
                || !Objects.equals(operation.getSkillCode(), command.skillCode())
                || !Objects.equals(operation.getHostType(), command.hostType())
                || !SkillConsumeCreditDigest.equal(
                        operation.getHostSessionDigest(), command.hostSessionDigest())) {
            throw failure("SKILL_CREDIT_LEDGER_IDEMPOTENCY_DIGEST_CONFLICT", 409);
        }
    }

    private void requireExactEntry(SkillCreditOperation operation, BoardCreditEntry entry) {
        if (entry == null || !Objects.equals(entry.getOperationId(), operation.getOperationId())
                || !SkillConsumeCreditDigest.equal(entry.getRequestDigest(), operation.getRequestDigest())
                || !Objects.equals(entry.getAccountId(), operation.getAccountId())
                || !Objects.equals(entry.getDelta(), operation.getDelta())
                || !Objects.equals(entry.getBalanceBefore(), operation.getBalanceBefore())
                || !Objects.equals(entry.getBalanceAfter(), operation.getBalanceAfter())
                || entry.getSequenceNo() == null || entry.getSequenceNo() <= 0
                || entry.getCreatedAt() == null
                || !SkillConsumeCreditDigest.equal(entry.getEntryHash(), SkillConsumeCreditDigest.entryHash(entry))) {
            throw failure("SKILL_CREDIT_LEDGER_CURRENT_READ_FAILED", 500);
        }
    }

    private void requireCurrentProjection(
            SkillConsumeCreditCommand command,
            BoardCreditUserProjection user,
            BoardCreditAccount account,
            SkillCreditProjectionBridge bridge) {
        if (user == null || account == null || bridge == null
                || !Objects.equals(user.getUserId(), command.userId())
                || !Objects.equals(user.getDelFlag(), "0") || !Objects.equals(user.getStatus(), "0")
                || !Objects.equals(account.getUserId(), command.userId())
                || !Objects.equals(account.getAccountScope(), SkillConsumeCreditCommand.ACCOUNT_SCOPE)
                || !Objects.equals(account.getCurrencyCode(), SkillConsumeCreditCommand.CURRENCY_CODE)
                || account.getOpeningBalance() == null || account.getBalance() == null
                || account.getVersion() == null || account.getLastEntrySequence() == null
                || account.getLastEntryHash() == null || bridge.getAccountId() == null
                || bridge.getProjectedBalance() == null
                || bridge.getProjectionVersion() == null
                || account.getOpeningBalance() < 0 || account.getOpeningBalance() > MAX_BALANCE
                || account.getBalance() < 0 || account.getBalance() > account.getOpeningBalance()
                || account.getVersion() < 0 || account.getLastEntrySequence() < 0
                || bridge.getProjectionVersion() < 0 || bridge.getProjectedBalance() < 0
                || !Objects.equals(bridge.getAccountId(), account.getAccountId())
                || !Objects.equals(bridge.getUserId(), command.userId())
                || !Objects.equals(bridge.getAccountScope(), SkillConsumeCreditCommand.ACCOUNT_SCOPE)
                || !Objects.equals(bridge.getCurrencyCode(), SkillConsumeCreditCommand.CURRENCY_CODE)
                || !Objects.equals(user.getPoints() == null ? 0L : user.getPoints().longValue(), account.getBalance())
                || !Objects.equals(account.getBalance(), bridge.getProjectedBalance())
                || !Objects.equals(account.getVersion(), account.getLastEntrySequence())
                || !Objects.equals(account.getVersion(), bridge.getProjectionVersion())
                || !account.getLastEntryHash().matches("[0-9a-f]{64}")
                || (account.getVersion() == 0 && (!Objects.equals(account.getLastEntryHash(), ZERO_HASH)
                || !Objects.equals(account.getBalance(), account.getOpeningBalance())))
                || (account.getVersion() > 0 && Objects.equals(account.getLastEntryHash(), ZERO_HASH))) {
            throw failure("SKILL_CREDIT_LEDGER_PROJECTION_DRIFT", 409);
        }
    }

    private String nextId() {
        String value = idGenerator.get();
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw failure("SKILL_CREDIT_LEDGER_ID_GENERATION_FAILED", 500);
        }
        return value;
    }

    private static String sha256(String value) {
        if (value == null) {
            return "";
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static ServiceException failure(String code, int status) {
        return new ServiceException(code, status);
    }
}
