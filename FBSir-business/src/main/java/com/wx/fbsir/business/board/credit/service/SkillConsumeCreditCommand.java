package com.wx.fbsir.business.board.credit.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Internal-only, fully normalized input for the future 042 skill-consume writer.
 *
 * <p>This type is intentionally not a request DTO and has no database side effects.
 * It fixes the replay anchor and digest contract before a transaction writer is allowed
 * to exist.  The raw host session is accepted only by {@link #create} and is never
 * retained in the command.</p>
 */
final class SkillConsumeCreditCommand {
    static final String PROTOCOL_VERSION = "skill-consume-credit-v1";
    static final String ACCOUNT_SCOPE = "USER_GLOBAL";
    static final String CURRENCY_CODE = "FBS_POINTS";
    static final String OPERATION_TYPE = "SKILL_CONSUME";
    static final String REASON_CODE = "SKILL_USE";
    static final String ISSUER_TYPE = "SERVICE";
    static final String ISSUER_ID = "FBS_SKILL_CONSUME_V1";

    private static final Pattern USAGE_RECORD_ID =
            Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern PACK_VERSION =
            Pattern.compile("[A-Za-z0-9._:-]{1,32}");
    private static final Pattern SKILL_CODE =
            Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern RULE_CODE =
            Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Pattern HOST_TYPE =
            Pattern.compile("[A-Za-z0-9._:-]{1,32}");
    private static final Set<String> PERSONAL_HOST_TYPES =
            Set.of("WORKBUDDY", "STANDALONE", "API");

    private final long userId;
    private final String usageRecordId;
    private final long packId;
    private final String packVersion;
    private final String skillCode;
    private final String ruleCode;
    private final int amount;
    private final String hostType;
    private final String hostSessionDigest;
    private final String idempotencyKey;
    private final String requestDigest;

    private SkillConsumeCreditCommand(
            long userId,
            String usageRecordId,
            long packId,
            String packVersion,
            String skillCode,
            String ruleCode,
            int amount,
            String hostType,
            String hostSessionDigest,
            String idempotencyKey,
            String requestDigest) {
        this.userId = userId;
        this.usageRecordId = usageRecordId;
        this.packId = packId;
        this.packVersion = packVersion;
        this.skillCode = skillCode;
        this.ruleCode = ruleCode;
        this.amount = amount;
        this.hostType = hostType;
        this.hostSessionDigest = hostSessionDigest;
        this.idempotencyKey = idempotencyKey;
        this.requestDigest = requestDigest;
    }

    static SkillConsumeCreditCommand create(
            Long userId,
            String usageRecordId,
            Long packId,
            String packVersion,
            String skillCode,
            String ruleCode,
            Integer amount,
            String hostType,
            String hostSessionId) {
        if (userId == null || userId <= 0) {
            throw invalid("USER_ID");
        }
        if (packId == null || packId <= 0) {
            throw invalid("PACK_ID");
        }
        if (amount == null || amount <= 0) {
            throw invalid("AMOUNT");
        }
        requireToken(usageRecordId, USAGE_RECORD_ID, "USAGE_RECORD_ID");
        requireToken(packVersion, PACK_VERSION, "PACK_VERSION");
        requireToken(skillCode, SKILL_CODE, "SKILL_CODE");
        requireToken(ruleCode, RULE_CODE, "RULE_CODE");
        requireToken(hostType, HOST_TYPE, "HOST_TYPE");
        if (!PERSONAL_HOST_TYPES.contains(hostType)) {
            throw invalid("HOST_TYPE");
        }
        requireHostSession(hostSessionId);

        String hostSessionDigest = sha256(hostSessionId);
        String idempotencyKey = "scv1:" + sha256(canonical("skill-consume-usage-anchor-v1", usageRecordId));
        String requestDigest = sha256(canonical(
                PROTOCOL_VERSION,
                userId,
                usageRecordId,
                packId,
                packVersion,
                skillCode,
                ruleCode,
                amount,
                hostType,
                hostSessionDigest,
                ACCOUNT_SCOPE,
                CURRENCY_CODE,
                OPERATION_TYPE,
                REASON_CODE,
                ISSUER_TYPE,
                ISSUER_ID));
        return new SkillConsumeCreditCommand(
                userId, usageRecordId, packId, packVersion, skillCode, ruleCode, amount, hostType,
                hostSessionDigest, idempotencyKey, requestDigest);
    }

    long userId() {
        return userId;
    }

    String usageRecordId() {
        return usageRecordId;
    }

    long packId() {
        return packId;
    }

    String packVersion() {
        return packVersion;
    }

    String skillCode() {
        return skillCode;
    }

    String ruleCode() {
        return ruleCode;
    }

    int amount() {
        return amount;
    }

    String hostType() {
        return hostType;
    }

    String hostSessionDigest() {
        return hostSessionDigest;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    String requestDigest() {
        return requestDigest;
    }

    long deltaAmount() {
        return -((long) amount);
    }

    @Override
    public String toString() {
        return "SkillConsumeCreditCommand{"
                + "userId=" + userId
                + ", usageRecordId='" + usageRecordId + '\''
                + ", packId=" + packId
                + ", requestDigest='" + requestDigest + '\''
                + '}';
    }

    private static void requireToken(String value, Pattern pattern, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw invalid(name);
        }
    }

    private static void requireHostSession(String value) {
        if (value == null || value.isEmpty() || value.length() > 128
                || value.codePoints().anyMatch(codePoint -> Character.isISOControl(codePoint)
                || Character.getType(codePoint) == Character.FORMAT
                || Character.getType(codePoint) == Character.SURROGATE)) {
            throw invalid("HOST_SESSION_ID");
        }
    }

    private static IllegalArgumentException invalid(String field) {
        return new IllegalArgumentException("SKILL_CREDIT_COMMAND_INVALID_" + field);
    }

    private static String canonical(Object... values) {
        StringBuilder result = new StringBuilder();
        for (Object raw : values) {
            String value = String.valueOf(raw);
            result.append(value.getBytes(StandardCharsets.UTF_8).length)
                    .append(':')
                    .append(value)
                    .append('\n');
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
