package com.wx.fbsir.business.board.credit.domain;

import java.util.Date;
import lombok.Data;

/** Mutable balance projection whose identity and opening balance are immutable. */
@Data
public class BoardCreditAccount {
    private Long id;
    private String accountId;
    private Long userId;
    private String accountScope;
    private String currencyCode;
    private Long openingBalance;
    private Long balance;
    private Long version;
    private Long lastEntrySequence;
    private String lastEntryHash;
    private Date createdAt;
    private Date updatedAt;
}
