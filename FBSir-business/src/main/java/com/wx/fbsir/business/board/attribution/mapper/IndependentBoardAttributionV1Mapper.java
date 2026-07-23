package com.wx.fbsir.business.board.attribution.mapper;

import com.wx.fbsir.business.board.attribution.domain.BoardAttributionJourneyHead;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IndependentBoardAttributionV1Mapper {
    BoardAttributionLedgerEvent selectEventByEventId(
            @Param("eventId") String eventId);

    BoardAttributionLedgerEvent selectEventByReceiptId(
            @Param("receiptId") String receiptId);

    int insertJourneyHeadIfAbsent(BoardAttributionJourneyHead head);

    BoardAttributionJourneyHead selectJourneyHeadForUpdate(
            @Param("sameBindingKey") String sameBindingKey);

    int insertLedgerEvent(BoardAttributionLedgerEvent event);

    int advanceJourneyHead(
            @Param("sameBindingKey") String sameBindingKey,
            @Param("expectedHeadVersion") long expectedHeadVersion,
            @Param("expectedLastSequenceNo") long expectedLastSequenceNo,
            @Param("nextSequenceNo") long nextSequenceNo,
            @Param("nextEventDigest") String nextEventDigest,
            @Param("intentFamily") String intentFamily);
}
