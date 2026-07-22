package com.wx.fbsir.business.board.credit.domain;

import lombok.Data;

/** Aggregate proof that an immutable entry is on the locked account head chain. */
@Data
public class BoardCreditChainProof {
    private Long entryCount;
    private Long validTransitionCount;
    private Long minSequence;
    private Long maxSequence;
    private String headHash;
    private Long headBalance;
}
