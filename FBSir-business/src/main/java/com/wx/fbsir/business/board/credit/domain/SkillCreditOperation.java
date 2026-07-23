package com.wx.fbsir.business.board.credit.domain;

import java.util.Date;
import lombok.Data;

/** Immutable 042 skill-consume operation. */
@Data
public class SkillCreditOperation {
    private Long id;
    private String operationId;
    private String usageRecordId;
    private String idempotencyKey;
    private String requestDigest;
    private String accountId;
    private Long userId;
    private String accountScope;
    private String currencyCode;
    private String operationType;
    private String reasonCode;
    private Long delta;
    private Long balanceBefore;
    private Long balanceAfter;
    private String issuerType;
    private String issuerId;
    private Long packId;
    private String packVersion;
    private String skillCode;
    private String hostType;
    private String hostSessionDigest;
    private Date createdAt;
}
