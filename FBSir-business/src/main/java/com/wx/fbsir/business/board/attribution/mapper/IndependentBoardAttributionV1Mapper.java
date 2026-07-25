package com.wx.fbsir.business.board.attribution.mapper;

import com.wx.fbsir.business.board.attribution.domain.BoardAttributionJourneyHead;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface IndependentBoardAttributionV1Mapper {
    BoardAttributionLedgerEvent selectEventByEventId(
            @Param("eventId") String eventId);

    BoardAttributionLedgerEvent selectEventByEventIdForUpdate(
            @Param("eventId") String eventId);

    BoardAttributionLedgerEvent selectEventByReceiptId(
            @Param("receiptId") String receiptId);

    BoardAttributionLedgerEvent selectEventByReceiptIdForUpdate(
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

    List<BoardAttributionSummaryRow> selectAttributionSummary(
            @Param("windowStart") Date windowStart,
            @Param("windowEnd") Date windowEnd,
            @Param("mode") String mode);
}
