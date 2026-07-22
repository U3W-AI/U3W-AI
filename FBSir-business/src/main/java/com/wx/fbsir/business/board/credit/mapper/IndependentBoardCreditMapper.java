package com.wx.fbsir.business.board.credit.mapper;

import com.wx.fbsir.business.board.credit.domain.BoardCreditAccount;
import com.wx.fbsir.business.board.credit.domain.BoardCreditAuditRow;
import com.wx.fbsir.business.board.credit.domain.BoardCreditChainProof;
import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.domain.BoardCreditOperation;
import com.wx.fbsir.business.board.credit.domain.BoardCreditUserProjection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentBoardCreditMapper {
    BoardCreditUserProjection selectUserProjectionForUpdate(@Param("userId") Long userId);

    BoardCreditAccount selectAccount(
            @Param("userId") Long userId,
            @Param("accountScope") String accountScope,
            @Param("currencyCode") String currencyCode);

    BoardCreditAccount selectAccountForUpdate(
            @Param("userId") Long userId,
            @Param("accountScope") String accountScope,
            @Param("currencyCode") String currencyCode);

    int insertAccount(BoardCreditAccount account);

    int insertOperation(BoardCreditOperation operation);

    BoardCreditOperation selectOperationByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    BoardCreditOperation selectOperationByOperationId(
            @Param("operationId") String operationId);

    BoardCreditOperation selectOperationByOperationIdForUpdate(
            @Param("operationId") String operationId);

    BoardCreditOperation selectReversalByOriginalOperationId(
            @Param("operationId") String operationId);

    BoardCreditEntry selectEntryByOperationId(@Param("operationId") String operationId);

    BoardCreditChainProof selectCommittedChainProof(
            @Param("accountId") String accountId,
            @Param("fromSequence") long fromSequence,
            @Param("toSequence") long toSequence);

    int insertEntry(BoardCreditEntry entry);

    int updateAccountIfVersion(
            @Param("account") BoardCreditAccount account,
            @Param("expectedVersion") long expectedVersion,
            @Param("expectedLastEntrySequence") long expectedLastEntrySequence,
            @Param("expectedBalance") long expectedBalance,
            @Param("expectedLastEntryHash") String expectedLastEntryHash);

    int updateUserProjectionIfBalance(
            @Param("userId") Long userId,
            @Param("expectedBalance") long expectedBalance,
            @Param("nextBalance") long nextBalance);

    List<BoardCreditAuditRow> selectAuditRowsByAccountId(
            @Param("accountId") String accountId);
}
