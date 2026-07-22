package com.wx.fbsir.business.board.credit.domain;

import java.util.Date;
import lombok.Data;

/** Immutable administrative credit command receipt. */
@Data
public class BoardCreditOperation {
    private Long id;
    private String operationId;
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
    private Date createdAt;
}
