package com.wx.fbsir.business.board.credit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Static gate requiring the 042 runner to execute the real Spring/MyBatis writer suite. */
class SkillConsumeCreditLedgerV2RunnerContractTest {
    private static String runner;
    private static String centralVerifier;

    @BeforeAll
    static void loadRunner() throws IOException {
        runner = Files.readString(locateRunner(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .toLowerCase(Locale.ROOT);
        centralVerifier = Files.readString(locateRepoFile(
                        "scripts", "verify-independent-board-control-plane.ps1"),
                StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .toLowerCase(Locale.ROOT);
    }

    @Test
    void runnerExecutesTheApplicationTransactionMatrixOnEveryRequestedMysqlVersion() {
        assertContains("skillconsumecreditledgerv2mysqlit.java");
        assertContains("-dtest=skillconsumecreditledgerv2mysqlit");
        assertContains("independent.board.skill.consume.credit.mysql.it.allowdestructive=true");
        assertContains("read-surefireevidence");
        assertContains("$expectedtests = 5");
        assertContains("dualflaghostconsumeusesv2andnevercallslegacywriters");
        assertContains("hostconsumeservice");
        assertContains("applicationtransactionmatrix");
        assertContains("pass_local_dual_mysql_host_path_matrix");
        assertContains("productionconnectionused = $false");
    }

    @Test
    void runnerKeepsTheRealDatabaseMatrixExplicitAndFailClosed() {
        assertContains("'8.0.30', '8.4.8'");
        assertContains("127.0.0.1");
        assertContains("explicit -allowdestructivetest consent is required");
        assertContains("workdirectorycleaned");
        assertFalse(runner.contains("-dskiptests"), "042 runner must never bypass its Java IT");
    }

    @Test
    void centralVerifierRequiresTheW4b5cSuccessorReceipt() {
        assertVerifierContains("skill-consume-dual-mysql-transaction-verification-20260723.json");
        assertVerifierContains("pass_local_dual_mysql_application_transaction_matrix");
        assertVerifierContains("terminalusagecasrollback");
        assertVerifierContains("v2blockslegacygrantreverseaudit");
        assertVerifierContains("surefirereportsha256");
        assertVerifierContains("predecessorsourcesha256");
    }

    @Test
    void centralVerifierRequiresTheW4b5dHostWiringSuccessorReceipt() {
        assertVerifierContains("skill-consume-host-wiring-verification-20260723.json");
        assertVerifierContains("pass_local_default_off_skill_consume_host_wiring");
        assertVerifierContains("outertransactionpoolstarvationriskopen");
        assertVerifierContains("hostsessioncompatibilityopen");
        assertVerifierContains("commercialhuboutboxopen");
        assertVerifierContains("exacttargetactivationreceiptopen");
        assertVerifierContains("fbsir_independent_board_credit_ledger_candidate_enabled:false");
        assertVerifierContains("fbsir_independent_board_skill_consume_credit_writer_enabled:false");
    }

    private static void assertContains(String expected) {
        assertTrue(runner.contains(expected), "missing 042 runner contract: " + expected);
    }

    private static void assertVerifierContains(String expected) {
        assertTrue(centralVerifier.contains(expected),
                "missing skill-consume central-verifier contract: " + expected);
    }

    private static Path locateRunner() {
        return locateRepoFile(
                "scripts", "run-independent-board-skill-consume-credit-ledger-v2-mysql-it.ps1");
    }

    private static Path locateRepoFile(String first, String second) {
        Path cursor = Path.of("").toAbsolutePath().normalize();
        while (cursor != null) {
            Path candidate = cursor.resolve(first).resolve(second);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("required repository file not found: " + first + "/" + second);
    }
}
