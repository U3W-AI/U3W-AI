package com.wx.fbsir.business.board.plan.service;

import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyReceipt;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixed SHA-256 contract shared by migration fixtures and runtime verification. */
public final class BoardPlanPolicyDigest {
    private BoardPlanPolicyDigest() {
    }

    public static String idempotencyKeyDigest(String idempotencyKey) {
        return sha256(canonical("board-plan-idempotency-v1", idempotencyKey));
    }

    public static String commandDigest(
            BoardPlanPolicyRevisionRequest request, Long actorUserId) {
        return sha256(canonical(
                "board-plan-policy-command-v1",
                IndependentBoardPlanPolicyService.PRODUCT_CODE,
                request.planCode(), request.expectedVersion(), request.planName(),
                request.dailyMeetingLimit(), request.agendaLimit(), request.seatLimit(),
                request.secretaryEnabled(), request.rollbackOfReceiptId(),
                idempotencyKeyDigest(request.idempotencyKey()), actorUserId));
    }

    public static String policyDigest(BoardPlanPolicyReceipt receipt) {
        return policyDigest(
                receipt.getProductCode(), receipt.getPlanCode(), receipt.getPlanName(),
                receipt.getVip(), receipt.getConnectorRequired(),
                receipt.getDailyMeetingLimit(), receipt.getAgendaLimit(), receipt.getSeatLimit(),
                receipt.getSecretaryEnabled(), receipt.getStatus());
    }

    public static String policyDigest(
            String productCode,
            String planCode,
            String planName,
            Boolean vip,
            Boolean connectorRequired,
            Integer dailyMeetingLimit,
            Integer agendaLimit,
            Integer seatLimit,
            Boolean secretaryEnabled,
            String status) {
        return sha256(canonical(
                "board-plan-policy-v1", productCode, planCode, planName, vip,
                connectorRequired, dailyMeetingLimit, agendaLimit, seatLimit,
                secretaryEnabled, status));
    }

    public static boolean equal(String left, String right) {
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
            requireWellFormedUtf16(value);
            result.append(value.getBytes(StandardCharsets.UTF_8).length)
                    .append(':').append(value).append('\n');
        }
        return result.toString();
    }

    private static void requireWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw new IllegalArgumentException("digest input is not well-formed UTF-16");
                }
                index++;
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("digest input is not well-formed UTF-16");
            }
        }
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
