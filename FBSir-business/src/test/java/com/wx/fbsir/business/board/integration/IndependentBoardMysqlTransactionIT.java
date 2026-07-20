package com.wx.fbsir.business.board.integration;

import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardDashboardView;
import com.wx.fbsir.business.board.dto.BoardEnterpriseContextView;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.dto.BoardMeetingLookupView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
import com.wx.fbsir.business.board.service.IndependentBoardDashboardService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingTransactionService;
import com.wx.fbsir.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit real-MySQL integration test for the Independent Board Spring/MyBatis transaction
 * boundary. The normal Maven lifecycle does not discover *IT classes. Run only against the
 * dedicated loopback database with explicit destructive-test consent.
 */
class IndependentBoardMysqlTransactionIT {
    private static final String DATABASE = "u3w_independent_board_it";
    private static final String MIGRATION_VERSION = "20260720_independent_board_control_plane_v1";
    private static final String MENU_MIGRATION_VERSION =
            "20260720_independent_board_me_menu_v1";
    private static final long TENANT_ONE = 1001L;
    private static final long MEMBER_ONE = 101L;
    private static final long USER_ONE = 501L;
    private static final long TENANT_TWO = 1002L;
    private static final long MEMBER_TWO = 102L;
    private static final long USER_TWO = 502L;
    private static final long TENANT_ONE_MEMBER_TWO = 103L;

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static IndependentBoardEntitlementService entitlementService;
    private static IndependentBoardMeetingService meetingService;
    private static IndependentBoardDashboardService dashboardService;

    @BeforeAll
    static void startSpringContextAndApplyCurrentMigration() throws Exception {
        String url = required("INDEPENDENT_BOARD_MYSQL_IT_URL");
        if (!"true".equals(required("INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP"))) {
            throw new IllegalStateException("INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP must equal true");
        }
        assertSafeDedicatedUrl(url);

        System.setProperty("independent.board.mysql.it.url", url);
        System.setProperty("independent.board.mysql.it.username",
                required("INDEPENDENT_BOARD_MYSQL_IT_USERNAME"));
        String password = value("INDEPENDENT_BOARD_MYSQL_IT_PASSWORD");
        System.setProperty("independent.board.mysql.it.password", password == null ? "" : password);

        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        dataSource = context.getBean(DataSource.class);
        entitlementService = context.getBean(IndependentBoardEntitlementService.class);
        meetingService = context.getBean(IndependentBoardMeetingService.class);
        dashboardService = context.getBean(IndependentBoardDashboardService.class);

        assertTrue(AopUtils.isAopProxy(entitlementService),
                "entitlement service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(meetingService),
                "meeting service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(dashboardService),
                "dashboard service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(
                        context.getBean(IndependentBoardMeetingTransactionService.class)),
                "meeting transaction service must be a Spring transaction proxy");
        assertDedicatedDatabaseAndRuntime();
        recreateSchemaFromCurrentMigration();
    }

    @AfterAll
    static void closeSpringContext() {
        if (context != null) {
            context.close();
        }
        System.clearProperty("independent.board.mysql.it.url");
        System.clearProperty("independent.board.mysql.it.username");
        System.clearProperty("independent.board.mysql.it.password");
    }

    @BeforeEach
    void resetBusinessRows() throws Exception {
        execute(
                "DROP TRIGGER IF EXISTS independent_board_it_fail_finalize",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_receipt",
                "DELETE FROM fbs_usage_operation",
                "DELETE FROM fbs_usage_budget",
                "DELETE FROM fbs_entitlement_receipt",
                "DELETE FROM fbs_product_entitlement",
                "DELETE FROM fbs_enterprise_member",
                "DELETE FROM fbs_enterprise",
                "INSERT INTO fbs_enterprise (id, enterprise_name, status, del_flag) VALUES "
                        + "(" + TENANT_ONE + ", 'Tenant One', 1, '0'), "
                        + "(" + TENANT_TWO + ", 'Tenant Two', 1, '0')",
                "INSERT INTO fbs_enterprise_member "
                        + "(id, enterprise_id, user_id, role, status, del_flag) VALUES "
                        + "(" + MEMBER_ONE + ", " + TENANT_ONE + ", " + USER_ONE
                        + ", 'MEMBER', 1, '0'), "
                        + "(" + MEMBER_TWO + ", " + TENANT_TWO + ", " + USER_TWO
                        + ", 'ADMIN', 1, '0'), "
                        + "(" + TENANT_ONE_MEMBER_TWO + ", " + TENANT_ONE + ", " + USER_TWO
                        + ", 'MEMBER', 1, '0')"
        );
    }

    @Test
    void contextCurrentReadReturnsOnlyTheAuthenticatedUsersActiveEnterprises() {
        List<BoardEnterpriseContextView> userOneContexts = dashboardService.listContexts(USER_ONE);
        List<BoardEnterpriseContextView> userTwoContexts = dashboardService.listContexts(USER_TWO);

        assertEquals(1, userOneContexts.size());
        assertEquals(TENANT_ONE, userOneContexts.get(0).tenantId());
        assertEquals(MEMBER_ONE, userOneContexts.get(0).memberId());
        assertEquals("Tenant One", userOneContexts.get(0).tenantName());
        assertEquals(2, userTwoContexts.size());
        assertTrue(userTwoContexts.stream().noneMatch(context -> context.memberId().equals(MEMBER_ONE)));
    }

    @Test
    void dashboardHistoryIsStrictlyFilteredByTenantAndAuthenticatedUser() {
        meetingService.reserve(request(TENANT_ONE, "history-user-one", 1, 1), USER_ONE);
        meetingService.reserve(request(TENANT_ONE, "history-user-two", 1, 1), USER_TWO);

        BoardDashboardView dashboard = dashboardService.getDashboard(TENANT_ONE, USER_ONE);

        assertEquals(TENANT_ONE, dashboard.context().tenantId());
        assertEquals(USER_ONE, dashboard.entitlement().userId());
        assertEquals(1, dashboard.recentMeetings().size());
        assertEquals("history-user-one", dashboard.recentMeetings().get(0).operationId());
        assertEquals(IndependentBoardDashboardService.CONNECTOR_NOT_CONNECTED,
                dashboard.connectorState());
        assertEquals(IndependentBoardDashboardService.COMING_SOON, dashboard.webhookState());
        assertEquals(IndependentBoardDashboardService.COMING_SOON, dashboard.watchState());
    }

    @Test
    void dashboardRejectsCrossTenantBeforeReturningAnyReadModel() {
        ServiceException rejected = assertThrows(ServiceException.class,
                () -> dashboardService.getDashboard(TENANT_TWO, USER_ONE));

        assertEquals(403, rejected.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", rejected.getMessage());
    }

    @Test
    void exactMeetingReservationReadReturnsTheCommittedReservation() {
        meetingService.reserve(request(TENANT_ONE, "exact-read-001", 2, 2), USER_ONE);

        BoardMeetingLookupView result = dashboardService.getMeetingReservation(
                TENANT_ONE, "exact-read-001", USER_ONE);

        assertTrue(result.found());
        assertEquals("exact-read-001", result.meeting().operationId());
        assertEquals("RESERVED", result.meeting().status());
        assertEquals(IndependentBoardEntitlementService.FREE_PLAN,
                result.meeting().effectivePlanCode());
        assertNotNull(result.meeting().bucketDate());
        assertNotNull(result.meeting().createdAt());
    }

    @Test
    void exactMeetingReservationReadDoesNotRevealAnotherUsersOperation() {
        meetingService.reserve(request(TENANT_ONE, "private-read1", 1, 1), USER_TWO);

        BoardMeetingLookupView missing = dashboardService.getMeetingReservation(
                TENANT_ONE, "missing-read1", USER_ONE);
        BoardMeetingLookupView privateOperation = dashboardService.getMeetingReservation(
                TENANT_ONE, "private-read1", USER_ONE);

        assertFalse(missing.found());
        assertNull(missing.meeting());
        assertEquals(missing, privateOperation);
    }

    @Test
    void exactMeetingReservationReadRejectsCrossTenantBeforeLookup() {
        meetingService.reserve(request(TENANT_TWO, "tenant2-read1", 1, 1), USER_TWO);

        ServiceException rejected = assertThrows(ServiceException.class,
                () -> dashboardService.getMeetingReservation(
                        TENANT_TWO, "tenant2-read1", USER_ONE));

        assertEquals(403, rejected.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", rejected.getMessage());
    }

    @Test
    void disabledEnterpriseClosesEveryMeSurfaceBeforeAnyBudgetOrOperationSideEffect()
            throws Exception {
        assertInactiveEnterpriseClosesEveryMeSurface(0, "0", "disabled");
    }

    @Test
    void deletedEnterpriseClosesEveryMeSurfaceBeforeAnyBudgetOrOperationSideEffect()
            throws Exception {
        assertInactiveEnterpriseClosesEveryMeSurface(1, "1", "deleted");
    }

    @Test
    void menuMigrationCreatesExactRoutePermissionAndOrdinaryUserRoleBinding() throws Exception {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM sys_menu AS menu "
                + "INNER JOIN sys_role_menu AS role_menu ON role_menu.menu_id = menu.menu_id "
                + "INNER JOIN sys_role AS role_row ON role_row.role_id = role_menu.role_id "
                + "WHERE menu.menu_name = '独董会' "
                + "AND menu.parent_id = 0 AND menu.order_num = 0 "
                + "AND menu.path = 'independent-board' "
                + "AND menu.component = 'business/independentBoard/me/index' "
                + "AND menu.route_name = 'IndependentBoardMe' "
                + "AND menu.perms = 'my:independent-board:view' "
                + "AND menu.menu_type = 'C' AND menu.visible = '0' AND menu.status = '0' "
                + "AND role_row.role_key = 'user' "
                + "AND role_row.status = '0' AND role_row.del_flag = '0'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MENU_MIGRATION_VERSION + "' "
                + "AND description = 'Independent Board top-level me portal menu "
                + "and ordinary-user role binding'"));
    }

    @Test
    void menuMigrationFailsClosedOnDriftAndPassesAfterExactRestore() throws Exception {
        Path migration = locateMigration("update_20260720_independent_board_me_menu.sql");
        execute("UPDATE sys_menu SET component = 'drifted/component' "
                + "WHERE path = 'independent-board'");
        try {
            SQLException drift = assertThrows(SQLException.class,
                    () -> executeMigration(migration));
            assertTrue(drift.getMessage().contains("current state is missing or drifted"));
        } finally {
            execute("UPDATE sys_menu SET component = 'business/independentBoard/me/index' "
                    + "WHERE path = 'independent-board'");
        }

        executeMigration(migration);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM sys_menu "
                + "WHERE path = 'independent-board' "
                + "AND component = 'business/independentBoard/me/index' "
                + "AND perms = 'my:independent-board:view'"));
    }

    @Test
    void successfulReservationCommitsBudgetAndOperation() throws Exception {
        BoardMeetingReservationView result = meetingService.reserve(
                request(TENANT_ONE, "success-0001", 2, 3), USER_ONE);

        assertEquals("RESERVED", result.status());
        assertEquals(IndependentBoardEntitlementService.FREE_PLAN, result.effectivePlanCode());
        assertEquals(0, result.remainingCount());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND status = 'RESERVED'"));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget "
                + "WHERE enterprise_id = " + TENANT_ONE));
    }

    @Test
    void exhaustedQuotaRollsBackRejectedOperation() throws Exception {
        meetingService.reserve(request(TENANT_ONE, "quota-ok-001", 1, 1), USER_ONE);

        ServiceException rejected = assertThrows(ServiceException.class,
                () -> meetingService.reserve(
                        request(TENANT_ONE, "quota-no-001", 1, 1), USER_ONE));

        assertEquals(409, rejected.getCode());
        assertEquals("DAILY_MEETING_QUOTA_EXHAUSTED", rejected.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND operation_id = 'quota-no-001'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget "
                + "WHERE enterprise_id = " + TENANT_ONE));
    }

    @Test
    void sameIdempotencyKeyAndPayloadReturnsSameCommittedResult() throws Exception {
        BoardMeetingReservationRequest request = request(TENANT_ONE, "idem-same-01", 2, 3);

        BoardMeetingReservationView first = meetingService.reserve(request, USER_ONE);
        BoardMeetingReservationView replay = meetingService.reserve(request, USER_ONE);

        assertEquals(first, replay);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND operation_id = 'idem-same-01'"));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget "
                + "WHERE enterprise_id = " + TENANT_ONE));
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadRejectsWithoutErroneousCommit() throws Exception {
        meetingService.reserve(request(TENANT_ONE, "idem-diff-01", 1, 1), USER_ONE);
        String originalDigest = scalarString("SELECT request_digest FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND operation_id = 'idem-diff-01'");

        ServiceException conflict = assertThrows(ServiceException.class,
                () -> meetingService.reserve(
                        request(TENANT_ONE, "idem-diff-01", 2, 1), USER_ONE));

        assertEquals(409, conflict.getCode());
        assertEquals("IDEMPOTENCY_DIGEST_CONFLICT", conflict.getMessage());
        assertEquals(originalDigest, scalarString("SELECT request_digest FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND operation_id = 'idem-diff-01'"));
        assertEquals(1, scalarInt("SELECT agenda_count FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND operation_id = 'idem-diff-01'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget "
                + "WHERE enterprise_id = " + TENANT_ONE));
    }

    @Test
    void sameOperationIdIsIsolatedByTenant() throws Exception {
        BoardMeetingReservationView first = meetingService.reserve(
                request(TENANT_ONE, "tenant-key-01", 1, 1), USER_ONE);
        BoardMeetingReservationView second = meetingService.reserve(
                request(TENANT_TWO, "tenant-key-01", 1, 1), USER_TWO);

        assertEquals("RESERVED", first.status());
        assertEquals("RESERVED", second.status());
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE operation_id = 'tenant-key-01'"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_usage_budget"));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget "
                + "WHERE enterprise_id = " + TENANT_ONE));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget "
                + "WHERE enterprise_id = " + TENANT_TWO));
    }

    @Test
    void forcedFinalizeFailureRollsBackWholeReservationAndSameKeyCanRetry() throws Exception {
        execute("CREATE TRIGGER independent_board_it_fail_finalize "
                + "BEFORE UPDATE ON fbs_usage_operation FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced board finalize failure'");
        BoardMeetingReservationRequest request = request(TENANT_ONE, "retry-key-001", 1, 1);

        assertThrows(RuntimeException.class, () -> meetingService.reserve(request, USER_ONE));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_budget"));

        execute("DROP TRIGGER independent_board_it_fail_finalize");
        BoardMeetingReservationView retry = meetingService.reserve(request, USER_ONE);
        assertEquals("RESERVED", retry.status());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget"));
    }

    @Test
    void thirtyTwoConcurrentUniqueReservationsHaveOneWinnerAndNoPendingRows() throws Exception {
        List<Attempt> attempts = runConcurrent(32,
                index -> request(TENANT_ONE, String.format("quota-race-%03d", index), 1, 1),
                USER_ONE);

        assertEquals(1, attempts.stream().filter(Attempt::success).count());
        assertEquals(31, attempts.stream()
                .filter(attempt -> "DAILY_MEETING_QUOTA_EXHAUSTED".equals(attempt.errorCode()))
                .count());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation WHERE status = 'PENDING'"));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget"));
    }

    @Test
    void sixteenConcurrentSameKeyReplaysOneDurableReservation() throws Exception {
        List<Attempt> attempts = runConcurrent(16,
                index -> request(TENANT_ONE, "idem-race-001", 2, 2), USER_ONE);

        assertTrue(attempts.stream().allMatch(Attempt::success));
        assertEquals(1, attempts.stream().map(Attempt::view).distinct().count());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
        assertEquals(1, scalarInt("SELECT reserved_count FROM fbs_usage_budget"));
    }

    @Test
    void entitlementGrantAndAuditCommitAtomically() throws Exception {
        BoardEntitlementAdminView result = entitlementService.grant(
                new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9001L);

        assertEquals(IndependentBoardEntitlementService.VIP_PLAN, result.planCode());
        assertEquals("PENDING_CONNECTOR", result.activationState());
        assertEquals(1L, result.version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_GRANTED' AND evidence_level = 'ACTION_COMPLETED'"));
    }

    @Test
    void entitlementAuditFailureRollsBackGrantAndExpectedVersionCanRetry() throws Exception {
        execute("CREATE TRIGGER independent_board_it_fail_receipt "
                + "BEFORE INSERT ON fbs_entitlement_receipt FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced board receipt failure'");
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                TENANT_ONE, MEMBER_ONE, USER_ONE,
                IndependentBoardEntitlementService.VIP_PLAN,
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L);

        assertThrows(RuntimeException.class, () -> entitlementService.grant(request, 9001L));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));

        execute("DROP TRIGGER independent_board_it_fail_receipt");
        BoardEntitlementAdminView retry = entitlementService.grant(request, 9001L);
        assertEquals(1L, retry.version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
    }

    @Test
    void disabledEnterpriseRejectsAdminGrantBeforeEntitlementOrReceiptWrite() throws Exception {
        assertInactiveEnterpriseRejectsGrant(0, "0");
    }

    @Test
    void deletedEnterpriseRejectsAdminGrantBeforeEntitlementOrReceiptWrite() throws Exception {
        assertInactiveEnterpriseRejectsGrant(1, "1");
    }

    @Test
    void disabledAndDeletedEnterpriseRejectExistingEntitlementUpdatesWithoutMutation()
            throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);
        BoardEntitlementGrantRequest update = new BoardEntitlementGrantRequest(
                TENANT_ONE, MEMBER_ONE, USER_ONE,
                IndependentBoardEntitlementService.FREE_PLAN,
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)), 1L);

        execute("UPDATE fbs_enterprise SET status = 0, del_flag = '0' WHERE id = " + TENANT_ONE);
        assertEntitlementScopeRejected(() -> entitlementService.grant(update, 9001L));
        execute("UPDATE fbs_enterprise SET status = 1, del_flag = '1' WHERE id = " + TENANT_ONE);
        assertEntitlementScopeRejected(() -> entitlementService.grant(update, 9002L));

        assertEquals(1, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(IndependentBoardEntitlementService.VIP_PLAN,
                scalarString("SELECT plan_code FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
    }

    @Test
    void grantWaitsForConcurrentEnterpriseDisableAndRejectsTheCommittedFinalState()
            throws Exception {
        assertConcurrentScopeMutationRejectsGrant(
                "UPDATE fbs_enterprise SET status = 2 WHERE id = " + TENANT_ONE,
                "SELECT status FROM fbs_enterprise WHERE id = " + TENANT_ONE);
    }

    @Test
    void grantWaitsForConcurrentMemberRemovalAndRejectsTheCommittedFinalState()
            throws Exception {
        assertConcurrentScopeMutationRejectsGrant(
                "UPDATE fbs_enterprise_member SET status = 2 WHERE id = " + MEMBER_ONE,
                "SELECT status FROM fbs_enterprise_member WHERE id = " + MEMBER_ONE);
    }

    @Test
    void concurrentFirstEntitlementGrantsProduceOneVersionAndOneReceipt() throws Exception {
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                TENANT_ONE, MEMBER_ONE, USER_ONE,
                IndependentBoardEntitlementService.VIP_PLAN,
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L);

        List<EntitlementAttempt> attempts = runConcurrentEntitlement(
                2, index -> request, index -> 9001L + index);

        assertEquals(1, attempts.stream().filter(EntitlementAttempt::success).count());
        assertEquals(1, attempts.stream().filter(EntitlementAttempt::versionConflict).count());
        assertEquals(1L, attempts.stream().filter(EntitlementAttempt::success)
                .findFirst().orElseThrow().view().version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
    }

    @Test
    void concurrentEntitlementUpdatesAllowOneExpectedVersionWinner() throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);
        BoardEntitlementGrantRequest update = new BoardEntitlementGrantRequest(
                TENANT_ONE, MEMBER_ONE, USER_ONE,
                IndependentBoardEntitlementService.FREE_PLAN,
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)), 1L);

        List<EntitlementAttempt> attempts = runConcurrentEntitlement(
                2, index -> update, index -> 9010L + index);

        assertEquals(1, attempts.stream().filter(EntitlementAttempt::success).count());
        assertEquals(1, attempts.stream().filter(EntitlementAttempt::versionConflict).count());
        BoardEntitlementAdminView winner = attempts.stream()
                .filter(EntitlementAttempt::success).findFirst().orElseThrow().view();
        assertEquals(2L, winner.version());
        assertEquals(IndependentBoardEntitlementService.FREE_PLAN, winner.planCode());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
        assertEquals(2, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_UPDATED'"));
    }

    private static List<Attempt> runConcurrent(
            int concurrency,
            IntFunction<BoardMeetingReservationRequest> requestFactory,
            long userId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Attempt>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    try {
                        return Attempt.succeeded(meetingService.reserve(requestFactory.apply(index), userId));
                    } catch (ServiceException expected) {
                        return Attempt.rejected(expected.getMessage());
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Attempt> attempts = new ArrayList<>();
            for (Future<Attempt> future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
            return attempts;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static List<EntitlementAttempt> runConcurrentEntitlement(
            int concurrency,
            IntFunction<BoardEntitlementGrantRequest> requestFactory,
            IntFunction<Long> actorFactory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<EntitlementAttempt>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    try {
                        return EntitlementAttempt.succeeded(entitlementService.grant(
                                requestFactory.apply(index), actorFactory.apply(index)));
                    } catch (ServiceException expected) {
                        return EntitlementAttempt.rejected(expected.getMessage());
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<EntitlementAttempt> attempts = new ArrayList<>();
            for (Future<EntitlementAttempt> future : futures) {
                attempts.add(future.get(30, TimeUnit.SECONDS));
            }
            return attempts;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static BoardMeetingReservationRequest request(
            long tenantId, String operationId, int agendaCount, int seatCount) {
        return new BoardMeetingReservationRequest(tenantId, operationId, agendaCount, seatCount);
    }

    private void assertInactiveEnterpriseClosesEveryMeSurface(
            int enterpriseStatus,
            String enterpriseDelFlag,
            String operationPrefix) throws Exception {
        String existingOperationId = operationPrefix + "-existing-001";
        String rejectedOperationId = operationPrefix + "-new-001";
        meetingService.reserve(
                request(TENANT_ONE, existingOperationId, 1, 1), USER_ONE);
        execute("DELETE FROM fbs_usage_budget WHERE enterprise_id = " + TENANT_ONE);
        int operationCountBefore = scalarInt(
                "SELECT COUNT(*) FROM fbs_usage_operation WHERE enterprise_id = " + TENANT_ONE);
        int budgetCountBefore = scalarInt(
                "SELECT COUNT(*) FROM fbs_usage_budget WHERE enterprise_id = " + TENANT_ONE);

        execute("UPDATE fbs_enterprise SET status = " + enterpriseStatus
                + ", del_flag = '" + enterpriseDelFlag + "' WHERE id = " + TENANT_ONE);

        assertMeScopeRejected(() -> entitlementService.getSnapshot(TENANT_ONE, USER_ONE));
        assertMeScopeRejected(() -> meetingService.reserve(
                request(TENANT_ONE, rejectedOperationId, 1, 1), USER_ONE));
        assertMeScopeRejected(() -> dashboardService.getDashboard(TENANT_ONE, USER_ONE));
        assertMeScopeRejected(() -> dashboardService.getMeetingReservation(
                TENANT_ONE, existingOperationId, USER_ONE));

        assertEquals(operationCountBefore, scalarInt(
                "SELECT COUNT(*) FROM fbs_usage_operation WHERE enterprise_id = " + TENANT_ONE));
        assertEquals(budgetCountBefore, scalarInt(
                "SELECT COUNT(*) FROM fbs_usage_budget WHERE enterprise_id = " + TENANT_ONE));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation "
                + "WHERE enterprise_id = " + TENANT_ONE
                + " AND operation_id = '" + rejectedOperationId + "'"));
    }

    private void assertInactiveEnterpriseRejectsGrant(
            int enterpriseStatus,
            String enterpriseDelFlag) throws Exception {
        execute("UPDATE fbs_enterprise SET status = " + enterpriseStatus
                + ", del_flag = '" + enterpriseDelFlag + "' WHERE id = " + TENANT_ONE);
        BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                TENANT_ONE, MEMBER_ONE, USER_ONE,
                IndependentBoardEntitlementService.VIP_PLAN,
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L);

        ServiceException rejected = assertThrows(
                ServiceException.class, () -> entitlementService.grant(request, 9001L));

        assertEquals(403, rejected.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", rejected.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
    }

    private void assertEntitlementScopeRejected(Executable action) {
        ServiceException rejected = assertThrows(ServiceException.class, action);
        assertEquals(403, rejected.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", rejected.getMessage());
    }

    private void assertConcurrentScopeMutationRejectsGrant(
            String scopeMutationSql,
            String finalStatusSql) throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection scopeMutation = dataSource.getConnection();
        boolean committed = false;
        try {
            scopeMutation.setAutoCommit(false);
            try (Statement statement = scopeMutation.createStatement()) {
                assertEquals(1, statement.executeUpdate(scopeMutationSql));
            }
            BoardEntitlementGrantRequest request = new BoardEntitlementGrantRequest(
                    TENANT_ONE, MEMBER_ONE, USER_ONE,
                    IndependentBoardEntitlementService.VIP_PLAN,
                    new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L);
            Future<ServiceException> grant = pool.submit(() -> {
                try {
                    entitlementService.grant(request, 9001L);
                    return null;
                } catch (ServiceException expected) {
                    return expected;
                }
            });

            awaitMysqlRowLockWait();
            assertFalse(grant.isDone(),
                    "grant must remain blocked while the canonical scope row is uncommitted");
            scopeMutation.commit();
            committed = true;

            ServiceException rejected = grant.get(10, TimeUnit.SECONDS);
            assertNotNull(rejected, "grant must not commit after the scope becomes inactive");
            assertEquals(403, rejected.getCode());
            assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", rejected.getMessage());
            assertEquals(2, scalarInt(finalStatusSql));
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement"));
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
        } finally {
            if (!committed) {
                scopeMutation.rollback();
            }
            scopeMutation.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static void awaitMysqlRowLockWait() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (scalarInt("SELECT COUNT(*) FROM performance_schema.data_lock_waits") > 0) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("grant did not enter the expected MySQL row-lock wait");
    }

    private void assertMeScopeRejected(Executable action) {
        ServiceException rejected = assertThrows(ServiceException.class, action);
        assertEquals(403, rejected.getCode());
        assertEquals("TENANT_MEMBER_USER_SCOPE_INVALID", rejected.getMessage());
    }

    private static void assertSafeDedicatedUrl(String url) {
        if (!url.matches("^jdbc:mysql://127\\.0\\.0\\.1:(?!3306(?:/|$))\\d+/"
                + DATABASE + "(?:\\?.*)?$")
                || !hasExactlyOneParameter(url, "useAffectedRows", "false")) {
            throw new IllegalStateException("MySQL IT URL must target the dedicated 127.0.0.1 "
                    + "database on a non-3306 port with useAffectedRows=false");
        }
    }

    private static void assertDedicatedDatabaseAndRuntime() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(DATABASE, connection.getCatalog());
            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("MySQL", metadata.getDatabaseProductName());
            assertTrue(metadata.getDatabaseMajorVersion() == 8);
            assertTrue(metadata.getDriverName().contains("MySQL Connector/J"));
            try (Statement statement = connection.createStatement();
                 ResultSet runtime = statement.executeQuery(
                         "SELECT VERSION(), @@version_comment, @@transaction_isolation")) {
                assertTrue(runtime.next());
                String version = runtime.getString(1);
                String distribution = runtime.getString(2);
                String isolation = runtime.getString(3);
                assertTrue(version.startsWith("8."));
                assertTrue(distribution.contains("MySQL"));
                assertEquals("REPEATABLE-READ", isolation);
                System.out.printf(
                        "Independent Board MySQL IT runtime: database=%s; distribution=%s; "
                                + "engine=InnoDB; isolation=%s; connector=%s %s%n",
                        version, distribution, isolation,
                        metadata.getDriverName(), metadata.getDriverVersion());
            }
        }
    }

    private static void recreateSchemaFromCurrentMigration() throws Exception {
        execute(
                "DROP TRIGGER IF EXISTS independent_board_it_fail_finalize",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_receipt",
                "DROP TABLE IF EXISTS fbs_product_entitlement",
                "DROP TABLE IF EXISTS fbs_usage_operation",
                "DROP TABLE IF EXISTS fbs_usage_budget",
                "DROP TABLE IF EXISTS fbs_entitlement_receipt",
                "DROP TABLE IF EXISTS fbs_product_plan",
                "DROP TABLE IF EXISTS sys_role_menu",
                "DROP TABLE IF EXISTS sys_menu",
                "DROP TABLE IF EXISTS sys_role",
                "DROP TABLE IF EXISTS fbs_enterprise_member",
                "DROP TABLE IF EXISTS fbs_enterprise",
                "CREATE TABLE IF NOT EXISTS u3w_schema_migration ("
                        + "version VARCHAR(96) NOT NULL, "
                        + "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "description VARCHAR(255) NOT NULL, PRIMARY KEY (version)) ENGINE=InnoDB",
                "DELETE FROM u3w_schema_migration WHERE version = '" + MIGRATION_VERSION + "'",
                "DELETE FROM u3w_schema_migration WHERE version = '" + MENU_MIGRATION_VERSION + "'",
                "CREATE TABLE sys_menu ("
                        + "menu_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, "
                        + "menu_name VARCHAR(64) NOT NULL, parent_id BIGINT NOT NULL, "
                        + "order_num INT NOT NULL, path VARCHAR(128) NOT NULL, "
                        + "component VARCHAR(255), query VARCHAR(255), route_name VARCHAR(128), "
                        + "is_frame TINYINT NOT NULL, is_cache TINYINT NOT NULL, "
                        + "menu_type CHAR(1) NOT NULL, visible CHAR(1) NOT NULL, "
                        + "status CHAR(1) NOT NULL, perms VARCHAR(128), icon VARCHAR(128), "
                        + "create_by VARCHAR(64), create_time DATETIME, update_by VARCHAR(64), "
                        + "update_time DATETIME, remark VARCHAR(512)) ENGINE=InnoDB",
                "CREATE TABLE sys_role ("
                        + "role_id BIGINT NOT NULL PRIMARY KEY, role_key VARCHAR(64) NOT NULL, "
                        + "status CHAR(1) NOT NULL, del_flag CHAR(1) NOT NULL) ENGINE=InnoDB",
                "CREATE TABLE sys_role_menu ("
                        + "role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, "
                        + "PRIMARY KEY (role_id, menu_id)) ENGINE=InnoDB",
                "INSERT INTO sys_role (role_id, role_key, status, del_flag) "
                        + "VALUES (10, 'user', '0', '0')",
                "CREATE TABLE fbs_enterprise ("
                        + "id BIGINT UNSIGNED NOT NULL PRIMARY KEY, "
                        + "enterprise_name VARCHAR(128) NOT NULL, "
                        + "status TINYINT NOT NULL, del_flag CHAR(1) NOT NULL) ENGINE=InnoDB",
                "CREATE TABLE fbs_enterprise_member ("
                        + "id BIGINT UNSIGNED NOT NULL PRIMARY KEY, "
                        + "enterprise_id BIGINT UNSIGNED NOT NULL, "
                        + "user_id BIGINT UNSIGNED NOT NULL, role VARCHAR(32), "
                        + "status TINYINT NOT NULL, del_flag CHAR(1) NOT NULL, "
                        + "UNIQUE KEY uk_board_it_member (enterprise_id, user_id)) ENGINE=InnoDB"
        );
        executeMigration(locateMigration("update_20260720_independent_board_control_plane.sql"));
        Path menuMigration = locateMigration("update_20260720_independent_board_me_menu.sql");
        executeMigration(menuMigration);
        executeMigration(menuMigration);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MENU_MIGRATION_VERSION + "'"));
        assertEquals(5, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget',"
                + "'fbs_usage_operation','fbs_entitlement_receipt') AND engine = 'InnoDB'"));
    }

    private static void executeMigration(Path path) throws Exception {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = dataSource.getConnection();
             Statement jdbc = connection.createStatement()) {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("DELIMITER ")) {
                    delimiter = trimmed.substring("DELIMITER ".length()).trim();
                    continue;
                }
                if (trimmed.startsWith("--") || trimmed.isEmpty()) {
                    continue;
                }
                statement.append(line).append('\n');
                if (trimmed.endsWith(delimiter)) {
                    int end = statement.lastIndexOf(delimiter);
                    jdbc.execute(statement.substring(0, end));
                    statement.setLength(0);
                }
            }
        }
        if (!statement.isEmpty()) {
            throw new IllegalStateException("Independent Board migration parser left an incomplete statement");
        }
    }

    private static Path locateMigration(String name) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve("sql").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("current Independent Board migration not found: " + name);
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static int scalarInt(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String scalarString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            String value = result.getString(1);
            assertNotNull(value);
            return value;
        }
    }

    private static boolean hasExactlyOneParameter(String url, String key, String expectedValue) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) {
            return false;
        }
        int matches = 0;
        for (String part : url.substring(queryStart + 1).split("&", -1)) {
            int equals = part.indexOf('=');
            if (equals <= 0 || !key.equals(part.substring(0, equals))) {
                continue;
            }
            if (!expectedValue.equals(part.substring(equals + 1))) {
                return false;
            }
            matches++;
        }
        return matches == 1;
    }

    private static String required(String name) {
        String result = value(name);
        if (result == null || result.isBlank()) {
            throw new IllegalStateException(name + " is required for the explicit MySQL IT");
        }
        return result;
    }

    private static String value(String name) {
        String system = System.getProperty(name);
        return system != null ? system : System.getenv(name);
    }

    private record Attempt(BoardMeetingReservationView view, String errorCode) {
        static Attempt succeeded(BoardMeetingReservationView view) {
            return new Attempt(view, null);
        }

        static Attempt rejected(String errorCode) {
            return new Attempt(null, errorCode);
        }

        boolean success() {
            return view != null;
        }
    }

    private record EntitlementAttempt(BoardEntitlementAdminView view, String errorCode) {
        static EntitlementAttempt succeeded(BoardEntitlementAdminView view) {
            return new EntitlementAttempt(view, null);
        }

        static EntitlementAttempt rejected(String errorCode) {
            return new EntitlementAttempt(null, errorCode);
        }

        boolean success() {
            return view != null;
        }

        boolean versionConflict() {
            return "ENTITLEMENT_VERSION_CONFLICT".equals(errorCode)
                    || "ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT".equals(errorCode);
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(System.getProperty("independent.board.mysql.it.url"));
            source.setUsername(System.getProperty("independent.board.mysql.it.username"));
            source.setPassword(System.getProperty("independent.board.mysql.it.password"));
            return source;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResource("classpath:mapper/board/IndependentBoardMapper.xml"));
            return factory.getObject();
        }

        @Bean
        IndependentBoardMapper independentBoardMapper(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory).getMapper(IndependentBoardMapper.class);
        }

        @Bean
        IndependentBoardEntitlementService entitlementService(IndependentBoardMapper mapper) {
            return new IndependentBoardEntitlementService(mapper);
        }

        @Bean
        IndependentBoardDashboardService dashboardService(
                IndependentBoardMapper mapper,
                IndependentBoardEntitlementService entitlementService) {
            return new IndependentBoardDashboardService(mapper, entitlementService);
        }

        @Bean
        IndependentBoardMeetingTransactionService meetingTransactionService(
                IndependentBoardMapper mapper,
                IndependentBoardEntitlementService entitlementService) {
            return new IndependentBoardMeetingTransactionService(mapper, entitlementService);
        }

        @Bean
        IndependentBoardMeetingService meetingService(
                IndependentBoardMapper mapper,
                IndependentBoardMeetingTransactionService transactionService) {
            return new IndependentBoardMeetingService(mapper, transactionService);
        }
    }
}
