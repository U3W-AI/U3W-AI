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

/** Static guard for the default-off W3f USER_GLOBAL/FBS_POINTS shadow ledger migration. */
class IndependentBoardCreditLedgerMigrationContractTest {
    private static final String MIGRATION =
            "update_20260722_independent_board_credit_ledger.sql";
    private static final String VERSION =
            "20260722_independent_board_credit_ledger_v1";

    private static String sql;

    @BeforeAll
    static void loadMigration() throws IOException {
        sql = Files.readString(locateMigration(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    @Test
    void migrationIsVersionGatedSerializedAndReceiptBound() {
        assertContains("target: exact mysql community 8.0.30 or 8.4.8");
        assertContains("version() not in ('8.0.30', '8.4.8')");
        assertContains("@@version_comment <> 'mysql community server - gpl'");
        assertContains("get_lock(migration_lock_name, 30)");
        assertContains("is_used_lock(migration_lock_name)");
        assertContains("release_lock(migration_lock_name)");
        assertContains("database() is null or database() = ''");
        assertContains("sys_user_column_contract_count <> 5");
        assertContains("column_name = 'status'");
        assertContains("column_name = 'del_flag'");
        assertContains("column_name = 'update_time'");
        assertTrue(occurrences(sql, "extra not like '%generated%'") >= 2,
                "both writable sys_user projection columns must reject generated storage");
        assertContains("from `u3w_schema_migration`");
        assertTrue(occurrences(sql, VERSION) >= 6,
                "lock, preflight, finalizer and exact receipt must share one version");
        assertFalse(sql.contains("drop trigger"),
                "a rerun must never open an immutability protection window");
    }

    @Test
    void migrationCreatesOnlyTheThreeShadowLedgerTables() {
        assertEquals(3, occurrences(sql, "create table if not exists `fbs_credit_"));
        assertContains("create table if not exists `fbs_credit_account`");
        assertContains("create table if not exists `fbs_credit_operation`");
        assertContains("create table if not exists `fbs_credit_entry`");
        assertContains("target_table_count <> 3");
        assertContains("target_total_column_count <> 45");
        assertContains("engine = 'innodb'");
        assertContains("table_collation = 'utf8mb4_unicode_ci'");
        assertEquals(3, occurrences(
                sql, "`created_at` datetime(3) not null default current_timestamp(3)"),
                "operation and entry hashes depend on millisecond-stable timestamps");
    }

    @Test
    void accountContractFixesIdentityGenesisAndContiguousChainHead() {
        assertContains("`subject_type` varchar(16) character set ascii collate ascii_bin not null default 'user'");
        assertContains("`account_scope` varchar(32) character set ascii collate ascii_bin not null default 'user_global'");
        assertContains("`currency_code` varchar(32) character set ascii collate ascii_bin not null default 'fbs_points'");
        assertContains("`last_entry_sequence` bigint unsigned not null default 0");
        assertContains("unique key `uk_credit_account_scope` (`subject_type`, `user_id`, `account_scope`, `currency_code`)");
        assertContains("unique key `uk_credit_account_snapshot` (`account_id`, `user_id`, `account_scope`, `currency_code`)");
        assertContains("constraint `fk_credit_account_user`");
        assertContains("references `sys_user` (`user_id`)");
        assertContains("constraint `chk_credit_account_version_chain`");
        assertContains("`version` = `last_entry_sequence`");
        assertContains("constraint `chk_credit_account_genesis`");
        assertContains("`last_entry_hash` = repeat('0', 64)");
        assertContains("create trigger if not exists `trg_credit_account_transition`");
        assertContains("new.version = old.version + 1");
        assertContains("new.last_entry_sequence = old.last_entry_sequence + 1");
        assertContains("new.last_entry_hash <> old.last_entry_hash");
        assertContains("e.sequence_no = new.last_entry_sequence");
        assertContains("e.previous_entry_hash = old.last_entry_hash");
        assertContains("e.entry_hash = new.last_entry_hash");
    }

    @Test
    void operationContractBindsIdempotencyAccountReasonsAndSingleReversal() {
        assertContains("unique key `uk_credit_operation_idempotency` (`idempotency_key`)");
        assertContains("unique key `uk_credit_operation_reversal` (`reversal_of_operation_id`)");
        assertContains("unique key `uk_credit_operation_account` (`operation_id`, `account_id`)");
        assertContains("key `idx_credit_operation_account_snapshot` (`account_id`, `user_id`, `account_scope`, `currency_code`)");
        assertContains("key `idx_credit_operation_reversal_account` (`reversal_of_operation_id`, `account_id`)");
        assertContains("constraint `fk_credit_operation_account`");
        assertContains("foreign key (`account_id`, `user_id`, `account_scope`, `currency_code`)");
        assertFalse(sql.contains("constraint `fk_credit_operation_actor`"),
                "the immutable actor snapshot must not take a reverse sys_user FK lock");
        assertContains("constraint `fk_credit_operation_reversal`");
        assertContains("foreign key (`reversal_of_operation_id`, `account_id`)");
        assertContains("constraint `chk_credit_operation_reason`");
        assertContains("'customer_support','service_recovery','migration_correction'");
        assertContains("'duplicate_grant','operator_error','policy_violation'");
        assertContains("constraint `chk_credit_operation_delta_reversal`");
        assertContains("`balance_after` = `balance_before` + `delta_amount`");
        assertContains("`status` = 'committed'");
    }

    @Test
    void indexAuditRejectsPrefixFunctionalInvisibleAndDescendingDrift() {
        assertContains("sum(case when sub_part is not null then 1 else 0 end) as prefix_part_count");
        assertContains("sum(case when expression is not null then 1 else 0 end) as expression_part_count");
        assertContains("sum(case when is_visible <> 'yes' then 1 else 0 end) as invisible_part_count");
        assertContains("sum(case when collation is null or collation <> 'a' then 1 else 0 end)");
        assertContains("prefix_part_count = 0");
        assertContains("expression_part_count = 0");
        assertContains("invisible_part_count = 0");
        assertContains("non_ascending_part_count = 0");
    }

    @Test
    void entryContractProvidesOneContiguousCanonicalHashLinkPerOperation() {
        assertContains("unique key `uk_credit_entry_operation` (`operation_id`)");
        assertContains("unique key `uk_credit_entry_account_sequence` (`account_id`, `sequence_no`)");
        assertContains("key `idx_credit_entry_operation_account` (`operation_id`, `account_id`)");
        assertContains("constraint `fk_credit_entry_operation`");
        assertContains("foreign key (`operation_id`, `account_id`)");
        assertContains("constraint `chk_credit_entry_hashes`");
        assertContains("`previous_entry_hash` regexp '^[0-9a-f]{64}$'");
        assertContains("`entry_hash` regexp '^[0-9a-f]{64}$'");
        assertContains("`canonicalization_version` varchar(32) character set ascii collate ascii_bin not null default 'credit-entry-v1'");
        assertContains("constraint `chk_credit_entry_canonicalization`");
        assertContains("`canonicalization_version` = 'credit-entry-v1'");
    }

    @Test
    void databaseRejectsMutationOfLedgerHistoryAndAccountIdentity() {
        assertEquals(6, occurrences(sql, "create trigger if not exists `trg_credit_"));
        assertContains("create trigger if not exists `trg_credit_operation_no_update`");
        assertContains("create trigger if not exists `trg_credit_operation_no_delete`");
        assertContains("create trigger if not exists `trg_credit_entry_no_update`");
        assertContains("create trigger if not exists `trg_credit_entry_no_delete`");
        assertContains("create trigger if not exists `trg_credit_account_no_delete`");
        assertContains("old.account_id <=> new.account_id");
        assertContains("old.opening_balance <=> new.opening_balance");
        assertContains("target_trigger_count <> 6 or target_trigger_contract_count <> 6");
    }

    @Test
    void exactCurrentReadBindsEveryCheckClauseAndCompleteTriggerBody() {
        assertContains("inner join information_schema.check_constraints");
        assertContains("if previous_group_concat_max_len < 1048576 then");
        assertContains("set session group_concat_max_len = 1048576");
        assertTrue(occurrences(
                        sql,
                        "set session group_concat_max_len = previous_group_concat_max_len") >= 2,
                "success and failure paths must restore the caller session variable");
        assertContains("target_column_metadata_digest");
        assertContains("4717b6466040c2b33513ef1fb92ccb9044e08a00fea41edd7ba617192c9c8d00");
        assertContains("target_index_metadata_digest");
        assertContains("3975985e059c133c0e91ef274702e5d270e4392b7b55b724b1d0d031b76f23cf");
        assertContains("target_foreign_key_metadata_digest");
        assertContains("412a5276aca76d608111eecf0f2297dce0ebe299a35c6d106a4aa09da004ea67");
        assertContains("sha2(cast(cc.check_clause as binary), 256) as clause_sha256");
        assertContains("sha2(cast(action_statement as binary), 256) as action_sha256");
        assertContains("target_check_contract_count");
        assertContains("credit ledger exact 22-check clause contract has drifted");
        assertEquals(22, occurrences(sql, "cast(clause_sha256 as binary) = cast("),
                "every named CHECK must bind the raw metadata bytes by digest");
        assertEquals(6, occurrences(sql, "cast(action_sha256 as binary) = cast("),
                "every trigger must bind the raw action-statement bytes by digest");
        assertContains("and action_order = 1");
        assertContains("93b000e883dee377fb2c4ff72b0cc56c81edfee3b0e177bb2692bd7a6fd96b8e");
        assertContains("fe61351bc245be129eedf83daa790444022925b660ed3f2c0241f9ec15917fc4");
        assertFalse(sql.contains("normalized_clause"),
                "lossy CHECK normalization can erase case-sensitive semantic drift");
        assertFalse(sql.contains("normalized_action"),
                "lossy trigger normalization can erase message and identifier drift");
        assertFalse(sql.contains("locate('old.id<=>new.id'"),
                "substring checks can accept a semantically disabled transition trigger");
    }

    @Test
    void receiptIsWrittenOnlyAfterExactCurrentStateAudits() {
        int audit = sql.indexOf("set migration_stage = 'table-audit'");
        int triggerAudit = sql.indexOf("set migration_stage = 'trigger-contract-audit'");
        int receipt = sql.indexOf("insert into `u3w_schema_migration`");
        assertTrue(audit >= 0 && triggerAudit > audit && receipt > triggerAudit,
                "receipt must follow table/key/check/FK and trigger current-state audits");
        assertContains("independent board user_global fbs_points immutable shadow ledger");
        assertContains("target_internal_receipt_count <> 1");
        assertContains("target_version_receipt_count <> 1");
    }

    private static void assertContains(String expected) {
        assertTrue(sql.contains(expected), "missing migration contract: " + expected);
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
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve("sql").resolve(MIGRATION);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("current W3f migration not found: " + MIGRATION);
    }
}
