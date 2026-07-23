package com.wx.fbsir.business.board.credit.mapper;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import com.wx.fbsir.business.board.credit.domain.SkillCreditOperation;
import com.wx.fbsir.business.board.credit.domain.SkillCreditProjectionBridge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Mapper restricted to the default-off 042 skill-consume ledger. */
@Mapper
public interface SkillConsumeCreditLedgerMapper {
    BoardCreditUserProjection selectUserProjection(@Param("userId") Long userId);

    BoardCreditUserProjection selectUserProjectionForUpdate(@Param("userId") Long userId);

    int countLegacyCreditAccounts(@Param("userId") Long userId);

    BoardCreditAccount selectAccount(
            @Param("userId") Long userId,
            @Param("accountScope") String accountScope,
            @Param("currencyCode") String currencyCode);

    BoardCreditAccount selectAccountForUpdate(
            @Param("userId") Long userId,
            @Param("accountScope") String accountScope,
            @Param("currencyCode") String currencyCode);

    int insertAccount(BoardCreditAccount account);

    SkillCreditProjectionBridge selectBridge(
            @Param("accountId") String accountId,
            @Param("userId") Long userId,
            @Param("accountScope") String accountScope,
            @Param("currencyCode") String currencyCode);

    SkillCreditProjectionBridge selectBridgeForUpdate(
            @Param("accountId") String accountId,
            @Param("userId") Long userId,
            @Param("accountScope") String accountScope,
            @Param("currencyCode") String currencyCode);

    int insertBridge(SkillCreditProjectionBridge bridge);

    SkillCreditOperation selectOperationByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    SkillCreditOperation selectOperationByUsageRecordId(
            @Param("usageRecordId") String usageRecordId);

    BoardCreditEntry selectEntryByOperationId(@Param("operationId") String operationId);

    int insertOperation(SkillCreditOperation operation);

    int insertEntry(BoardCreditEntry entry);

    int updateAccountIfVersion(
            @Param("account") BoardCreditAccount account,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedLastEntrySequence") long expectedLastEntrySequence,
            @Param("expectedBalance") long expectedBalance,
            @Param("expectedLastEntryHash") String expectedLastEntryHash);

    int updateBridgeIfVersion(
            @Param("bridge") SkillCreditProjectionBridge bridge,
            @Param("expectedProjectionVersion") long expectedProjectionVersion,
            @Param("expectedProjectedBalance") long expectedProjectedBalance);

    int updateUserProjectionIfBalance(
            @Param("userId") Long userId,
            @Param("expectedBalance") long expectedBalance,
            @Param("nextBalance") long nextBalance);
}
