package com.wx.fbsir.business.board.credit.domain;

import java.util.Date;
import lombok.Data;

/** Internal joined operation/entry row for bounded administrator audit reads. */
@Data
public class BoardCreditAuditRow {
    private String operationId;
    private String entryId;
    private String idempotencyKey;
    private String requestDigest;
    private String accountId;
    private Long userId;
    private String accountScope;
    private String currencyCode;
    private String operationType;
    private Long delta;
    private String reasonCode;
    private String reasonNote;
    private Long actorUserId;
    private String reversalOfOperationId;
    private Long balanceBefore;
    private Long balanceAfter;
    private Long sequenceNo;
    private String previousEntryHash;
    private String entryHash;
    private Date createdAt;
    private String entryRequestDigest;
    private String entryAccountId;
    private Long entryDelta;
    private Long entryBalanceBefore;
    private Long entryBalanceAfter;
    private Date entryCreatedAt;
    private String originalOperationType;
    private String originalAccountId;
    private Long originalUserId;
    private Long originalDelta;
}
