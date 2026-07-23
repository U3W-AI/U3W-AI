package com.wx.fbsir.business.board.credit.domain;

import java.util.Date;
import lombok.Data;

/** Mutable, CAS-protected compatibility projection for the 042 skill ledger. */
@Data
public class SkillCreditProjectionBridge {
    private Long id;
    private String accountId;
    private Long userId;
    private String accountScope;
    private String currencyCode;
    private Long projectedBalance;
    private Long projectionVersion;
    private Date createdAt;
    private Date updatedAt;
}
