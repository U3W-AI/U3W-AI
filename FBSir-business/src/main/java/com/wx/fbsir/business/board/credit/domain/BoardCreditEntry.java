package com.wx.fbsir.business.board.credit.domain;

import java.util.Date;
import lombok.Data;

/** Immutable balance-transition entry linked to one immutable operation. */
@Data
public class BoardCreditEntry {
    private Long id;
    private String entryId;
    private String operationId;
    private String requestDigest;
    private String accountId;
    private Long sequenceNo;
    private Long delta;
    private Long balanceBefore;
    private Long balanceAfter;
    private String previousEntryHash;
    private String entryHash;
    private Date createdAt;
}
