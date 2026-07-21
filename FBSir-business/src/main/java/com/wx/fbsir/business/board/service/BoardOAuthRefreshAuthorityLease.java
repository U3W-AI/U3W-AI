package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.domain.BoardConnectorBinding;
import com.wx.fbsir.business.board.oauth.service.BoardOAuthRefreshAuthorityPort;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;

/** Package-private capability proving that W4a refresh authority rows are locked. */
final class BoardOAuthRefreshAuthorityLease
        implements BoardOAuthRefreshAuthorityPort.Lease {
    private final Object ownerToken;
    private final Thread ownerThread;
    private final Object boardTransactionResource;
    private final Connection boardPhysicalConnection;
    private final BoardConnectorBinding binding;
    private final List<String> scopes;
    private final Instant observedAt;
    private boolean w4aReceiptsLocked;
    private boolean replayRevoked;

    BoardOAuthRefreshAuthorityLease(
            Object ownerToken,
            Object boardTransactionResource,
            Connection boardPhysicalConnection,
            BoardConnectorBinding binding,
            List<String> scopes,
            Instant observedAt) {
        this.ownerToken = ownerToken;
        this.ownerThread = Thread.currentThread();
        this.boardTransactionResource = boardTransactionResource;
        this.boardPhysicalConnection = boardPhysicalConnection;
        this.binding = binding;
        this.scopes = scopes == null ? List.of() : List.copyOf(scopes);
        this.observedAt = observedAt;
    }

    boolean isOwnedBy(Object candidate) {
        return ownerToken == candidate;
    }

    boolean isOwnedByCurrentThread() {
        return ownerThread == Thread.currentThread();
    }

    Object boardTransactionResource(Object ownerCapability) {
        requireOwner(ownerCapability);
        return boardTransactionResource;
    }

    Connection boardPhysicalConnection(Object ownerCapability) {
        requireOwner(ownerCapability);
        return boardPhysicalConnection;
    }

    BoardConnectorBinding binding(Object ownerCapability) {
        requireOwner(ownerCapability);
        return binding;
    }

    List<String> scopes(Object ownerCapability) {
        requireOwner(ownerCapability);
        return scopes;
    }

    Instant observedAt(Object ownerCapability) {
        requireOwner(ownerCapability);
        return observedAt;
    }

    synchronized boolean replayRevoked(Object ownerCapability) {
        requireOwner(ownerCapability);
        return replayRevoked;
    }

    synchronized boolean w4aReceiptsLocked(Object ownerCapability) {
        requireOwner(ownerCapability);
        return w4aReceiptsLocked;
    }

    synchronized void markW4aReceiptsLocked(Object ownerCapability) {
        requireOwner(ownerCapability);
        if (w4aReceiptsLocked || replayRevoked) {
            throw new IllegalStateException("refresh W4a receipt prefix was already locked");
        }
        w4aReceiptsLocked = true;
    }

    synchronized void markReplayRevoked(Object ownerCapability) {
        requireOwner(ownerCapability);
        if (!w4aReceiptsLocked || replayRevoked) {
            throw new IllegalStateException("refresh authority lease was already consumed");
        }
        replayRevoked = true;
    }

    private void requireOwner(Object candidate) {
        if (ownerToken != candidate) {
            throw new IllegalStateException("refresh authority lease owner mismatch");
        }
    }
}
