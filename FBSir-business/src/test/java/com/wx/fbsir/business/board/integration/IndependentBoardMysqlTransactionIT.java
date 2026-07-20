package com.wx.fbsir.business.board.integration;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingSnapshot;
import com.wx.fbsir.business.board.dto.BoardConnectorProtectedRequestAttestation;
import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementReceiptAuditEnvelope;
import com.wx.fbsir.business.board.dto.BoardEntitlementRevokeRequest;
import com.wx.fbsir.business.board.dto.BoardEntitlementSnapshot;
import com.wx.fbsir.business.board.dto.BoardDashboardView;
import com.wx.fbsir.business.board.dto.BoardEnterpriseContextView;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.dto.BoardMeetingLookupView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationRequest;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationResponse;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthClientRegistrationService;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
import com.wx.fbsir.business.board.service.IndependentBoardConnectorBindingService;
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
import java.util.Set;
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
    private static final String CONNECTOR_MIGRATION_VERSION =
            "20260721_independent_board_connector_binding_v1";
    private static final String OAUTH_MIGRATION_VERSION =
            "20260721_independent_board_oauth_foundation_v1";
    private static final String CONNECTOR_ISSUER = "https://api2.u3w.com";
    private static final String CONNECTOR_RESOURCE = "https://api2.u3w.com/fbs-mcp/mcp";
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
    private static IndependentBoardConnectorBindingService connectorBindingService;
    private static IndependentBoardMeetingService meetingService;
    private static IndependentBoardDashboardService dashboardService;
    private static IndependentBoardOAuthClientRegistrationService oauthClientRegistrationService;
    private static IndependentBoardOAuthMapper oauthMapper;

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
        connectorBindingService = context.getBean(IndependentBoardConnectorBindingService.class);
        meetingService = context.getBean(IndependentBoardMeetingService.class);
        dashboardService = context.getBean(IndependentBoardDashboardService.class);
        oauthClientRegistrationService = context.getBean(
                IndependentBoardOAuthClientRegistrationService.class);
        oauthMapper = context.getBean(IndependentBoardOAuthMapper.class);

        assertTrue(AopUtils.isAopProxy(entitlementService),
                "entitlement service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(connectorBindingService),
                "connector binding service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(meetingService),
                "meeting service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(dashboardService),
                "dashboard service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(oauthClientRegistrationService),
                "OAuth client registration service must be a Spring transaction proxy");
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
                "DROP TRIGGER IF EXISTS independent_board_it_fail_binding_receipt",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_oauth_receipt",
                "TRUNCATE TABLE fbs_oauth_receipt",
                "DELETE FROM fbs_oauth_token",
                "DELETE FROM fbs_oauth_token_family",
                "DELETE FROM fbs_oauth_authorization_code",
                "DELETE FROM fbs_oauth_authorization_request",
                "DELETE FROM fbs_oauth_client",
                "DELETE FROM fbs_usage_operation",
                "DELETE FROM fbs_usage_budget",
                "TRUNCATE TABLE fbs_connector_binding_receipt",
                "DELETE FROM fbs_connector_binding_scope",
                "DELETE FROM fbs_connector_binding",
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
    void oauthClientRegistrationAndReceiptCommitAtomicallyAndCanRetryAfterReceiptFailure()
            throws Exception {
        BoardOAuthClientRegistrationRequest request = new BoardOAuthClientRegistrationRequest(
                List.of("http://127.0.0.1:54321/oauth/callback"),
                IndependentBoardOAuthClientRegistrationService.TOKEN_ENDPOINT_AUTH_METHOD,
                List.of("authorization_code", "refresh_token"),
                List.of("code"),
                List.of(
                        "board.receipt.write",
                        "identity.read",
                        "board.meeting.reserve",
                        "entitlement.read"),
                "{\"client_name\":\"WorkBuddy\"}".getBytes(StandardCharsets.UTF_8),
                "internal-w4b-registration".getBytes(StandardCharsets.UTF_8));

        execute("CREATE TRIGGER independent_board_it_fail_oauth_receipt "
                + "BEFORE INSERT ON fbs_oauth_receipt FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'forced OAuth receipt failure'");

        ServiceException failed = assertThrows(
                ServiceException.class, () -> oauthClientRegistrationService.register(request));
        assertEquals(500, failed.getCode());
        assertEquals(IndependentBoardOAuthClientRegistrationService.RECEIPT_WRITE_FAILED,
                failed.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_client"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt"));

        execute("DROP TRIGGER independent_board_it_fail_oauth_receipt");
        BoardOAuthClientRegistrationResponse response =
                oauthClientRegistrationService.register(request);

        assertNotNull(response.clientId());
        assertEquals(BoardOAuthProfile.CANONICAL_SCOPE, response.scope());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_client "
                + "WHERE client_id = '" + response.clientId() + "' AND status = 'ACTIVE'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE client_id = '" + response.clientId() + "' "
                + "AND action = 'OAUTH_CLIENT_REGISTERED' "
                + "AND evidence_level = 'ACTION_COMPLETED'"));
    }

    @Test
    void familyTokenTerminationExpiresHistoricalAccessAndRevokesLiveRefreshAtTimeFloor()
            throws Exception {
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                "INSERT INTO fbs_oauth_token_family SET "
                        + "family_id = 'family-termination-it', "
                        + "origin_authorization_code_id = 1, client_id = REPEAT('A', 43), "
                        + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                        + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                        + "connector_code = 'fbs-connector', issuer_uri = 'https://api2.u3w.com', "
                        + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                        + "scope_canonical = 'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                        + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                        + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                        + "binding_id = 'binding-termination-it', binding_version = 1, "
                        + "status = 'ACTIVE', current_refresh_generation = 0, "
                        + "issued_at = '2026-07-20 00:00:00.000', "
                        + "activated_at = '2026-07-20 00:00:00.000', "
                        + "expires_at = '2026-07-20 01:00:00.000'",
                "INSERT INTO fbs_oauth_token "
                        + "(token_digest, family_id, token_type, generation, resource_uri, "
                        + "scope_canonical, scope_digest, status, issued_at, expires_at) VALUES "
                        + "(UNHEX(REPEAT('71', 32)), 'family-termination-it', 'ACCESS', 0, "
                        + "'https://api2.u3w.com/fbs-mcp/mcp', "
                        + "'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                        + "UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                        + "'ACTIVE', '2026-07-20 00:00:00.000', '2026-07-20 00:10:00.000'), "
                        + "(UNHEX(REPEAT('72', 32)), 'family-termination-it', 'REFRESH', 0, "
                        + "'https://api2.u3w.com/fbs-mcp/mcp', "
                        + "'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                        + "UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                        + "'ACTIVE', '2026-07-20 00:25:00.000', '2026-07-20 00:55:00.000')",
                "SET FOREIGN_KEY_CHECKS = 1");

        int updated = oauthMapper.revokeActiveFamilyTokens(
                "family-termination-it",
                java.sql.Timestamp.valueOf("2026-07-20 00:20:00.000"));

        assertEquals(2, updated);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = 'family-termination-it' AND token_type = 'ACCESS' "
                + "AND status = 'EXPIRED' AND revoked_at IS NULL AND version = 1"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = 'family-termination-it' AND token_type = 'REFRESH' "
                + "AND status = 'REVOKED' "
                + "AND revoked_at = '2026-07-20 00:25:00.000' AND version = 1"));
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
    void firstProtectedConnectorRequestActivatesVipWithExactDurableEvidence() throws Exception {
        grantVip();

        BoardConnectorBindingSnapshot binding = connectorBindingService.confirmProtectedRequest(
                connectorAttestation(), USER_ONE);
        BoardEntitlementSnapshot active = entitlementService.getSnapshot(TENANT_ONE, USER_ONE);
        BoardConnectorBindingSnapshot replay = connectorBindingService.confirmProtectedRequest(
                connectorAttestation(), USER_ONE);

        assertEquals("ACTIVE", binding.status());
        assertEquals(binding.bindingId(), replay.bindingId());
        assertEquals(1L, replay.version());
        assertEquals("ACTIVE", active.activationState());
        assertEquals(IndependentBoardEntitlementService.VIP_PLAN, active.effectivePlanCode());
        assertTrue(active.connectorVerified());
        assertTrue(active.secretaryEnabled());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_scope"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt "
                + "WHERE action = 'CONNECTOR_BINDING_VERIFIED' "
                + "AND evidence_level = 'ACTION_COMPLETED'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement "
                + "WHERE connector_binding_id IS NOT NULL OR connector_verified_at IS NOT NULL"));
    }

    @Test
    void connectorBindingReceiptsAreDatabaseImmutable() throws Exception {
        grantVip();
        connectorBindingService.confirmProtectedRequest(connectorAttestation(), USER_ONE);

        SQLException updateRejected = assertThrows(SQLException.class, () -> execute(
                "UPDATE fbs_connector_binding_receipt SET action = 'CONNECTOR_BINDING_REVOKED'"));
        SQLException deleteRejected = assertThrows(SQLException.class, () -> execute(
                "DELETE FROM fbs_connector_binding_receipt"));

        assertTrue(updateRejected.getMessage().contains("Connector binding receipts are immutable"));
        assertTrue(deleteRejected.getMessage().contains("Connector binding receipts are immutable"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt "
                + "WHERE action = 'CONNECTOR_BINDING_VERIFIED' "
                + "AND evidence_level = 'ACTION_COMPLETED'"));
    }

    @Test
    void connectorReceiptFailureRollsBackBindingAndSameProofCanRetry() throws Exception {
        grantVip();
        execute("CREATE TRIGGER independent_board_it_fail_binding_receipt "
                + "BEFORE INSERT ON fbs_connector_binding_receipt FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced connector receipt failure'");

        assertThrows(RuntimeException.class, () -> connectorBindingService.confirmProtectedRequest(
                connectorAttestation(), USER_ONE));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_scope"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt"));

        execute("DROP TRIGGER independent_board_it_fail_binding_receipt");
        BoardConnectorBindingSnapshot retry = connectorBindingService.confirmProtectedRequest(
                connectorAttestation(), USER_ONE);
        assertEquals("ACTIVE", retry.status());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_scope"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt"));
    }

    @Test
    void thirtyTwoConcurrentFirstConnectorProofsProduceOneBindingAndOneReceipt()
            throws Exception {
        grantVip();
        BoardConnectorProtectedRequestAttestation attestation = connectorAttestation();
        int concurrency = 32;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<BoardConnectorBindingSnapshot>> futures = new ArrayList<>();
            for (int index = 0; index < concurrency; index++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(30, TimeUnit.SECONDS);
                    return connectorBindingService.confirmProtectedRequest(attestation, USER_ONE);
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();
            Set<String> bindingIds = new java.util.HashSet<>();
            for (Future<BoardConnectorBindingSnapshot> future : futures) {
                bindingIds.add(future.get(60, TimeUnit.SECONDS).bindingId());
            }
            assertEquals(1, bindingIds.size());
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding"));
        assertEquals(4, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_scope"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt"));
    }

    @Test
    void missingScopeAndExpiredBindingImmediatelyFallBackToPendingConnector() throws Exception {
        grantVip();
        connectorBindingService.confirmProtectedRequest(connectorAttestation(), USER_ONE);

        execute("DELETE FROM fbs_connector_binding_scope "
                + "WHERE scope_code = 'board.receipt.write'");
        BoardEntitlementSnapshot missingScope = entitlementService.getSnapshot(TENANT_ONE, USER_ONE);
        assertEquals("PENDING_CONNECTOR", missingScope.activationState());
        assertFalse(missingScope.connectorVerified());

        execute("INSERT INTO fbs_connector_binding_scope (binding_id, scope_code) "
                + "SELECT binding_id, 'board.receipt.write' FROM fbs_connector_binding",
                "UPDATE fbs_connector_binding "
                        + "SET verified_at = DATE_SUB(NOW(3), INTERVAL 3 SECOND), "
                        + "last_seen_at = DATE_SUB(NOW(3), INTERVAL 2 SECOND), "
                        + "valid_until = DATE_SUB(NOW(3), INTERVAL 1 SECOND)");
        BoardEntitlementSnapshot expired = entitlementService.getSnapshot(TENANT_ONE, USER_ONE);
        assertEquals("PENDING_CONNECTOR", expired.activationState());
        assertFalse(expired.connectorVerified());
    }

    @Test
    void reservationWaitsForConcurrentScopeRemovalAndUsesCommittedFreePolicyWithoutWrites()
            throws Exception {
        grantVip();
        connectorBindingService.confirmProtectedRequest(connectorAttestation(), USER_ONE);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection scopeRemoval = dataSource.getConnection();
        boolean committed = false;
        try {
            scopeRemoval.setAutoCommit(false);
            try (Statement statement = scopeRemoval.createStatement()) {
                assertEquals(1, statement.executeUpdate(
                        "DELETE FROM fbs_connector_binding_scope "
                                + "WHERE scope_code = 'board.receipt.write'"));
            }
            Future<ServiceException> reservation = pool.submit(() -> {
                try {
                    meetingService.reserve(
                            request(TENANT_ONE, "scope-race-001", 6, 3), USER_ONE);
                    return null;
                } catch (ServiceException expected) {
                    return expected;
                }
            });

            awaitMysqlRowLockWait();
            assertFalse(reservation.isDone(),
                    "reservation must wait for the authoritative scope write to commit");
            scopeRemoval.commit();
            committed = true;

            ServiceException rejected = reservation.get(10, TimeUnit.SECONDS);
            assertNotNull(rejected, "reservation must not retain a stale VIP scope snapshot");
            assertEquals(400, rejected.getCode());
            assertEquals("AGENDA_LIMIT_EXCEEDED", rejected.getMessage());
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_budget"));
        } finally {
            if (!committed) {
                scopeRemoval.rollback();
            }
            scopeRemoval.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void vipDowngradeRevokesBindingAndLaterUpgradeRequiresASeparateReauthorization()
            throws Exception {
        grantVip();
        connectorBindingService.confirmProtectedRequest(connectorAttestation(), USER_ONE);

        BoardEntitlementAdminView free = entitlementService.grant(
                new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.FREE_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)), 1L),
                9001L);
        assertEquals("FREE", free.activationState());
        assertEquals("REVOKED", scalarString("SELECT status FROM fbs_connector_binding"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt "
                + "WHERE action = 'CONNECTOR_BINDING_REVOKED'"));

        BoardEntitlementAdminView upgraded = entitlementService.grant(
                new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)), 2L),
                9002L);
        assertEquals("PENDING_CONNECTOR", upgraded.activationState());
        ServiceException terminalBinding = assertThrows(
                ServiceException.class,
                () -> connectorBindingService.confirmProtectedRequest(
                        connectorAttestation(), USER_ONE));
        assertEquals(409, terminalBinding.getCode());
        assertEquals("BOARD_CONNECTOR_BINDING_CONFLICT", terminalBinding.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_budget"));
    }

    @Test
    void entitlementRevokeAlsoRevokesBindingAfterEnterpriseDisablement() throws Exception {
        grantVip();
        connectorBindingService.confirmProtectedRequest(connectorAttestation(), USER_ONE);
        execute("UPDATE fbs_enterprise SET status = 0 WHERE id = " + TENANT_ONE);

        BoardEntitlementAdminView revoked = entitlementService.revoke(
                new BoardEntitlementRevokeRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE, 1L),
                9001L);

        assertEquals("REVOKED", revoked.activationState());
        assertEquals("REVOKED", scalarString("SELECT status FROM fbs_connector_binding"));
        assertEquals(2, scalarInt("SELECT version FROM fbs_connector_binding"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt "
                + "WHERE action = 'CONNECTOR_BINDING_REVOKED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED'"));
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

    @Test
    void revokeCommitsWithReceiptAndMeCurrentReadFallsBackToExplicitFreeState()
            throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);

        BoardEntitlementAdminView revoked = entitlementService.revoke(
                new BoardEntitlementRevokeRequest(TENANT_ONE, MEMBER_ONE, USER_ONE, 1L),
                9001L);
        BoardEntitlementSnapshot snapshot = entitlementService.getSnapshot(TENANT_ONE, USER_ONE);

        assertEquals("REVOKED", revoked.entitlementStatus());
        assertEquals("REVOKED", revoked.activationState());
        assertEquals(2L, revoked.version());
        assertEquals("REVOKED", snapshot.activationState());
        assertEquals(IndependentBoardEntitlementService.FREE_PLAN, snapshot.effectivePlanCode());
        assertFalse(snapshot.connectorRequired());
        assertEquals("REVOKED", scalarString("SELECT status FROM fbs_product_entitlement"));
        assertEquals(2, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED' "
                + "AND evidence_level = 'ACTION_COMPLETED'"));
    }

    @Test
    void revokeStillWorksAfterEnterpriseAndMemberBecomeInactive() throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);
        execute(
                "UPDATE fbs_enterprise SET status = 2 WHERE id = " + TENANT_ONE,
                "UPDATE fbs_enterprise_member SET status = 2, del_flag = '1' WHERE id = "
                        + MEMBER_ONE);

        BoardEntitlementAdminView revoked = entitlementService.revoke(
                new BoardEntitlementRevokeRequest(TENANT_ONE, MEMBER_ONE, USER_ONE, 1L),
                9001L);

        assertEquals("REVOKED", revoked.entitlementStatus());
        assertEquals(2L, revoked.version());
        assertEquals("REVOKED", scalarString("SELECT status FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED'"));
    }

    @Test
    void revokeSurvivesAClockRollbackWithoutViolatingTheMysqlValidityCheck()
            throws Exception {
        execute("INSERT INTO fbs_product_entitlement "
                + "(enterprise_id, member_id, user_id, product_code, plan_code, status, "
                + "valid_from, valid_until, version, created_at, updated_at) VALUES ("
                + TENANT_ONE + "," + MEMBER_ONE + "," + USER_ONE + ","
                + "'" + IndependentBoardEntitlementService.PRODUCT_CODE + "',"
                + "'" + IndependentBoardEntitlementService.VIP_PLAN + "','ACTIVE',"
                + "TIMESTAMPADD(MINUTE,1,NOW(3)),TIMESTAMPADD(HOUR,2,NOW(3)),1,NOW(3),NOW(3))");

        BoardEntitlementAdminView revoked = entitlementService.revoke(
                new BoardEntitlementRevokeRequest(TENANT_ONE, MEMBER_ONE, USER_ONE, 1L),
                9001L);

        assertEquals("REVOKED", revoked.entitlementStatus());
        assertEquals(2L, revoked.version());
        assertEquals(1000, scalarInt("SELECT TIMESTAMPDIFF(MICROSECOND, valid_from, valid_until) "
                + "FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED'"));
    }

    @Test
    void revokeReceiptFailureRollsBackEntitlementMutationAndSameVersionCanRetry()
            throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);
        execute("CREATE TRIGGER independent_board_it_fail_receipt "
                + "BEFORE INSERT ON fbs_entitlement_receipt FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced board receipt failure'");
        BoardEntitlementRevokeRequest request =
                new BoardEntitlementRevokeRequest(TENANT_ONE, MEMBER_ONE, USER_ONE, 1L);

        assertThrows(RuntimeException.class, () -> entitlementService.revoke(request, 9001L));
        assertEquals("ACTIVE", scalarString("SELECT status FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED'"));

        execute("DROP TRIGGER independent_board_it_fail_receipt");
        BoardEntitlementAdminView retry = entitlementService.revoke(request, 9001L);
        assertEquals("REVOKED", retry.entitlementStatus());
        assertEquals(2L, retry.version());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED'"));
    }

    @Test
    void concurrentRevokesAllowExactlyOneCasWinnerAndOneImmutableReceipt()
            throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);
        BoardEntitlementRevokeRequest request =
                new BoardEntitlementRevokeRequest(TENANT_ONE, MEMBER_ONE, USER_ONE, 1L);

        List<EntitlementAttempt> attempts = runConcurrentRevoke(
                2, request, index -> 9010L + index);

        assertEquals(1, attempts.stream().filter(EntitlementAttempt::success).count());
        assertEquals(1, attempts.stream().filter(EntitlementAttempt::versionConflict).count());
        assertEquals("REVOKED", scalarString("SELECT status FROM fbs_product_entitlement"));
        assertEquals(2, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt "
                + "WHERE action = 'ENTITLEMENT_REVOKED'"));
    }

    @Test
    void grantCannotRestoreARevokedEntitlementInMysql() throws Exception {
        entitlementService.grant(new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)), 0L),
                9000L);
        entitlementService.revoke(
                new BoardEntitlementRevokeRequest(TENANT_ONE, MEMBER_ONE, USER_ONE, 1L),
                9001L);

        ServiceException conflict = assertThrows(ServiceException.class, () -> entitlementService.grant(
                new BoardEntitlementGrantRequest(
                        TENANT_ONE, MEMBER_ONE, USER_ONE,
                        IndependentBoardEntitlementService.VIP_PLAN,
                        new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)), 2L),
                9002L));

        assertEquals(409, conflict.getCode());
        assertEquals("ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT", conflict.getMessage());
        assertEquals("REVOKED", scalarString("SELECT status FROM fbs_product_entitlement"));
        assertEquals(2, scalarInt("SELECT version FROM fbs_product_entitlement"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_entitlement_receipt"));
    }

    @Test
    void receiptAuditIsTenantBoundAndTruncatesAtFiveHundred() throws Exception {
        StringBuilder values = new StringBuilder();
        for (int index = 0; index < 501; index++) {
            if (index > 0) {
                values.append(',');
            }
            values.append("('receipt-bound-").append(index).append("',")
                    .append(TENANT_ONE).append(",9001,").append(MEMBER_ONE)
                    .append(",'ENTITLEMENT_REVOKED',REPEAT('a',64),'ACTION_COMPLETED',")
                    .append("TIMESTAMPADD(MICROSECOND,").append(index)
                    .append(",'2026-07-20 12:00:00.000'))");
        }
        execute("INSERT INTO fbs_entitlement_receipt "
                + "(receipt_id, enterprise_id, actor_user_id, target_member_id, action, "
                + "payload_digest, evidence_level, created_at) VALUES " + values,
                "INSERT INTO fbs_entitlement_receipt "
                        + "(receipt_id, enterprise_id, actor_user_id, target_member_id, action, "
                        + "payload_digest, evidence_level, created_at) VALUES "
                        + "('receipt-other-tenant'," + TENANT_TWO + ",9002," + MEMBER_TWO
                        + ",'ENTITLEMENT_GRANTED',REPEAT('b',64),'ACTION_COMPLETED',NOW(3))");

        BoardEntitlementReceiptAuditEnvelope result = entitlementService.listReceipts(TENANT_ONE);

        assertEquals(500, result.limit());
        assertTrue(result.truncated());
        assertEquals(500, result.records().size());
        assertTrue(result.records().stream().allMatch(row -> row.tenantId().equals(TENANT_ONE)));
        assertTrue(result.records().stream().noneMatch(
                row -> row.receiptId().equals("receipt-other-tenant")));
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

    private static List<EntitlementAttempt> runConcurrentRevoke(
            int concurrency,
            BoardEntitlementRevokeRequest request,
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
                        return EntitlementAttempt.succeeded(
                                entitlementService.revoke(request, actorFactory.apply(index)));
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

    private static BoardEntitlementAdminView grantVip() {
        return entitlementService.grant(new BoardEntitlementGrantRequest(
                TENANT_ONE,
                MEMBER_ONE,
                USER_ONE,
                IndependentBoardEntitlementService.VIP_PLAN,
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(2)),
                0L), 9000L);
    }

    private static BoardConnectorProtectedRequestAttestation connectorAttestation() {
        return new BoardConnectorProtectedRequestAttestation(
                TENANT_ONE,
                MEMBER_ONE,
                USER_ONE,
                CONNECTOR_ISSUER,
                CONNECTOR_RESOURCE,
                "workbuddy-mysql-it-client",
                "a".repeat(64),
                List.of(
                        "identity.read",
                        "entitlement.read",
                        "board.meeting.reserve",
                        "board.receipt.write"),
                IndependentBoardConnectorBindingService.VERIFY_INITIALIZE,
                "b".repeat(64),
                new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
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
                "DROP TRIGGER IF EXISTS independent_board_it_fail_binding_receipt",
                "DROP TABLE IF EXISTS fbs_oauth_receipt",
                "DROP TABLE IF EXISTS fbs_oauth_token",
                "DROP TABLE IF EXISTS fbs_oauth_token_family",
                "DROP TABLE IF EXISTS fbs_oauth_authorization_code",
                "DROP TABLE IF EXISTS fbs_oauth_authorization_request",
                "DROP TABLE IF EXISTS fbs_oauth_client",
                "DROP TABLE IF EXISTS fbs_connector_binding_receipt",
                "DROP TABLE IF EXISTS fbs_connector_binding_scope",
                "DROP TABLE IF EXISTS fbs_connector_binding",
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
                "DELETE FROM u3w_schema_migration WHERE version = '" + CONNECTOR_MIGRATION_VERSION + "'",
                "DELETE FROM u3w_schema_migration WHERE version = '" + OAUTH_MIGRATION_VERSION + "'",
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
        Path connectorMigration = locateMigration(
                "update_20260721_independent_board_connector_binding.sql");
        assertConnectorMigrationRejectsPartialStatesAndReleasesLock(connectorMigration);
        try {
            executeMigration(connectorMigration);
        } catch (SQLException migrationFailure) {
            printConnectorCheckContractForDiagnosis();
            throw migrationFailure;
        }
        executeMigration(connectorMigration);
        assertConnectorMigrationRejectsCheckDriftAndReleasesLock(connectorMigration);
        assertConnectorMigrationRejectsMissingImmutabilityTriggerAndReleasesLock(
                connectorMigration);
        Path oauthMigration = locateMigration(
                "update_20260721_independent_board_oauth_foundation.sql");
        assertOAuthMigrationRejectsPartialStatesAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsExternalDependencyDriftAndReleasesLock(oauthMigration);
        try {
            executeMigration(oauthMigration);
        } catch (SQLException migrationFailure) {
            printOAuthIndexContractForDiagnosis();
            printOAuthExactContractEvidence();
            throw migrationFailure;
        }
        executeMigration(oauthMigration);
        assertOAuthLifecycleNullVectorsAreRejected();
        assertOAuthReceiptRequiredObjectVectorsAreRejected();
        assertOAuthMigrationRejectsSameNameCheckDriftAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsColumnShapeDriftAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsGeneratedExpressionDriftAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsIndexVisibilityDriftAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsForeignKeyActionDriftAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsMissingImmutabilityTriggerAndReleasesLock(oauthMigration);
        assertOAuthMigrationRejectsTransplantedImmutabilityTriggersAndReleasesLock(
                oauthMigration);
        Path menuMigration = locateMigration("update_20260720_independent_board_me_menu.sql");
        executeMigration(menuMigration);
        executeMigration(menuMigration);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MENU_MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + CONNECTOR_MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_MIGRATION_VERSION + "'"));
        assertEquals(5, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_product_plan','fbs_product_entitlement','fbs_usage_budget',"
                + "'fbs_usage_operation','fbs_entitlement_receipt') AND engine = 'InnoDB'"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_connector_binding','fbs_connector_binding_scope',"
                + "'fbs_connector_binding_receipt') AND engine = 'InnoDB'"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() "
                + "AND event_object_table = 'fbs_connector_binding_receipt'"));
        assertEquals(6, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt') AND engine = 'InnoDB'"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() "
                + "AND event_object_table = 'fbs_oauth_receipt'"));
    }

    private static void assertOAuthMigrationRejectsPartialStatesAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_oauth_foundation_v1'), 256))";
        execute("CREATE TABLE fbs_oauth_client ("
                + "id BIGINT UNSIGNED NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        SQLException orphanTable = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(orphanTable.getMessage().contains(
                "tables exist without the exact migration receipt"), orphanTable.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_MIGRATION_VERSION + "'"));
        execute("DROP TABLE fbs_oauth_client");

        execute("INSERT INTO u3w_schema_migration (version, description) VALUES ('"
                + OAUTH_MIGRATION_VERSION + "', "
                + "'Independent Board OAuth client, authorization, token family "
                + "and immutable receipt tables')");
        SQLException orphanReceipt = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(orphanReceipt.getMessage().contains(
                "migration receipt exists but its six-table set is incomplete"),
                orphanReceipt.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        execute("DELETE FROM u3w_schema_migration WHERE version = '"
                + OAUTH_MIGRATION_VERSION + "'");

        execute(
                "CREATE TRIGGER trg_oauth_receipt_no_update "
                        + "BEFORE UPDATE ON fbs_connector_binding FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'",
                "CREATE TRIGGER trg_oauth_receipt_no_delete "
                        + "BEFORE DELETE ON fbs_connector_binding FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'");
        SQLException triggerNameCollision = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(triggerNameCollision.getMessage().contains(
                "trigger names collide before first apply"),
                triggerNameCollision.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name LIKE 'fbs_oauth_%'"));
        assertEquals(1, scalarInt(lockFreeSql));
        execute(
                "DROP TRIGGER trg_oauth_receipt_no_update",
                "DROP TRIGGER trg_oauth_receipt_no_delete");
    }

    private static void assertOAuthMigrationRejectsExternalDependencyDriftAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = oauthMigrationLockFreeSql();
        execute("ALTER TABLE fbs_connector_binding "
                + "DROP FOREIGN KEY fk_connector_binding_entitlement");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains(
                "external FK dependency contract has drifted"), drift.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name LIKE 'fbs_oauth_%'"));
        assertEquals(1, scalarInt(lockFreeSql));

        execute("ALTER TABLE fbs_connector_binding "
                + "ADD CONSTRAINT fk_connector_binding_entitlement "
                + "FOREIGN KEY (enterprise_id, member_id, product_code) "
                + "REFERENCES fbs_product_entitlement "
                + "(enterprise_id, member_id, product_code) "
                + "ON UPDATE RESTRICT ON DELETE RESTRICT");
    }

    private static void assertOAuthLifecycleNullVectorsAreRejected() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                assertCheckConstraintRejects(statement, "chk_oauth_client_lifecycle",
                        invalidOAuthClientInsert("REVOKED"));
                assertCheckConstraintRejects(statement, "chk_oauth_client_lifecycle",
                        invalidOAuthClientInsert("EXPIRED"));
                assertCheckConstraintRejects(statement, "chk_oauth_request_lifecycle",
                        invalidOAuthRequestInsert("31", "APPROVED", true, ""));
                assertCheckConstraintRejects(statement, "chk_oauth_request_lifecycle",
                        invalidOAuthRequestInsert("32", "DENIED", false, ""));
                assertCheckConstraintRejects(statement, "chk_oauth_request_lifecycle",
                        invalidOAuthRequestInsert("33", "CONSUMED", false,
                                ", consumed_at = '2026-07-20 00:03:00.000'"));
                assertCheckConstraintRejects(statement, "chk_oauth_request_lifecycle",
                        invalidOAuthRequestInsert("34", "CONSUMED", false,
                                ", approved_at = '2026-07-20 00:02:00.000'"));
                assertCheckConstraintRejects(statement, "chk_oauth_request_lifecycle",
                        invalidOAuthRequestInsert("35", "EXPIRED", false,
                                ", approved_at = '2026-07-19 23:59:00.000'"));
                assertCheckConstraintRejects(statement, "chk_oauth_code_lifecycle",
                        invalidOAuthCodeInsert("41", "USED"));
                assertCheckConstraintRejects(statement, "chk_oauth_code_lifecycle",
                        invalidOAuthCodeInsert("42", "REVOKED"));
                assertCheckConstraintRejects(statement, "chk_oauth_family_lifecycle",
                        invalidOAuthFamilyInsert("family-invalid-active", "ACTIVE",
                                ", binding_id = 'binding-invalid-active', binding_version = 1"));
                assertCheckConstraintRejects(statement, "chk_oauth_family_lifecycle",
                        invalidOAuthFamilyInsert("family-invalid-terminal", "REVOKED", ""));
                assertCheckConstraintRejects(statement, "chk_oauth_family_lifecycle",
                        invalidOAuthFamilyInsert("family-invalid-bound-terminal", "COMPROMISED",
                                ", binding_id = 'binding-invalid-terminal', binding_version = 1"
                                        + ", terminated_at = '2026-07-20 00:04:00.000'"));
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private static void assertOAuthReceiptRequiredObjectVectorsAreRejected() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("61", "OAUTH_CLIENT_REGISTERED",
                                ", family_id = 'family-unexpected'", false));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("62", "AUTHORIZATION_APPROVED",
                                "", true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("63", "AUTHORIZATION_CODE_ISSUED",
                                ", authorization_request_id = 1", true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("64", "AUTHORIZATION_CODE_REPLAY_DETECTED",
                                ", authorization_request_id = 1, authorization_code_id = 1, "
                                        + "family_id = 'family-replay'", true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("65", "TOKEN_FAMILY_CREATED",
                                ", authorization_code_id = 1, family_id = 'family-created', "
                                        + "binding_id = 'binding-unexpected'", true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("66", "TOKEN_FAMILY_ACTIVATED",
                                ", family_id = 'family-activated'", true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("67", "TOKEN_FAMILY_ROTATED",
                                ", family_id = 'family-rotated', binding_id = 'binding-rotated'",
                                true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("68", "TOKEN_FAMILY_REVOKED",
                                ", family_id = 'family-revoked', token_id = 1", true));
                assertCheckConstraintRejects(statement, "chk_oauth_receipt_required_objects",
                        invalidOAuthReceiptInsert("69", "REFRESH_REPLAY_DETECTED",
                                ", family_id = 'family-refresh'", true));
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    private static void assertCheckConstraintRejects(
            Statement statement,
            String expectedConstraint,
            String insertSql) {
        SQLException rejected = assertThrows(SQLException.class, () -> statement.execute(insertSql));
        assertTrue(rejected.getMessage().contains(expectedConstraint), rejected.getMessage());
    }

    private static String invalidOAuthClientInsert(String status) {
        return "INSERT INTO fbs_oauth_client SET "
                + "client_id = REPEAT('A', 43), "
                + "client_name = CONVERT(0xe69caae9aa8ce8af81e79a84e69cace59cb0e585ace585b1e5aea2e688b7e7abaf USING utf8mb4), "
                + "issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', redirect_port = 49152, "
                + "redirect_uri = 'http://127.0.0.1:49152/oauth/callback', "
                + "token_endpoint_auth_method = 'none', "
                + "grant_types_canonical = 'authorization_code refresh_token', "
                + "response_types_canonical = 'code', "
                + "scope_canonical = 'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "metadata_digest = UNHEX(REPEAT('11', 32)), "
                + "registration_source_digest = UNHEX(REPEAT('12', 32)), "
                + "status = '" + status + "', registered_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-08-20 00:00:00.000'";
    }

    private static String invalidOAuthRequestInsert(
            String marker,
            String status,
            boolean encryptedState,
            String lifecycleAssignments) {
        String cryptoAssignments = encryptedState
                ? ", state_key_ref = 'kms:test', "
                        + "state_nonce = UNHEX('000102030405060708090a0b'), "
                        + "state_ciphertext = UNHEX(REPEAT('ab', 32))"
                : "";
        return "INSERT INTO fbs_oauth_authorization_request SET "
                + "request_handle_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "client_id = REPEAT('A', 43), "
                + "redirect_uri = 'http://127.0.0.1:49152/oauth/callback', "
                + "code_challenge = REPEAT('B', 43), code_challenge_method = 'S256', "
                + "state_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', "
                + "scope_canonical = 'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                + "status = '" + status + "', requested_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:05:00.000'"
                + cryptoAssignments + lifecycleAssignments;
    }

    private static String invalidOAuthCodeInsert(String marker, String status) {
        return "INSERT INTO fbs_oauth_authorization_code SET "
                + "code_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "authorization_request_id = 1, client_id = REPEAT('A', 43), "
                + "redirect_uri = 'http://127.0.0.1:49152/oauth/callback', "
                + "code_challenge = REPEAT('B', 43), code_challenge_method = 'S256', "
                + "issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', "
                + "scope_canonical = 'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                + "status = '" + status + "', issued_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:01:00.000'";
    }

    private static String invalidOAuthFamilyInsert(
            String familyId,
            String status,
            String lifecycleAssignments) {
        return "INSERT INTO fbs_oauth_token_family SET "
                + "family_id = '" + familyId + "', origin_authorization_code_id = 1, "
                + "client_id = REPEAT('A', 43), enterprise_id = 1001, "
                + "member_id = 101, user_id = 501, "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "scope_canonical = 'identity.read entitlement.read board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "status = '" + status + "', current_refresh_generation = 0, "
                + "issued_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:05:00.000'"
                + lifecycleAssignments;
    }

    private static String invalidOAuthReceiptInsert(
            String marker,
            String action,
            String objectAssignments,
            boolean identityBound) {
        String identityAssignments = identityBound
                ? ", enterprise_id = 1001, member_id = 101, user_id = 501, "
                        + "principal_subject_digest = UNHEX(REPEAT('51', 32))"
                : "";
        return "INSERT INTO fbs_oauth_receipt SET "
                + "receipt_id = 'receipt-invalid-" + marker + "', "
                + "action = '" + action + "', client_id = REPEAT('A', 43), "
                + "actor_type = 'SYSTEM', actor_subject_digest = UNHEX(REPEAT('52', 32)), "
                + "correlation_id = 'correlation-invalid-" + marker + "', "
                + "payload_digest = UNHEX(REPEAT('53', 32)), "
                + "evidence_level = 'ACTION_COMPLETED'"
                + identityAssignments + objectAssignments;
    }

    private static void assertOAuthMigrationRejectsSameNameCheckDriftAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = oauthMigrationLockFreeSql();
        execute(
                "ALTER TABLE fbs_oauth_client DROP CHECK chk_oauth_client_status",
                "ALTER TABLE fbs_oauth_client ADD CONSTRAINT chk_oauth_client_status "
                        + "CHECK (1 = 1)");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("exact CHECK clause contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        resetOAuthFoundation(oauthMigration);
    }

    private static void assertOAuthMigrationRejectsColumnShapeDriftAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = oauthMigrationLockFreeSql();
        execute("ALTER TABLE fbs_oauth_receipt MODIFY correlation_id "
                + "VARCHAR(127) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("exact column metadata contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        resetOAuthFoundation(oauthMigration);
    }

    private static void assertOAuthMigrationRejectsGeneratedExpressionDriftAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = oauthMigrationLockFreeSql();
        execute(
                "ALTER TABLE fbs_oauth_token_family ADD INDEX "
                        + "tmp_oauth_family_entitlement (enterprise_id, member_id, product_code)",
                "ALTER TABLE fbs_oauth_token_family DROP INDEX uk_oauth_family_live_slot",
                "ALTER TABLE fbs_oauth_token_family DROP COLUMN lifecycle_slot",
                "ALTER TABLE fbs_oauth_token_family ADD COLUMN lifecycle_slot "
                        + "VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin "
                        + "GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' "
                        + "THEN 'ACTIVE' ELSE NULL END) STORED AFTER status",
                "ALTER TABLE fbs_oauth_token_family ADD UNIQUE INDEX "
                        + "uk_oauth_family_live_slot "
                        + "(enterprise_id, member_id, product_code, source_code, "
                        + "connector_code, lifecycle_slot)");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("exact column metadata contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        resetOAuthFoundation(oauthMigration);
    }

    private static void assertOAuthMigrationRejectsIndexVisibilityDriftAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = oauthMigrationLockFreeSql();
        execute("ALTER TABLE fbs_oauth_client "
                + "ALTER INDEX idx_oauth_client_status_expiry INVISIBLE");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("exact visible full-index contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        resetOAuthFoundation(oauthMigration);
    }

    private static void assertOAuthMigrationRejectsForeignKeyActionDriftAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = oauthMigrationLockFreeSql();
        execute(
                "ALTER TABLE fbs_oauth_authorization_request "
                        + "DROP FOREIGN KEY fk_oauth_request_client_redirect",
                "ALTER TABLE fbs_oauth_authorization_request "
                + "ADD CONSTRAINT fk_oauth_request_client_redirect "
                + "FOREIGN KEY (client_id, redirect_uri) "
                + "REFERENCES fbs_oauth_client (client_id, redirect_uri) "
                + "ON UPDATE RESTRICT ON DELETE CASCADE");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("foreign-key contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        resetOAuthFoundation(oauthMigration);
    }

    private static String oauthMigrationLockFreeSql() {
        return "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_oauth_foundation_v1'), 256))";
    }

    private static void resetOAuthFoundation(Path oauthMigration) throws Exception {
        execute(
                "DROP TABLE fbs_oauth_receipt",
                "DROP TABLE fbs_oauth_token",
                "DROP TABLE fbs_oauth_token_family",
                "DROP TABLE fbs_oauth_authorization_code",
                "DROP TABLE fbs_oauth_authorization_request",
                "DROP TABLE fbs_oauth_client",
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + OAUTH_MIGRATION_VERSION + "'");
        executeMigration(oauthMigration);
    }

    private static void assertOAuthMigrationRejectsMissingImmutabilityTriggerAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_oauth_foundation_v1'), 256))";
        execute("DROP TRIGGER trg_oauth_receipt_no_delete");
        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("immutability trigger contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));

        execute("CREATE TRIGGER trg_oauth_receipt_no_delete "
                + "BEFORE DELETE ON fbs_oauth_receipt FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'");
        executeMigration(oauthMigration);
    }

    private static void assertOAuthMigrationRejectsTransplantedImmutabilityTriggersAndReleasesLock(
            Path oauthMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_oauth_foundation_v1'), 256))";
        execute(
                "DROP TRIGGER trg_oauth_receipt_no_update",
                "DROP TRIGGER trg_oauth_receipt_no_delete",
                "CREATE TRIGGER trg_oauth_receipt_no_update "
                        + "BEFORE UPDATE ON fbs_oauth_client FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'",
                "CREATE TRIGGER trg_oauth_receipt_no_delete "
                        + "BEFORE DELETE ON fbs_oauth_client FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'");

        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(oauthMigration));
        assertTrue(drift.getMessage().contains("immutability trigger contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));

        execute(
                "DROP TRIGGER trg_oauth_receipt_no_update",
                "DROP TRIGGER trg_oauth_receipt_no_delete",
                "CREATE TRIGGER trg_oauth_receipt_no_update "
                        + "BEFORE UPDATE ON fbs_oauth_receipt FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'",
                "CREATE TRIGGER trg_oauth_receipt_no_delete "
                        + "BEFORE DELETE ON fbs_oauth_receipt FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT = 'OAuth receipts are immutable'");
        executeMigration(oauthMigration);
    }

    private static void assertConnectorMigrationRejectsPartialStatesAndReleasesLock(
            Path connectorMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_connector_binding_v1'), 256))";
        execute("CREATE TABLE fbs_connector_binding ("
                + "id BIGINT UNSIGNED NOT NULL PRIMARY KEY) ENGINE=InnoDB");
        SQLException orphanTable = assertThrows(
                SQLException.class, () -> executeMigration(connectorMigration));
        assertTrue(orphanTable.getMessage().contains(
                "tables exist without the exact migration receipt"), orphanTable.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + CONNECTOR_MIGRATION_VERSION + "'"));
        execute("DROP TABLE fbs_connector_binding");

        execute("INSERT INTO u3w_schema_migration (version, description) VALUES ('"
                + CONNECTOR_MIGRATION_VERSION + "', "
                + "'Independent Board authoritative Connector binding, scope and receipt tables')");
        SQLException orphanReceipt = assertThrows(
                SQLException.class, () -> executeMigration(connectorMigration));
        assertTrue(orphanReceipt.getMessage().contains(
                "migration receipt exists but its three-table set is incomplete"),
                orphanReceipt.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        execute("DELETE FROM u3w_schema_migration WHERE version = '"
                + CONNECTOR_MIGRATION_VERSION + "'");
    }

    private static void assertConnectorMigrationRejectsCheckDriftAndReleasesLock(
            Path connectorMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_connector_binding_v1'), 256))";
        execute(
                "ALTER TABLE fbs_connector_binding "
                        + "DROP CHECK chk_connector_binding_source",
                "ALTER TABLE fbs_connector_binding ADD CONSTRAINT "
                        + "chk_connector_binding_source "
                        + "CHECK (source_code = 'WORKBUDDY' OR 1 = 1)");
        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(connectorMigration));
        assertTrue(drift.getMessage().contains("exact fifteen-check contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));

        execute(
                "DROP TABLE fbs_connector_binding_receipt",
                "DROP TABLE fbs_connector_binding_scope",
                "DROP TABLE fbs_connector_binding",
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + CONNECTOR_MIGRATION_VERSION + "'");
        executeMigration(connectorMigration);
    }

    private static void assertConnectorMigrationRejectsMissingImmutabilityTriggerAndReleasesLock(
            Path connectorMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_connector_binding_v1'), 256))";
        execute("DROP TRIGGER trg_connector_binding_receipt_no_delete");
        SQLException drift = assertThrows(
                SQLException.class, () -> executeMigration(connectorMigration));
        assertTrue(drift.getMessage().contains("immutability trigger contract has drifted"),
                drift.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));

        execute("CREATE TRIGGER trg_connector_binding_receipt_no_delete "
                + "BEFORE DELETE ON fbs_connector_binding_receipt FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'Connector binding receipts are immutable'");
        executeMigration(connectorMigration);
    }

    private static void executeMigration(Path path) throws Exception {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        int statementNumber = 0;
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
                    String sql = statement.substring(0, end);
                    statementNumber++;
                    try {
                        jdbc.execute(sql);
                    } catch (SQLException failure) {
                        String firstLine = sql.lines().findFirst().orElse("<empty>").trim();
                        throw new SQLException(
                                "Migration statement " + statementNumber + " failed at '"
                                        + firstLine + "': " + failure.getMessage(),
                                failure.getSQLState(), failure.getErrorCode(), failure);
                    }
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

    private static void printConnectorCheckContractForDiagnosis() throws SQLException {
        String sql = "SELECT tc.table_name, tc.constraint_name, cc.check_clause "
                + "FROM information_schema.table_constraints tc "
                + "INNER JOIN information_schema.check_constraints cc "
                + "ON cc.constraint_schema = tc.constraint_schema "
                + "AND cc.constraint_name = tc.constraint_name "
                + "WHERE tc.constraint_schema = DATABASE() "
                + "AND tc.constraint_type = 'CHECK' "
                + "AND tc.table_name IN ('fbs_connector_binding', "
                + "'fbs_connector_binding_scope', 'fbs_connector_binding_receipt') "
                + "ORDER BY tc.table_name, tc.constraint_name";
        try (Connection connection = dataSource.getConnection();
             Statement jdbc = connection.createStatement();
             ResultSet rows = jdbc.executeQuery(sql)) {
            while (rows.next()) {
                System.err.printf(
                        "Connector CHECK diagnostic: %s.%s = %s%n",
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3));
            }
        }
        String indexSql = "SELECT table_name, index_name, non_unique, index_type, "
                + "MIN(is_visible), "
                + "GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL')) "
                + "ORDER BY seq_in_index SEPARATOR ',') "
                + "FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() "
                + "AND table_name IN ('fbs_connector_binding', "
                + "'fbs_connector_binding_scope', 'fbs_connector_binding_receipt') "
                + "GROUP BY table_name, index_name, non_unique, index_type "
                + "ORDER BY table_name, index_name";
        try (Connection connection = dataSource.getConnection();
             Statement jdbc = connection.createStatement();
             ResultSet rows = jdbc.executeQuery(indexSql)) {
            while (rows.next()) {
                System.err.printf(
                        "Connector INDEX diagnostic: %s.%s unique=%s type=%s visible=%s columns=%s%n",
                        rows.getString(1), rows.getString(2), rows.getInt(3) == 0,
                        rows.getString(4), rows.getString(5), rows.getString(6));
            }
        }
    }

    private static void printOAuthIndexContractForDiagnosis() throws SQLException {
        String sql = "SELECT table_name, index_name, non_unique, index_type, "
                + "MIN(is_visible), "
                + "GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL')) "
                + "ORDER BY seq_in_index SEPARATOR ',') "
                + "FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() "
                + "AND table_name IN ('fbs_oauth_client', "
                + "'fbs_oauth_authorization_request', 'fbs_oauth_authorization_code', "
                + "'fbs_oauth_token_family', 'fbs_oauth_token', 'fbs_oauth_receipt') "
                + "GROUP BY table_name, index_name, non_unique, index_type "
                + "ORDER BY table_name, index_name";
        try (Connection connection = dataSource.getConnection();
             Statement jdbc = connection.createStatement();
             ResultSet rows = jdbc.executeQuery(sql)) {
            while (rows.next()) {
                System.err.printf(
                        "OAuth INDEX diagnostic: %s.%s unique=%s type=%s visible=%s columns=%s%n",
                        rows.getString(1), rows.getString(2), rows.getInt(3) == 0,
                        rows.getString(4), rows.getString(5), rows.getString(6));
            }
        }
    }

    private static void printOAuthExactContractEvidence() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement jdbc = connection.createStatement()) {
            jdbc.execute("SET SESSION group_concat_max_len = 1048576");
            printOAuthDigest(jdbc, "columns_raw", "SELECT SHA2(GROUP_CONCAT(CONCAT("
                    + "'T:', HEX(CAST(table_name AS BINARY)), "
                    + "'|O:', LPAD(ordinal_position, 3, '0'), "
                    + "'|N:', HEX(CAST(column_name AS BINARY)), "
                    + "'|Y:', HEX(CAST(column_type AS BINARY)), "
                    + "'|U:', HEX(CAST(is_nullable AS BINARY)), "
                    + "'|D:', IF(column_default IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(column_default AS BINARY)))), "
                    + "'|C:', IF(character_set_name IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(character_set_name AS BINARY)))), "
                    + "'|L:', IF(collation_name IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(collation_name AS BINARY)))), "
                    + "'|E:', HEX(CAST(extra AS BINARY)), "
                    + "'|G:', IF(generation_expression IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(generation_expression AS BINARY))))) "
                    + "ORDER BY table_name, ordinal_position SEPARATOR 0x0A), 256) "
                    + "FROM information_schema.columns WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                    + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                    + "'fbs_oauth_token','fbs_oauth_receipt')");
            printOAuthDigest(jdbc, "indexes_raw", "SELECT SHA2(GROUP_CONCAT(CONCAT("
                    + "'T:', HEX(CAST(table_name AS BINARY)), "
                    + "'|I:', HEX(CAST(index_name AS BINARY)), "
                    + "'|U:', non_unique, '|Y:', HEX(CAST(index_type AS BINARY)), "
                    + "'|V:', HEX(CAST(is_visible AS BINARY)), '|S:', seq_in_index, "
                    + "'|N:', IF(column_name IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(column_name AS BINARY)))), "
                    + "'|X:', IF(expression IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(expression AS BINARY)))), "
                    + "'|C:', IF(collation IS NULL, 'N', "
                    + "CONCAT('V:', HEX(CAST(collation AS BINARY)))), "
                    + "'|P:', IF(sub_part IS NULL, 'N', CONCAT('V:', sub_part)), "
                    + "'|Q:', HEX(CAST(nullable AS BINARY))) "
                    + "ORDER BY table_name, index_name, seq_in_index SEPARATOR 0x0A), 256) "
                    + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                    + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                    + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                    + "'fbs_oauth_token','fbs_oauth_receipt')");
            printOAuthDigest(jdbc, "foreign_keys_raw", "SELECT SHA2(GROUP_CONCAT(CONCAT("
                    + "'T:', HEX(CAST(rc.table_name AS BINARY)), "
                    + "'|C:', HEX(CAST(rc.constraint_name AS BINARY)), "
                    + "'|S:', IF(rc.unique_constraint_schema = DATABASE(), 'SAME', 'OTHER'), "
                    + "'|K:', HEX(CAST(rc.unique_constraint_name AS BINARY)), "
                    + "'|R:', HEX(CAST(rc.referenced_table_name AS BINARY)), "
                    + "'|U:', HEX(CAST(rc.update_rule AS BINARY)), "
                    + "'|D:', HEX(CAST(rc.delete_rule AS BINARY)), "
                    + "'|M:', HEX(CAST(rc.match_option AS BINARY)), "
                    + "'|O:', kcu.ordinal_position, "
                    + "'|N:', HEX(CAST(kcu.column_name AS BINARY)), "
                    + "'|Q:', IF(kcu.referenced_table_schema = DATABASE(), 'SAME', 'OTHER'), "
                    + "'|P:', HEX(CAST(kcu.referenced_column_name AS BINARY)), "
                    + "'|I:', IF(kcu.position_in_unique_constraint IS NULL, 'N', "
                    + "CONCAT('V:', kcu.position_in_unique_constraint))) "
                    + "ORDER BY rc.table_name, rc.constraint_name, kcu.ordinal_position SEPARATOR 0x0A), 256) "
                    + "FROM information_schema.referential_constraints rc "
                    + "INNER JOIN information_schema.key_column_usage kcu "
                    + "ON kcu.constraint_schema = rc.constraint_schema "
                    + "AND kcu.table_name = rc.table_name "
                    + "AND kcu.constraint_name = rc.constraint_name "
                    + "WHERE rc.constraint_schema = DATABASE() "
                    + "AND rc.unique_constraint_schema = DATABASE() "
                    + "AND kcu.referenced_table_schema = DATABASE() "
                    + "AND rc.table_name IN ('fbs_oauth_authorization_request',"
                    + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                    + "'fbs_oauth_token','fbs_oauth_receipt')");
            printOAuthDigest(jdbc, "checks_raw", "SELECT SHA2(GROUP_CONCAT(CONCAT("
                    + "'T:', HEX(CAST(table_name AS BINARY)), "
                    + "'|C:', HEX(CAST(constraint_name AS BINARY)), "
                    + "'|E:', HEX(CAST(enforced AS BINARY)), "
                    + "'|X:', HEX(CAST(check_clause AS BINARY))) "
                    + "ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256) FROM ("
                    + "SELECT tc.table_name, tc.constraint_name, tc.enforced, cc.check_clause "
                    + "FROM information_schema.table_constraints tc "
                    + "INNER JOIN information_schema.check_constraints cc "
                    + "ON cc.constraint_schema = tc.constraint_schema "
                    + "AND cc.constraint_name = tc.constraint_name "
                    + "WHERE tc.constraint_schema = DATABASE() AND tc.constraint_type = 'CHECK' "
                    + "AND tc.table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                    + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                    + "'fbs_oauth_token','fbs_oauth_receipt')) exact_checks");

            try (ResultSet rows = jdbc.executeQuery("SELECT table_name, column_name, "
                    + "generation_expression FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND generation_expression <> '' "
                    + "AND table_name IN ('fbs_oauth_token_family','fbs_oauth_token') "
                    + "ORDER BY table_name, column_name")) {
                while (rows.next()) {
                    System.err.printf("OAuth generated expression: %s.%s=%s%n",
                            rows.getString(1), rows.getString(2), rows.getString(3));
                }
            }
        }
    }

    private static void printOAuthDigest(Statement jdbc, String label, String sql)
            throws SQLException {
        try (ResultSet rows = jdbc.executeQuery(sql)) {
            assertTrue(rows.next());
            System.err.printf("OAuth exact contract digest: %s=%s%n", label, rows.getString(1));
        }
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
                    .getResources("classpath*:mapper/board/IndependentBoard*Mapper.xml"));
            return factory.getObject();
        }

        @Bean
        IndependentBoardMapper independentBoardMapper(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory).getMapper(IndependentBoardMapper.class);
        }

        @Bean
        IndependentBoardOAuthMapper independentBoardOAuthMapper(
                SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory)
                    .getMapper(IndependentBoardOAuthMapper.class);
        }

        @Bean
        IndependentBoardOAuthClientRegistrationService oauthClientRegistrationService(
                IndependentBoardOAuthMapper mapper) {
            return new IndependentBoardOAuthClientRegistrationService(mapper);
        }

        @Bean
        IndependentBoardConnectorProperties connectorProperties() {
            IndependentBoardConnectorProperties properties = new IndependentBoardConnectorProperties();
            properties.setIssuerUri(CONNECTOR_ISSUER);
            properties.setResourceUri(CONNECTOR_RESOURCE);
            return properties;
        }

        @Bean
        IndependentBoardConnectorBindingService connectorBindingService(
                IndependentBoardMapper mapper,
                IndependentBoardConnectorProperties properties) {
            return new IndependentBoardConnectorBindingService(mapper, properties);
        }

        @Bean
        IndependentBoardEntitlementService entitlementService(
                IndependentBoardMapper mapper,
                IndependentBoardConnectorBindingService connectorBindingService) {
            return new IndependentBoardEntitlementService(mapper, connectorBindingService);
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
