package com.wx.fbsir.business.board.credit.domain;

import lombok.Data;

/** Locked compatibility projection from sys_user; not a ledger row. */
@Data
public class BoardCreditUserProjection {
    private Long userId;
    private Integer points;
    private String status;
    private String delFlag;
}
