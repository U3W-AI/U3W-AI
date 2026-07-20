package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import java.util.Set;

public interface BoardConnectorBindingPort {
    BoardConnectorBindingSnapshot confirmProtectedRequest(
            BoardConnectorProtectedRequestAttestation attestation, Long actorUserId);

    boolean hasAuthoritativeCurrentBinding(
            Long tenantId, Long memberId, Long userId, String productCode, boolean lockBinding);

    Set<BoardConnectorBindingKey> selectAuthoritativeCurrentBindingKeys(
            Long tenantId, String productCode);

    void revokeForEntitlement(
            Long tenantId, Long memberId, Long userId, String productCode, Long actorUserId);
}
