package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.domain.BoardCreditEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Canonical entry hash for the 042 skill-consume ledger only. */
final class SkillConsumeCreditDigest {
    private SkillConsumeCreditDigest() {
    }

    static String entryHash(BoardCreditEntry entry) {
        return sha256(canonical(
                "skill-credit-entry-v1", entry.getEntryId(), entry.getOperationId(),
                entry.getRequestDigest(), entry.getAccountId(), entry.getSequenceNo(),
                entry.getDelta(), entry.getBalanceBefore(), entry.getBalanceAfter(),
                entry.getPreviousEntryHash(), entry.getCreatedAt().getTime()));
    }

    static boolean equal(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    private static String canonical(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object raw : values) {
            String value = String.valueOf(raw);
            result.append(value.getBytes(StandardCharsets.UTF_8).length).append(':')
                    .append(value).append('\n');
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
