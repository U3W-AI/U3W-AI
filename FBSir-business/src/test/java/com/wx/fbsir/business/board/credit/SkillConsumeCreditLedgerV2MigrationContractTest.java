package com.wx.fbsir.business.board.credit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static contract for the default-off 042 skill-consume v2 ledger migration. */
class SkillConsumeCreditLedgerV2MigrationContractTest {
    private static final String MIGRATION =
            "update_20260723_skill_consume_credit_ledger_v2.sql";
    private static final String VERSION =
            "20260723_skill_consume_credit_ledger_v2_042";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        sql = Files.readString(locateMigration(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    @Test
    void migrationIsVersionGatedSerializedAndFailureClosed() {
        assertContains("target: exact mysql community 8.0.30 or 8.4.8");
        assertContains("version() not in ('8.0.30', '8.4.8')");
        assertContains("get_lock(migration_lock_name, 30)");
        assertContains("release_lock(migration_lock_name)");
        assertContains("migration_stage = 'running'");
        assertContains("migration_stage = 'preflight'");
        assertContains("migration_stage = 'table-audit'");
        assertContains("migration_stage = 'receipt'");
        assertTrue(occurrences(sql, VERSION) >= 6,
                "042 lock, preflight, receipt and current-read must share one immutable version");
        assertFalse(sql.contains("drop table"), "042 reruns must never delete ledger state");
        assertFalse(sql.contains("alter table `fbs_credit_"), "042 must not mutate 038 tables");
        assertFalse(sql.contains("alter table `fbs_skill_usage_record`"),
                "042 must not mutate the legacy usage table");
    }

    @Test
    void migrationCreatesOnlyTheFourV2TablesWithIsolatedUsageReceipt() {
        assertEquals(4, occurrences(sql, "create table if not exists `fbs_skill_credit_"));
        assertContains("create table if not exists `fbs_skill_credit_account_v2`");
        assertContains("create table if not exists `fbs_skill_credit_operation_v2`");
        assertContains("create table if not exists `fbs_skill_credit_entry_v2`");
        assertContains("create table if not exists `fbs_skill_credit_projection_bridge_v2`");
        assertContains("target_table_count <> 4");
        assertContains("engine = 'innodb'");
        assertContains("table_collation = 'utf8mb4_unicode_ci'");
        assertContains("unique key `uk_skill_credit_operation_usage` (`usage_record_id`)");
        assertContains("unique key `uk_skill_credit_operation_idempotency` (`idempotency_key`)");
        assertFalse(sql.contains("foreign key (`usage_record_id`)"),
                "legacy usage identity is an immutable receipt anchor, not a foreign-key migration coupling");
    }

    @Test
    void accountAndProjectionContractPreservesOneAuthoritativeV2Balance() {
        assertContains("unique key `uk_skill_credit_account_scope` (`subject_type`, `user_id`, `account_scope`, `currency_code`)");
        assertContains("`subject_type` varchar(16) character set ascii collate ascii_bin not null default 'user'");
        assertContains("`account_scope` varchar(32) character set ascii collate ascii_bin not null default 'user_global'");
        assertContains("`currency_code` varchar(32) character set ascii collate ascii_bin not null default 'fbs_points'");
        assertContains("`last_entry_hash` char(64) character set ascii collate ascii_bin not null default '0000000000000000000000000000000000000000000000000000000000000000'");
        assertContains("constraint `chk_skill_credit_operation_type`");
        assertContains("`operation_type` = 'skill_consume'");
        assertContains("constraint `chk_skill_credit_operation_issuer`");
        assertContains("`issuer_type` = 'service'");
        assertContains("`issuer_id` = 'fbs_skill_consume_v1'");
        assertContains("constraint `chk_skill_credit_operation_delta`");
        assertContains("`delta_amount` < 0");
        assertContains("unique key `uk_skill_credit_entry_account_sequence` (`account_id`, `sequence_no`)");
        assertContains("unique key `uk_skill_credit_entry_operation` (`operation_id`)");
        assertContains("unique key `uk_skill_credit_projection_bridge_scope` (`account_id`, `user_id`, `account_scope`, `currency_code`)");
    }

    @Test
    void receiptFollowsExactCurrentReadAndPublicManifestRemainsBound() throws IOException {
        int audit = sql.indexOf("migration_stage = 'table-audit'");
        int receipt = sql.indexOf("insert into `u3w_schema_migration`");
        assertTrue(audit >= 0 && receipt > audit,
                "migration receipt must follow exact v2 table current-read");
        assertContains("target_internal_receipt_count <> 1");
        assertContains("target_version_receipt_count <> 1");
        assertContains("u3w_assert_skill_consume_credit_ledger_v2_triggers_20260723");
        assertContains("skill consume v2 ledger trigger body contract has drifted");
        assertContains("0cee59ea32e300fb668eae3ab4f7d26053b0d61d96a6e024bc583d487a656d00");
        assertContains("b3b22a50327eef51eae218ef63a88393ac4ec135a8fb697d94bc954b979b91da");
        assertContains("34b5934eedb28e7193d3baece34efe3c2f2b0b6adec4ab751c1e40bcf3b2aa87");
        assertContains("085e2bda7bd883b653f89d645718babe93cec6dca81ad6352aafe9deb6200654");
        assertContains("88f1e0ce3746408140785ce97a97451095c43e229ef57e2c0de81f45b18a2cca");
        assertContains("0f49dad15d89d2d1687de51f60dd6f41b096705e78b321892c02f81e4d131040");
        assertContains("dd4eebc8ae154cebb5a2235cf35a076c86b1e3ba644c7177b9feea2faa0843b4");
        assertContains("058f1aafa0b4e28ccb1ea3eae24f2319f1c63d877fc4aa3816f6b405b743fa1d");
        assertContains("b90f2665d993943fd6df22bcd88f8c1fe89594a1f73be985bcf1b4caba45f4e0");
        assertContains("72adb6082d425d913a1a235ccdc398ed5fc40a0fe7ba1aa0122bdc2f6a3a8d32");
        assertContains("de942cb491f1b4dfc74035c5db0e6c515c184074e4b691ce6ec5c59b14d41c1d");
        assertContains("525f785bcfc3e65823498cc1333331c6d48eb5f023803d360f1895b54463d22c");

        String manifest = Files.readString(locateSql("init-manifest.json"), StandardCharsets.UTF_8);
        assertTrue(manifest.contains("\"version\": \"public_init_042\""));
        assertTrue(manifest.contains("\"file\": \"" + MIGRATION + "\""));
    }

    private static void assertContains(String expected) {
        assertTrue(sql.contains(expected), "missing 042 migration contract: " + expected);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            count++;
            offset += needle.length();
        }
        return count;
    }

    private static Path locateMigration() {
        return locateSql(MIGRATION);
    }

    private static Path locateSql(String name) {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve("sql").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("required 042 SQL artifact not found: " + name);
    }
}
