package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed SHA-256 contracts for commands and the append-only entry chain. */
final class BoardCreditDigest {
    private BoardCreditDigest() {
    }

    static String grantDigest(BoardCreditGrantRequest request, Long actorUserId) {
        return sha256(canonical(
                "credit-command-v1", "GRANT",
                IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE,
                request.userId(), request.amount(), request.reasonCode(), request.note(),
                request.idempotencyKey(), actorUserId));
    }

    static String reversalDigest(
            BoardCreditReversalRequest request, Long actorUserId, Long userId, Long delta) {
        return sha256(canonical(
                "credit-command-v1", "REVERSAL",
                IndependentBoardCreditService.ACCOUNT_SCOPE,
                IndependentBoardCreditService.CURRENCY_CODE,
                userId, delta, request.originalOperationId(), request.reasonCode(), request.note(),
                request.idempotencyKey(), actorUserId));
    }

    static String entryHash(BoardCreditEntry entry) {
        return sha256(canonical(
                "credit-entry-v1", entry.getEntryId(), entry.getOperationId(),
                entry.getRequestDigest(), entry.getAccountId(), entry.getSequenceNo(),
                entry.getDelta(), entry.getBalanceBefore(), entry.getBalanceAfter(),
                entry.getPreviousEntryHash(), entry.getCreatedAt().getTime()));
    }

    static boolean equal(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String canonical(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object raw : values) {
            String value = String.valueOf(raw);
            int byteLength = value.getBytes(StandardCharsets.UTF_8).length;
            result.append(byteLength).append(':').append(value).append('\n');
        }
        return result.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
