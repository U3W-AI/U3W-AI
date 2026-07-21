package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import java.util.Set;

public interface BoardConnectorBindingPort {
    boolean hasAuthoritativeCurrentBinding(
            Long tenantId, Long memberId, Long userId, String productCode, boolean lockBinding);

    Set<BoardConnectorBindingKey> selectAuthoritativeCurrentBindingKeys(
            Long tenantId, String productCode);

    void revokeForEntitlement(
            Long tenantId, Long memberId, Long userId, String productCode, Long actorUserId);
}
