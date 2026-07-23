package com.wx.fbsir.business.board.credit.service;

import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillConsumeCreditCommandTest {

    @Test
    void canonicalizesEveryPersonalConsumeAnchorWithoutRetainingHostSessionText()
            throws IllegalAccessException {
        SkillConsumeCreditCommand first = command("host-session-secret-1");
        SkillConsumeCreditCommand exactReplay = command("host-session-secret-1");
        SkillConsumeCreditCommand changedSession = command("host-session-secret-2");

        assertEquals(first.requestDigest(), exactReplay.requestDigest());
        assertEquals(first.idempotencyKey(), exactReplay.idempotencyKey());
        assertEquals(first.idempotencyKey(), changedSession.idempotencyKey(),
                "one usage record remains the external replay anchor");
        assertTrue(first.idempotencyKey().matches("scv1:[0-9a-f]{64}"));
        assertNotEquals(first.requestDigest(), changedSession.requestDigest(),
                "a changed command summary must become a stable writer conflict");
        assertNotEquals(first.hostSessionDigest(), changedSession.hostSessionDigest());
        assertDigestChanges(first, command(8L, "usage-001", 9L, "1.2.3", "skill-code",
                "rule-code", 25, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-002", 9L, "1.2.3", "skill-code",
                "rule-code", 25, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-001", 10L, "1.2.3", "skill-code",
                "rule-code", 25, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-001", 9L, "1.2.4", "skill-code",
                "rule-code", 25, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-001", 9L, "1.2.3", "skill-code-2",
                "rule-code", 25, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-001", 9L, "1.2.3", "skill-code",
                "rule-code-2", 25, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-001", 9L, "1.2.3", "skill-code",
                "rule-code", 26, "WORKBUDDY", "host-session-secret-1"));
        assertDigestChanges(first, command(7L, "usage-001", 9L, "1.2.3", "skill-code",
                "rule-code", 25, "API", "host-session-secret-1"));
        assertEquals(-25L, first.deltaAmount());
        assertFalse(first.toString().contains("host-session-secret-1"));
        assertFalse(Arrays.stream(SkillConsumeCreditCommand.class.getDeclaredFields())
                .anyMatch(field -> field.getName().equals("hostSessionId")));
        for (Field field : SkillConsumeCreditCommand.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                field.setAccessible(true);
                assertFalse(((String) field.get(first)).contains("host-session-secret-1"));
            }
        }
    }

    @Test
    void rejectsUnsafeOrOutOfContractCommandFieldsBeforeAnyWriterCanSeeThem() {
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                0L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage with spaces", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 0,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "version with spaces", "skill-code", "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "ENTERPRISE", "session"));
        IllegalArgumentException unknownHost = assertThrows(IllegalArgumentException.class,
                () -> SkillConsumeCreditCommand.create(7L, "usage-001", 9L, "1.2.3",
                        "skill-code", "rule-code", 25, "foo", "session"));
        assertEquals("SKILL_CREDIT_COMMAND_INVALID_HOST_TYPE", unknownHost.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "workbuddy", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "\uD800"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "session\u2060"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "x".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "u".repeat(65), 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "v".repeat(33), "skill-code", "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "s".repeat(65), "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", null, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", "session"));
        assertThrows(IllegalArgumentException.class, () -> SkillConsumeCreditCommand.create(
                7L, "usage-001", 9L, "1.2.3", "skill-code", null, 25,
                "WORKBUDDY", "session"));
    }

    @Test
    void nullablePublicHostSessionHasAStableTypedReplayDigestButBlankRemainsInvalid() {
        SkillConsumeCreditCommand first = command(null);
        SkillConsumeCreditCommand replay = command(null);
        SkillConsumeCreditCommand present = command("host-session-secret");

        assertEquals(first.hostSessionDigest(), replay.hostSessionDigest());
        assertEquals(first.requestDigest(), replay.requestDigest());
        assertNotEquals(first.hostSessionDigest(), present.hostSessionDigest());
        assertNotEquals(first.requestDigest(), present.requestDigest());
        assertThrows(IllegalArgumentException.class, () -> command(""));
        assertThrows(IllegalArgumentException.class, () -> command(" "));
    }

    private SkillConsumeCreditCommand command(String hostSessionId) {
        return command(7L, "usage-001", 9L, "1.2.3", "skill-code", "rule-code", 25,
                "WORKBUDDY", hostSessionId);
    }

    private SkillConsumeCreditCommand command(
            long userId, String usageRecordId, long packId, String packVersion, String skillCode,
            String ruleCode, int amount, String hostType, String hostSessionId) {
        return SkillConsumeCreditCommand.create(
                userId, usageRecordId, packId, packVersion, skillCode, ruleCode, amount,
                hostType, hostSessionId);
    }

    private void assertDigestChanges(
            SkillConsumeCreditCommand baseline, SkillConsumeCreditCommand changed) {
        assertNotEquals(baseline.requestDigest(), changed.requestDigest());
    }
}
