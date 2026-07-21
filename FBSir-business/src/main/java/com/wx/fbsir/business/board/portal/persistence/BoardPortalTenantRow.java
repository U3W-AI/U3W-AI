package com.wx.fbsir.business.board.portal.persistence;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardPortalTenantRow {
    private Long rowId;
    private Long tenantId;
    private String tenantLabel;
    private Integer status;
}
