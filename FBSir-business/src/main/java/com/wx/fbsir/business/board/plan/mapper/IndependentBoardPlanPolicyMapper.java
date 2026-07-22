package com.wx.fbsir.business.board.plan.mapper;

import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyReceipt;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentBoardPlanPolicyMapper {
    List<BoardPlanPolicySnapshot> selectCurrentPolicies(
            @Param("productCode") String productCode);

    List<String> selectPolicyHeadCodesForUpdate(
            @Param("productCode") String productCode);

    BoardPlanPolicySnapshot selectCurrentPolicy(
            @Param("productCode") String productCode,
            @Param("planCode") String planCode);

    BoardPlanPolicyReceipt selectReceiptByActorAndIdempotencyDigest(
            @Param("productCode") String productCode,
            @Param("actorType") String actorType,
            @Param("actorUserId") Long actorUserId,
            @Param("idempotencyKeyDigest") String idempotencyKeyDigest);

    BoardPlanPolicyReceipt selectReceiptByReceiptId(
            @Param("productCode") String productCode,
            @Param("receiptId") String receiptId);

    int insertReceipt(BoardPlanPolicyReceipt receipt);

    int updateHeadIfCurrent(
            @Param("productCode") String productCode,
            @Param("planCode") String planCode,
            @Param("expectedReceiptId") String expectedReceiptId,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextReceiptId") String nextReceiptId,
            @Param("nextVersion") long nextVersion,
            @Param("updatedAt") Date updatedAt);

    List<BoardPlanPolicyReceipt> selectPolicyReceipts(
            @Param("productCode") String productCode,
            @Param("limit") int limit);
}
