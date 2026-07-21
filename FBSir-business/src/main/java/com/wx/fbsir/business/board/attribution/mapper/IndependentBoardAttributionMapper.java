package com.wx.fbsir.business.board.attribution.mapper;

import com.wx.fbsir.business.board.attribution.domain.BoardAttributionEvidenceEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionProductContract;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSnapshot;
import com.wx.fbsir.business.board.attribution.domain.BoardHostForwardingChallenge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentBoardAttributionMapper {
    BoardAttributionProductContract selectExactProductForUpdate(@Param("productId") String productId,
                                                                  @Param("productVersion") String productVersion);

    BoardHostForwardingChallenge selectChallengeForUpdate(@Param("challengeId") String challengeId,
                                                           @Param("serverBindingId") String serverBindingId,
                                                           @Param("contractId") String contractId);

    int insertChallenge(BoardHostForwardingChallenge challenge);

    int updateChallengeStatus(@Param("challengeId") String challengeId,
                              @Param("status") String status);

    int insertEventIfAbsent(BoardAttributionEvidenceEvent event);

    BoardAttributionEvidenceEvent selectEventByReceiptForUpdate(@Param("receiptId") String receiptId);

    int insertSnapshot(BoardAttributionSnapshot snapshot);

    BoardAttributionSnapshot selectSnapshotByWindowForUpdate(@Param("contractId") String contractId,
                                                              @Param("windowStart") java.util.Date windowStart,
                                                              @Param("windowEnd") java.util.Date windowEnd);
}
