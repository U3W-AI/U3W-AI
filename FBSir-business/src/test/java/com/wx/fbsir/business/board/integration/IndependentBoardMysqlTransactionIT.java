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
import com.wx.fbsir.business.board.dto.BoardProductPlanAdminView;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthConsentIntent;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationCode;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthAuthorizationRequest;
import com.wx.fbsir.business.board.oauth.domain.BoardOAuthTokenFamily;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationRequest;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationResponse;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthTokenExchangeResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthClientRegistrationService;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthTokenExchangeFacade;
import com.wx.fbsir.business.board.oauth.service.IndependentBoardOAuthTokenExchangeMysqlTestConfiguration;
import com.wx.fbsir.business.board.portal.mapper.IndependentBoardPortalReadMapper;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalConnectorBindingRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthClientRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalOAuthFamilyRow;
import com.wx.fbsir.business.board.portal.persistence.BoardPortalTenantRow;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
import com.wx.fbsir.business.board.service.IndependentBoardConnectorBindingService;
import com.wx.fbsir.business.board.service.IndependentBoardDashboardService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingTransactionService;
import com.wx.fbsir.business.board.service.IndependentBoardOAuthFirstProtectedRequestFacade;
import com.wx.fbsir.business.board.service.IndependentBoardOAuthFirstProtectedRequestMysqlTestConfiguration;
import com.wx.fbsir.business.board.service.LegacyConnectorBindingTestAdapter;
import com.wx.fbsir.common.exception.ServiceException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
    private static final String OAUTH_PROVENANCE_MIGRATION_VERSION =
            "20260721_independent_board_oauth_receipt_provenance_v1";
    private static final String OAUTH_PROVENANCE_RUNNING =
            "RUNNING:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness";
    private static final String OAUTH_PROVENANCE_APPLIED =
            "APPLIED:Independent Board OAuth TOKEN_FAMILY_CREATED provenance uniqueness";
    private static final String OAUTH_CONSENT_MIGRATION_VERSION =
            "20260721_independent_board_oauth_consent_intent_lineage_v1";
    private static final String OAUTH_CONSENT_RUNNING =
            "RUNNING:Independent Board OAuth consent intent lineage";
    private static final String OAUTH_CONSENT_APPLIED =
            "APPLIED:Independent Board OAuth consent intent lineage";
    private static final String PLAN_POLICY_MIGRATION_VERSION =
            "20260722_independent_board_plan_policy_v1";
    private static final String CONNECTOR_ISSUER = "https://api2.u3w.com";
    private static final String CONNECTOR_RESOURCE = "https://api2.u3w.com/fbs-mcp/mcp";
    private static final long TENANT_ONE = 1001L;
    private static final long MEMBER_ONE = 101L;
    private static final long USER_ONE = 501L;
    private static final long TENANT_TWO = 1002L;
    private static final long MEMBER_TWO = 102L;
    private static final long USER_TWO = 502L;
    private static final long TENANT_ONE_MEMBER_TWO = 103L;
    private static final BoardOAuthCrypto OAUTH_CRYPTO = new BoardOAuthCrypto();

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static IndependentBoardEntitlementService entitlementService;
    private static IndependentBoardConnectorBindingService connectorBindingService;
    private static IndependentBoardMeetingService meetingService;
    private static IndependentBoardDashboardService dashboardService;
    private static IndependentBoardOAuthClientRegistrationService oauthClientRegistrationService;
    private static IndependentBoardOAuthTokenExchangeFacade oauthTokenExchangeFacade;
    private static IndependentBoardOAuthTokenExchangeMysqlTestConfiguration.SwitchableClock
            oauthTokenExchangeClock;
    private static IndependentBoardOAuthFirstProtectedRequestFacade
            oauthFirstProtectedRequestFacade;
    private static IndependentBoardOAuthMapper oauthMapper;
    private static IndependentBoardPortalReadMapper portalReadMapper;
    private static IndependentBoardMapper independentBoardMapper;
    private static LegacyConnectorBindingTestAdapter legacyConnectorBindingTestAdapter;

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

        context = new AnnotationConfigApplicationContext(
                TestConfiguration.class,
                IndependentBoardOAuthTokenExchangeMysqlTestConfiguration.class,
                IndependentBoardOAuthFirstProtectedRequestMysqlTestConfiguration.class);
        dataSource = context.getBean(DataSource.class);
        entitlementService = context.getBean(IndependentBoardEntitlementService.class);
        connectorBindingService = context.getBean(IndependentBoardConnectorBindingService.class);
        meetingService = context.getBean(IndependentBoardMeetingService.class);
        dashboardService = context.getBean(IndependentBoardDashboardService.class);
        oauthClientRegistrationService = context.getBean(
                IndependentBoardOAuthClientRegistrationService.class);
        oauthTokenExchangeFacade = context.getBean(
                IndependentBoardOAuthTokenExchangeFacade.class);
        oauthTokenExchangeClock = context.getBean(
                IndependentBoardOAuthTokenExchangeMysqlTestConfiguration.SwitchableClock.class);
        oauthFirstProtectedRequestFacade = context.getBean(
                IndependentBoardOAuthFirstProtectedRequestFacade.class);
        oauthMapper = context.getBean(IndependentBoardOAuthMapper.class);
        portalReadMapper = context.getBean(IndependentBoardPortalReadMapper.class);
        independentBoardMapper = context.getBean(IndependentBoardMapper.class);
        legacyConnectorBindingTestAdapter = context.getBean(
                LegacyConnectorBindingTestAdapter.class);

        assertTrue(AopUtils.isAopProxy(entitlementService),
                "entitlement service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(connectorBindingService),
                "connector binding service must be a Spring transaction proxy");
        // The orchestration service deliberately delegates transactional work
        // to IndependentBoardMeetingTransactionService. Requiring the facade
        // itself to be proxied is stale and would reject the intended split.
        assertTrue(AopUtils.isAopProxy(dashboardService),
                "dashboard service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(oauthClientRegistrationService),
                "OAuth client registration service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(context.getBean(
                        IndependentBoardOAuthTokenExchangeMysqlTestConfiguration.RUNNER_BEAN_NAME)),
                "OAuth token exchange runner must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(context.getBean(
                        IndependentBoardOAuthFirstProtectedRequestMysqlTestConfiguration
                                .RUNNER_BEAN_NAME)),
                "OAuth first-protected-request runner must be a Spring transaction proxy");
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

    private static BoardConnectorBindingSnapshot confirmLegacyProtectedRequest(
            IndependentBoardConnectorBindingService ignoredProductionService,
            BoardConnectorProtectedRequestAttestation attestation,
            Long actorUserId) {
        return legacyConnectorBindingTestAdapter.confirmLegacyProtectedRequest(
                attestation, actorUserId);
    }

    @BeforeEach
    void resetBusinessRows() throws Exception {
        oauthTokenExchangeClock.reset();
        execute(
                "DROP TRIGGER IF EXISTS independent_board_it_fail_finalize",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_receipt",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_binding_receipt",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_oauth_receipt",
                "DROP TRIGGER IF EXISTS independent_board_it_fail_oauth_successor_family",
                "TRUNCATE TABLE fbs_oauth_receipt",
                "DELETE FROM fbs_oauth_token",
                "DELETE FROM fbs_oauth_token_family",
                "DELETE FROM fbs_oauth_authorization_code",
                "DELETE FROM fbs_oauth_authorization_request",
                "DELETE FROM fbs_oauth_client",
                "TRUNCATE TABLE fbs_usage_operation_policy_receipt",
                "DELETE FROM fbs_usage_operation",
                "DELETE FROM fbs_usage_budget",
                "TRUNCATE TABLE fbs_connector_binding_receipt",
                "DELETE FROM fbs_connector_binding_scope",
                "DELETE FROM fbs_connector_binding",
                "TRUNCATE TABLE fbs_entitlement_receipt",
                "DELETE FROM fbs_product_entitlement",
                "DELETE FROM fbs_enterprise_member",
                "DELETE FROM fbs_enterprise",
                "DELETE FROM sys_role_menu WHERE menu_id IN (990001, 990002)",
                "DELETE FROM sys_menu WHERE menu_id IN (990001, 990002)",
                "DELETE FROM sys_user_role WHERE role_id = 20 OR user_id = 1",
                "DELETE FROM sys_role WHERE role_id = 20",
                "DELETE FROM sys_user WHERE user_id = 1",
                "UPDATE sys_user SET status = '0', del_flag = '0' "
                        + "WHERE user_id IN (" + USER_ONE + ", " + USER_TWO + ")",
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
    void planCatalogMapperPreservesStableOrderPolicyMetadataAndSafeProjection() {
        List<BoardProductPlan> rawPlans = independentBoardMapper.selectPlansByProduct(
                IndependentBoardEntitlementService.PRODUCT_CODE);

        assertEquals(2, rawPlans.size());
        assertPlan(rawPlans.get(0), "BOARD_FREE", "Independent Board Free",
                false, false, 1, 5, 3, false);
        assertPlan(rawPlans.get(1), "BOARD_VIP", "Independent Board VIP",
                true, true, 5, 30, null, true);

        List<BoardProductPlanAdminView> projectedPlans = entitlementService.listPlans();
        assertEquals(List.of("BOARD_FREE", "BOARD_VIP"), projectedPlans.stream()
                .map(BoardProductPlanAdminView::planCode)
                .toList());
        assertEquals(rawPlans.get(0).getUpdatedAt(), projectedPlans.get(0).updatedAt());
        assertEquals(rawPlans.get(1).getUpdatedAt(), projectedPlans.get(1).updatedAt());
    }

    private static void assertPlan(
            BoardProductPlan plan,
            String planCode,
            String planName,
            boolean vip,
            boolean connectorRequired,
            int dailyMeetingLimit,
            int agendaLimit,
            Integer seatLimit,
            boolean secretaryEnabled) {
        assertNotNull(plan);
        assertEquals(IndependentBoardEntitlementService.PRODUCT_CODE, plan.getProductCode());
        assertEquals(planCode, plan.getPlanCode());
        assertEquals(planName, plan.getPlanName());
        assertEquals(vip, plan.getVip());
        assertEquals(connectorRequired, plan.getConnectorRequired());
        assertEquals(dailyMeetingLimit, plan.getDailyMeetingLimit());
        assertEquals(agendaLimit, plan.getAgendaLimit());
        assertEquals(seatLimit, plan.getSeatLimit());
        assertEquals(secretaryEnabled, plan.getSecretaryEnabled());
        assertEquals("ACTIVE", plan.getStatus());
        assertEquals(1L, plan.getVersion());
        assertNotNull(plan.getUpdatedAt());
        assertNotNull(plan.getPolicyReceiptId());
        assertTrue(plan.getPolicyReceiptId().startsWith("plan-policy-baseline-"));
        assertNotNull(plan.getPolicyDigest());
        assertTrue(plan.getPolicyDigest().matches("[0-9a-f]{64}"));
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
    void activationAndExchangeQueueOnSameEnterpriseAuthoritySlotWithoutDeadlock()
            throws Exception {
        grantVip();
        OAuthClientFixture clientA = registerOAuthClient(55201, "lock-order-a");
        OAuthLineage lineageA = createOAuthLineage(
                clientA, BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthTokenExchangeResult issuedA = oauthTokenExchangeFacade.exchange(
                lineageA.command());
        String familyA = familyIdForCode(lineageA.codeId());

        OAuthClientFixture clientB = registerOAuthClient(55202, "lock-order-b");
        OAuthLineage lineageB = createOAuthLineage(
                clientB, BoardOAuthConsentIntent.FIRST_CONNECT);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Connection blocker = dataSource.getConnection();
        boolean blockerCommitted = false;
        AsyncOAuthAttempt activation;
        AsyncOAuthAttempt exchange;
        try {
            lockEnterpriseAuthority(blocker);
            Future<AsyncOAuthAttempt> activationFuture = pool.submit(() ->
                    captureActivation(issuedA.rawAccessToken()));
            awaitMysqlRowLockWaitersOrFailEarly(
                    "fbs_enterprise", "PRIMARY", 1, activationFuture, "activation");
            Future<AsyncOAuthAttempt> exchangeFuture = pool.submit(() ->
                    captureExchange(lineageB));
            awaitMysqlRowLockWaitersOrFailEarly(
                    "fbs_enterprise", "PRIMARY", 2, exchangeFuture, "exchange");

            blocker.commit();
            blockerCommitted = true;
            activation = activationFuture.get(15, TimeUnit.SECONDS);
            exchange = exchangeFuture.get(15, TimeUnit.SECONDS);
        } finally {
            if (!blockerCommitted) {
                blocker.rollback();
            }
            blocker.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertFalse(activation.succeeded() && exchange.succeeded(),
                "activation and a stale FIRST_CONNECT exchange cannot both commit");
        assertTrue(activation.succeeded() || exchange.succeeded(),
                "one canonical lock-order contender must commit");
        assertNotDatabaseDeadlock(activation.failure());
        assertNotDatabaseDeadlock(exchange.failure());

        if (activation.succeeded()) {
            assertOAuthProtocolFailure(
                    exchange,
                    "invalid_request",
                    "OAUTH_TOKEN_EXCHANGE_CONSENT_INTENT_DRIFT");
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding "
                    + "WHERE enterprise_id = " + TENANT_ONE
                    + " AND member_id = " + MEMBER_ONE + " AND status = 'ACTIVE'"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                    + "WHERE family_id = '" + familyA + "' AND status = 'ACTIVE'"));
            assertFreshLineageUnchanged(lineageB);
            assertEquals(0, familyCountForCode(lineageB.codeId()));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                    + "WHERE family_id = '" + familyA
                    + "' AND action = 'TOKEN_FAMILY_ACTIVATED'"));
        } else {
            assertActivationLostToPendingFamilyTransition(activation);
            BoardOAuthTokenExchangeResult issuedB =
                    (BoardOAuthTokenExchangeResult) exchange.value();
            assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding "
                    + "WHERE enterprise_id = " + TENANT_ONE
                    + " AND member_id = " + MEMBER_ONE));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                    + "WHERE family_id = '" + familyA + "' AND status = 'REVOKED'"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                    + "WHERE origin_authorization_code_id = " + lineageB.codeId()
                    + " AND status = 'PENDING_BINDING'"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                    + "WHERE receipt_id = '" + issuedB.receiptId()
                    + "' AND action = 'TOKEN_FAMILY_CREATED'"));
        }
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND member_id = " + MEMBER_ONE
                + " AND lifecycle_slot IS NOT NULL"));
    }

    @Test
    void crossClientPendingExchangeSupersedesWithCausalReceipt() throws Exception {
        grantVip();
        OAuthLineage lineageA = createOAuthLineage(
                registerOAuthClient(55211, "supersession-a"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        oauthTokenExchangeFacade.exchange(lineageA.command());
        String familyA = familyIdForCode(lineageA.codeId());

        OAuthLineage lineageB = createOAuthLineage(
                registerOAuthClient(55212, "supersession-b"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthTokenExchangeResult issuedB = oauthTokenExchangeFacade.exchange(
                lineageB.command());

        assertCrossClientPendingSupersession(lineageA, familyA, lineageB, issuedB);
    }

    @Test
    void stalePendingClientLocatorFailsBeforeFamilyTokenOrReceiptMutation()
            throws Exception {
        grantVip();
        OAuthLineage lineageA = createOAuthLineage(
                registerOAuthClient(55221, "stale-a"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        OAuthLineage lineageB = createOAuthLineage(
                registerOAuthClient(55222, "stale-b"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        OAuthLineage lineageC = createOAuthLineage(
                registerOAuthClient(55223, "stale-c"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        String familyA = insertBarePendingFamily(lineageA);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection blocker = dataSource.getConnection();
        boolean blockerCommitted = false;
        AsyncOAuthAttempt exchange;
        String familyC;
        try {
            lockEnterpriseAuthority(blocker);
            Future<AsyncOAuthAttempt> exchangeFuture = pool.submit(() ->
                    captureExchange(lineageB));
            awaitMysqlRowLockWaiters("fbs_enterprise", "PRIMARY", 1);

            execute("UPDATE fbs_oauth_token_family SET status = 'REVOKED', "
                    + "terminated_at = GREATEST(issued_at, NOW(3)), version = version + 1 "
                    + "WHERE family_id = '" + familyA + "' AND status = 'PENDING_BINDING'");
            familyC = insertBarePendingFamily(lineageC);

            blocker.commit();
            blockerCommitted = true;
            exchange = exchangeFuture.get(15, TimeUnit.SECONDS);
        } finally {
            if (!blockerCommitted) {
                blocker.rollback();
            }
            blocker.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertOAuthProtocolFailure(
                exchange,
                "invalid_request",
                "OAUTH_TOKEN_EXCHANGE_CONFLICT");
        assertFreshLineageUnchanged(lineageB);
        assertEquals(0, familyCountForCode(lineageB.codeId()));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyA + "' AND status = 'REVOKED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyC + "' AND status = 'PENDING_BINDING'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE action IN ('TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED', "
                + "'AUTHORIZATION_CODE_REPLAY_DETECTED')"));
    }

    @Test
    void successorFamilyInsertFailureRollsBackSupersessionAndSameCodeCanRetry()
            throws Exception {
        grantVip();
        OAuthLineage lineageA = createOAuthLineage(
                registerOAuthClient(55231, "rollback-a"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        oauthTokenExchangeFacade.exchange(lineageA.command());
        String familyA = familyIdForCode(lineageA.codeId());
        OAuthLineage lineageB = createOAuthLineage(
                registerOAuthClient(55232, "rollback-b"),
                BoardOAuthConsentIntent.FIRST_CONNECT);

        execute("CREATE TRIGGER independent_board_it_fail_oauth_successor_family "
                + "BEFORE INSERT ON fbs_oauth_token_family FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'forced OAuth successor family failure'");
        BoardOAuthProtocolException failed = assertThrows(
                BoardOAuthProtocolException.class,
                () -> oauthTokenExchangeFacade.exchange(lineageB.command()));
        assertEquals("temporarily_unavailable", failed.oauthError());
        assertEquals("OAUTH_TOKEN_EXCHANGE_PERSISTENCE_UNAVAILABLE", failed.reasonCode());

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyA
                + "' AND status = 'PENDING_BINDING' AND version = 0"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + familyA + "' AND status = 'ACTIVE'"));
        assertFreshLineageUnchanged(lineageB);
        assertEquals(0, familyCountForCode(lineageB.codeId()));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE action = 'TOKEN_FAMILY_REVOKED'"));

        execute("DROP TRIGGER independent_board_it_fail_oauth_successor_family");
        BoardOAuthTokenExchangeResult issuedB = oauthTokenExchangeFacade.exchange(
                lineageB.command());
        assertCrossClientPendingSupersession(lineageA, familyA, lineageB, issuedB);
    }

    @Test
    void inactiveClientStillCommitsConsumedCodeReplayContainment() throws Exception {
        grantVip();
        OAuthLineage lineage = createOAuthLineage(
                registerOAuthClient(55241, "inactive-client-replay"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        oauthTokenExchangeFacade.exchange(lineage.command());
        String familyId = familyIdForCode(lineage.codeId());

        execute("UPDATE fbs_oauth_client SET status = 'REVOKED', "
                + "terminated_at = GREATEST(registered_at, NOW(3)), version = version + 1 "
                + "WHERE client_id = '" + lineage.clientId() + "'");
        BoardOAuthProtocolException replay = assertThrows(
                BoardOAuthProtocolException.class,
                () -> oauthTokenExchangeFacade.exchange(lineage.command()));
        assertEquals("invalid_grant", replay.oauthError());
        assertEquals("OAUTH_TOKEN_EXCHANGE_CODE_INVALID", replay.reasonCode());

        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_client "
                + "WHERE client_id = '" + lineage.clientId()
                + "' AND status = 'REVOKED' AND version = 1"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyId
                + "' AND status = 'COMPROMISED' AND version = 1"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + familyId + "' AND status = 'REVOKED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyId
                + "' AND action = 'TOKEN_FAMILY_COMPROMISED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyId
                + "' AND action = 'AUTHORIZATION_CODE_REPLAY_DETECTED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt compromised "
                + "INNER JOIN fbs_oauth_receipt replay "
                + "ON replay.family_id = compromised.family_id "
                + "AND replay.correlation_id = compromised.correlation_id "
                + "AND replay.created_at = compromised.created_at "
                + "WHERE compromised.family_id = '" + familyId + "' "
                + "AND compromised.action = 'TOKEN_FAMILY_COMPROMISED' "
                + "AND replay.action = 'AUTHORIZATION_CODE_REPLAY_DETECTED'"));
    }

    @Test
    void freshIssuanceWaitsForEnterpriseDisableAndRejectsWithoutWrites()
            throws Exception {
        grantVip();
        OAuthLineage lineage = createOAuthLineage(
                registerOAuthClient(55251, "inactive-enterprise"),
                BoardOAuthConsentIntent.FIRST_CONNECT);

        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection disable = dataSource.getConnection();
        boolean disableCommitted = false;
        AsyncOAuthAttempt exchange;
        try {
            disable.setAutoCommit(false);
            try (Statement statement = disable.createStatement()) {
                assertEquals(1, statement.executeUpdate(
                        "UPDATE fbs_enterprise SET status = 2 WHERE id = " + TENANT_ONE));
            }
            Future<AsyncOAuthAttempt> exchangeFuture = pool.submit(() ->
                    captureExchange(lineage));
            awaitMysqlRowLockWaiters("fbs_enterprise", "PRIMARY", 1);
            disable.commit();
            disableCommitted = true;
            exchange = exchangeFuture.get(15, TimeUnit.SECONDS);
        } finally {
            if (!disableCommitted) {
                disable.rollback();
            }
            disable.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertOAuthProtocolFailure(
                exchange,
                "access_denied",
                "OAUTH_TOKEN_EXCHANGE_AUTHORITY_NOT_CURRENT");
        assertFreshLineageUnchanged(lineage);
        assertEquals(0, familyCountForCode(lineage.codeId()));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE action IN ('TOKEN_FAMILY_CREATED', 'TOKEN_FAMILY_REVOKED', "
                + "'TOKEN_FAMILY_COMPROMISED', 'AUTHORIZATION_CODE_REPLAY_DETECTED')"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_enterprise_member "
                + "WHERE id = " + MEMBER_ONE + " AND status = 1 AND del_flag = '0'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_product_entitlement "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND member_id = " + MEMBER_ONE
                + " AND status = 'ACTIVE'"));
    }

    @Test
    void pendingTokenRowWaitCrossesCodeExpiryWithoutMutation() throws Exception {
        grantVip();
        OAuthLineage existing = createOAuthLineage(
                registerOAuthClient(55261, "pending-token-expiry-existing"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        oauthTokenExchangeFacade.exchange(existing.command());
        String existingFamily = familyIdForCode(existing.codeId());
        OAuthLineage contender = createOAuthLineage(
                registerOAuthClient(55262, "pending-token-expiry-contender"),
                BoardOAuthConsentIntent.FIRST_CONNECT);

        Instant initiallyValid = contender.decisionAt().toInstant().plusSeconds(1);
        Instant codeExpiry = contender.decisionAt().toInstant().plusSeconds(60);
        AsyncOAuthAttempt exchange = exchangeAfterObservedOAuthRowWait(
                contender,
                "fbs_oauth_token",
                "SELECT id FROM fbs_oauth_token WHERE family_id = '"
                        + existingFamily + "' ORDER BY id FOR UPDATE",
                2,
                initiallyValid,
                codeExpiry);

        assertOAuthProtocolFailure(
                exchange,
                "invalid_grant",
                "OAUTH_TOKEN_EXCHANGE_CODE_INVALID");
        assertFreshLineageUnchanged(contender);
        assertEquals(0, familyCountForCode(contender.codeId()));
        assertPendingFamilyUnchanged(existingFamily);
    }

    @Test
    void pendingCreationReceiptRowWaitCrossesEntitlementExpiryWithoutMutation()
            throws Exception {
        grantVip();
        OAuthLineage existing = createOAuthLineage(
                registerOAuthClient(55263, "pending-receipt-expiry-existing"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        oauthTokenExchangeFacade.exchange(existing.command());
        String existingFamily = familyIdForCode(existing.codeId());
        OAuthLineage contender = createOAuthLineage(
                registerOAuthClient(55264, "pending-receipt-expiry-contender"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        Instant entitlementExpiry = contender.decisionAt().toInstant().plusSeconds(45);
        execute("UPDATE fbs_product_entitlement SET valid_until = '"
                + java.sql.Timestamp.from(entitlementExpiry)
                + "' WHERE enterprise_id = " + TENANT_ONE
                + " AND member_id = " + MEMBER_ONE
                + " AND product_code = 'FBSIR_INDEPENDENT_BOARD'");

        AsyncOAuthAttempt exchange = exchangeAfterObservedOAuthRowWait(
                contender,
                "fbs_oauth_receipt",
                "SELECT id FROM fbs_oauth_receipt WHERE family_id = '"
                        + existingFamily
                        + "' AND action = 'TOKEN_FAMILY_CREATED' FOR UPDATE",
                1,
                entitlementExpiry.minusMillis(1),
                entitlementExpiry);

        assertOAuthProtocolFailure(
                exchange,
                "access_denied",
                "OAUTH_TOKEN_EXCHANGE_AUTHORITY_NOT_CURRENT");
        assertFreshLineageUnchanged(contender);
        assertEquals(0, familyCountForCode(contender.codeId()));
        assertPendingFamilyUnchanged(existingFamily);
    }

    @Test
    void replayTokenRowWaitCrossesRequestAndCodeExpiryButContains() throws Exception {
        grantVip();
        OAuthLineage lineage = createOAuthLineage(
                registerOAuthClient(55265, "replay-token-expiry"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthTokenExchangeResult issued = oauthTokenExchangeFacade.exchange(
                lineage.command());
        String familyId = familyIdForCode(lineage.codeId());

        AsyncOAuthAttempt replay = exchangeAfterObservedOAuthRowWait(
                lineage,
                "fbs_oauth_token",
                "SELECT id FROM fbs_oauth_token WHERE family_id = '"
                        + familyId + "' ORDER BY id FOR UPDATE",
                2,
                issued.accessTokenExpiresAt()
                        .minusSeconds(issued.expiresInSeconds()).plusSeconds(1),
                lineage.decisionAt().toInstant().plusSeconds(301));

        assertOAuthProtocolFailure(
                replay,
                "invalid_grant",
                "OAUTH_TOKEN_EXCHANGE_CODE_INVALID");
        assertReplayContainmentCommitted(familyId, "REVOKED", true);
    }

    @Test
    void replayCreationReceiptRowWaitCrossesAllExpiriesButContains() throws Exception {
        grantVip();
        OAuthLineage lineage = createOAuthLineage(
                registerOAuthClient(55266, "replay-receipt-all-expiries"),
                BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthTokenExchangeResult issued = oauthTokenExchangeFacade.exchange(
                lineage.command());
        String familyId = familyIdForCode(lineage.codeId());

        AsyncOAuthAttempt replay = exchangeAfterObservedOAuthRowWait(
                lineage,
                "fbs_oauth_receipt",
                "SELECT id FROM fbs_oauth_receipt WHERE family_id = '"
                        + familyId
                        + "' AND action = 'TOKEN_FAMILY_CREATED' FOR UPDATE",
                1,
                issued.accessTokenExpiresAt()
                        .minusSeconds(issued.expiresInSeconds()).plusSeconds(1),
                lineage.decisionAt().toInstant().plusSeconds(
                        TimeUnit.DAYS.toSeconds(32)));

        assertOAuthProtocolFailure(
                replay,
                "invalid_grant",
                "OAUTH_TOKEN_EXCHANGE_CODE_INVALID");
        assertReplayContainmentCommitted(familyId, "EXPIRED", false);
    }

    @Test
    void expiredBindingPermitsExplicitReauthorizationExchange() throws Exception {
        grantVip();
        OAuthClientFixture client = registerOAuthClient(55267, "expired-binding-reauth");
        OAuthLineage firstConnect = createOAuthLineage(
                client, BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthTokenExchangeResult firstTokens = oauthTokenExchangeFacade.exchange(
                firstConnect.command());
        BoardConnectorBindingSnapshot active = oauthFirstProtectedRequestFacade.activate(
                "Bearer " + firstTokens.rawAccessToken(), "initialize");

        execute("UPDATE fbs_connector_binding SET status = 'REVOKED', "
                + "verified_at = DATE_SUB(NOW(3), INTERVAL 3 SECOND), "
                + "last_seen_at = DATE_SUB(NOW(3), INTERVAL 2 SECOND), "
                + "valid_until = DATE_SUB(NOW(3), INTERVAL 1 SECOND), "
                + "revoked_at = DATE_SUB(NOW(3), INTERVAL 1 SECOND), "
                + "version = version + 1 WHERE binding_id = '"
                + active.bindingId() + "'");
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding "
                + "WHERE binding_id = '" + active.bindingId() + "' "
                + "AND status = 'REVOKED' AND valid_until <= NOW(3) "
                + "AND revoked_at IS NOT NULL AND version = 2"));

        OAuthLineage reauthorization = createOAuthLineage(
                client, BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
        BoardOAuthTokenExchangeResult replacement = oauthTokenExchangeFacade.exchange(
                reauthorization.command());
        String replacementFamily = familyIdForCode(reauthorization.codeId());

        assertNotNull(replacement.rawAccessToken());
        assertNotNull(replacement.rawRefreshToken());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + replacementFamily + "' "
                + "AND status = 'PENDING_BINDING' "
                + "AND consent_intent = 'EXPLICIT_REAUTHORIZATION'"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + replacementFamily + "' AND status = 'ACTIVE'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE receipt_id = '" + replacement.receiptId() + "' "
                + "AND family_id = '" + replacementFamily + "' "
                + "AND action = 'TOKEN_FAMILY_CREATED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding "
                + "WHERE binding_id = '" + active.bindingId() + "' "
                + "AND status = 'REVOKED' AND version = 2"));
    }

    @Test
    void portalReadMapperExecutesNormalActivationCurrentFirstAndSubjectDriftOnMysql()
            throws Exception {
        grantVip();
        execute(
                "INSERT INTO fbs_enterprise "
                        + "(id, enterprise_name, contact_name, contact_phone, contact_email, "
                        + "remark, status, del_flag) VALUES "
                        + "(2109, '福帮手国际版企业', 'Portal Fixture Contact', "
                        + "'+86-000-0000', 'portal-fixture@example.invalid', "
                        + "'must not reach the portal row', 1, '0'), "
                        + "(2108, '福帮手百分号%企业', NULL, NULL, NULL, NULL, 1, '0'), "
                        + "(2107, '福帮手下划线_企业', NULL, NULL, NULL, NULL, 1, '0'), "
                        + "(2106, CONCAT('福帮手反斜线', CHAR(92), '企业'), "
                        + "NULL, NULL, NULL, NULL, 1, '0'), "
                        + "(2105, '福帮手禁用企业', NULL, NULL, NULL, NULL, 2, '0'), "
                        + "(2104, '福帮手已删除企业', NULL, NULL, NULL, NULL, 1, '1'), "
                        + "(2205, '稳定窗口企业五', NULL, NULL, NULL, NULL, 1, '0'), "
                        + "(2204, '稳定窗口企业四', NULL, NULL, NULL, NULL, 1, '0'), "
                        + "(2203, '稳定窗口企业三', NULL, NULL, NULL, NULL, 2, '0'), "
                        + "(2202, '稳定窗口企业二', NULL, NULL, NULL, NULL, 1, '0'), "
                        + "(2201, '稳定窗口企业一', NULL, NULL, NULL, NULL, 1, '0')");

        List<BoardPortalTenantRow> activeTenants = portalReadMapper.selectTenants(
                "福帮手", 1, null, null, 100);
        assertEquals(List.of(2109L, 2108L, 2107L, 2106L), activeTenants.stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        List<BoardPortalTenantRow> disabledTenants = portalReadMapper.selectTenants(
                "福帮手", 2, null, null, 100);
        assertEquals(List.of(2105L), disabledTenants.stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        assertTrue(portalReadMapper.selectTenants(
                "已删除", null, null, null, 100).isEmpty());

        List<BoardPortalTenantRow> unicodeMatch = portalReadMapper.selectTenants(
                "国际版", null, null, null, 100);
        assertEquals(1, unicodeMatch.size());
        assertEquals(2109L, unicodeMatch.get(0).getRowId());
        assertEquals(unicodeMatch.get(0).getRowId(), unicodeMatch.get(0).getTenantId());
        assertEquals("福帮手国际版企业", unicodeMatch.get(0).getTenantLabel());
        assertEquals(1, unicodeMatch.get(0).getStatus());
        assertEquals(List.of(2108L), portalReadMapper.selectTenants(
                        "%", null, null, null, 100).stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        assertEquals(List.of(2107L), portalReadMapper.selectTenants(
                        "_", null, null, null, 100).stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        assertEquals(List.of(2106L), portalReadMapper.selectTenants(
                        "\\", null, null, null, 100).stream()
                .map(BoardPortalTenantRow::getTenantId).toList());

        Set<String> tenantRowFields = java.util.Arrays.stream(
                        BoardPortalTenantRow.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("rowId", "tenantId", "tenantLabel", "status"), tenantRowFields);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_enterprise WHERE id = 2109 "
                + "AND contact_name = 'Portal Fixture Contact' "
                + "AND contact_phone = '+86-000-0000' "
                + "AND contact_email = 'portal-fixture@example.invalid' "
                + "AND remark = 'must not reach the portal row'"));

        List<BoardPortalTenantRow> firstWindow = portalReadMapper.selectTenants(
                "稳定窗口", null, null, null, 2);
        assertEquals(List.of(2205L, 2204L), firstWindow.stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        execute("INSERT INTO fbs_enterprise "
                + "(id, enterprise_name, status, del_flag) VALUES "
                + "(2206, '稳定窗口后插入企业', 1, '0')");
        List<BoardPortalTenantRow> stableFirstWindow = portalReadMapper.selectTenants(
                "稳定窗口", null, 2205L, null, 2);
        assertEquals(List.of(2205L, 2204L), stableFirstWindow.stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        List<BoardPortalTenantRow> secondWindow = portalReadMapper.selectTenants(
                "稳定窗口", null, 2205L, 2204L, 2);
        assertEquals(List.of(2203L, 2202L), secondWindow.stream()
                .map(BoardPortalTenantRow::getTenantId).toList());
        List<BoardPortalTenantRow> finalWindow = portalReadMapper.selectTenants(
                "稳定窗口", null, 2205L, 2202L, 2);
        assertEquals(List.of(2201L), finalWindow.stream()
                .map(BoardPortalTenantRow::getTenantId).toList());

        OAuthClientFixture client = registerOAuthClient(55268, "portal-read-mysql");
        Date readAt = Date.from(Instant.now().plusSeconds(2));

        List<BoardPortalOAuthClientRow> clients = portalReadMapper.selectOAuthClients(
                "ACTIVE", readAt, null, null, 101);
        assertEquals(1, clients.size());
        assertEquals(client.clientId(), clients.get(0).getClientRef());
        assertEquals(0, portalReadMapper.selectCurrentAuthority(
                USER_ONE, null, "my:independent-board:connector:view"));
        execute("INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, "
                        + "component, query, route_name, is_frame, is_cache, menu_type, "
                        + "visible, status, perms, icon) VALUES (990001, 'Portal IT Permission', "
                        + "0, 99, 'portal-it', NULL, NULL, NULL, 1, 0, 'F', '1', '0', "
                        + "'my:independent-board:connector:view', '#')",
                "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (10, 990001)");
        assertEquals(1, portalReadMapper.selectCurrentAuthority(
                USER_ONE, null, "my:independent-board:connector:view"));
        execute("UPDATE sys_user SET status = '1' WHERE user_id = " + USER_ONE);
        assertEquals(0, portalReadMapper.selectCurrentAuthority(
                USER_ONE, null, "my:independent-board:connector:view"));
        execute("UPDATE sys_user SET status = '0' WHERE user_id = " + USER_ONE);

        execute(
                "INSERT INTO sys_role (role_id, role_key, status, del_flag) "
                        + "VALUES (20, 'admin', '0', '0')",
                "INSERT INTO sys_user_role (user_id, role_id) VALUES ("
                        + USER_ONE + ", 20)");
        assertEquals(0, portalReadMapper.selectCurrentAuthority(
                USER_ONE, "admin", "board:oauth:client:query"));
        execute("INSERT INTO sys_menu (menu_id, menu_name, parent_id, order_num, path, "
                        + "component, query, route_name, is_frame, is_cache, menu_type, "
                        + "visible, status, perms, icon) VALUES (990002, 'Portal Admin IT Permission', "
                        + "0, 100, 'portal-admin-it', NULL, NULL, NULL, 1, 0, 'F', '1', '0', "
                        + "'board:oauth:client:query', '#')",
                "INSERT INTO sys_role_menu (role_id, menu_id) VALUES (20, 990002)");
        assertEquals(1, portalReadMapper.selectCurrentAuthority(
                USER_ONE, "admin", "board:oauth:client:query"));
        execute("UPDATE sys_menu SET perms = 'BOARD:OAUTH:CLIENT:QUERY' "
                + "WHERE menu_id = 990002");
        assertEquals(0, portalReadMapper.selectCurrentAuthority(
                USER_ONE, "admin", "board:oauth:client:query"));
        execute("UPDATE sys_menu SET perms = 'board:oauth:client:query' "
                + "WHERE menu_id = 990002");
        execute("UPDATE sys_role SET role_key = 'ADMIN' WHERE role_id = 20");
        assertEquals(0, portalReadMapper.selectCurrentAuthority(
                USER_ONE, "admin", "board:oauth:client:query"));
        execute("UPDATE sys_role SET role_key = 'admin' WHERE role_id = 20");
        assertEquals(1, portalReadMapper.selectCurrentAuthority(
                USER_ONE, "admin", "board:oauth:client:query"));
        execute("DELETE FROM sys_role_menu WHERE role_id = 20 AND menu_id = 990002");
        assertEquals(0, portalReadMapper.selectCurrentAuthority(
                USER_ONE, "admin", "board:oauth:client:query"));
        execute(
                "INSERT INTO sys_user (user_id, status, del_flag) VALUES (1, '0', '0')",
                "INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 20)");
        assertEquals(1, portalReadMapper.selectCurrentAuthority(
                1L, "admin", "board:oauth:client:query"));

        OAuthLineage firstConnect = createOAuthLineage(
                client, BoardOAuthConsentIntent.FIRST_CONNECT);
        BoardOAuthTokenExchangeResult firstTokens = oauthTokenExchangeFacade.exchange(
                firstConnect.command());
        readAt = Date.from(Instant.now().plusSeconds(2));
        List<BoardPortalOAuthFamilyRow> pending = portalReadMapper.selectOAuthFamilies(
                TENANT_ONE, MEMBER_ONE, USER_ONE, true, null, readAt,
                null, null, 3);
        assertEquals(1, pending.size());
        assertEquals("PENDING_BINDING", pending.get(0).getEffectiveStatus());
        assertTrue(pending.get(0).getPendingActivationProven());

        BoardConnectorBindingSnapshot active = oauthFirstProtectedRequestFacade.activate(
                "Bearer " + firstTokens.rawAccessToken(), "initialize");
        readAt = Date.from(Instant.now().plusSeconds(2));
        List<BoardPortalConnectorBindingRow> activeBindings =
                portalReadMapper.selectConnectorBindings(
                        TENANT_ONE, MEMBER_ONE, USER_ONE, "ACTIVE", readAt,
                        null, null, 2);
        assertEquals(1, activeBindings.size());
        assertEquals(active.bindingId(), activeBindings.get(0).getBindingRef());
        assertTrue(activeBindings.get(0).getEntitlementActive());
        assertTrue(activeBindings.get(0).getFamilyActive());
        assertEquals(BoardOAuthProfile.REQUIRED_SCOPES, activeBindings.get(0).getScopes());

        for (int index = 0; index < 3; index++) {
            OAuthLineage reauthorization = createOAuthLineage(
                    client, BoardOAuthConsentIntent.EXPLICIT_REAUTHORIZATION);
            oauthTokenExchangeFacade.exchange(reauthorization.command());
        }
        readAt = Date.from(Instant.now().plusSeconds(2));
        List<BoardPortalOAuthFamilyRow> currentFirst = portalReadMapper.selectOAuthFamilies(
                TENANT_ONE, MEMBER_ONE, USER_ONE, true, null, readAt,
                null, null, 3);
        assertEquals(3, currentFirst.size());
        assertTrue(currentFirst.stream().anyMatch(
                row -> "ACTIVE".equals(row.getEffectiveStatus())));
        assertTrue(currentFirst.stream().anyMatch(
                row -> "PENDING_BINDING".equals(row.getEffectiveStatus())));

        execute("UPDATE fbs_connector_binding b "
                + "INNER JOIN fbs_oauth_token_family f ON f.binding_id = b.binding_id "
                + "SET b.valid_until = DATE_SUB(f.expires_at, INTERVAL 1 SECOND) "
                + "WHERE b.binding_id = '" + active.bindingId() + "'");
        readAt = Date.from(Instant.now().plusSeconds(2));
        List<BoardPortalConnectorBindingRow> expiryDriftBindings =
                portalReadMapper.selectConnectorBindings(
                        TENANT_ONE, MEMBER_ONE, USER_ONE, "ACTIVE", readAt,
                        null, null, 2);
        assertEquals(1, expiryDriftBindings.size());
        assertFalse(expiryDriftBindings.get(0).getFamilyActive());
        List<BoardPortalOAuthFamilyRow> expiryDriftFamily =
                portalReadMapper.selectOAuthFamilies(
                        TENANT_ONE, MEMBER_ONE, USER_ONE, false, "ACTIVE", readAt,
                        null, null, 3);
        assertEquals(1, expiryDriftFamily.size());
        assertNotEquals(expiryDriftFamily.get(0).getExpiresAt(),
                expiryDriftFamily.get(0).getCurrentBindingValidUntil());
        execute("UPDATE fbs_connector_binding b "
                + "INNER JOIN fbs_oauth_token_family f ON f.binding_id = b.binding_id "
                + "SET b.valid_until = f.expires_at "
                + "WHERE b.binding_id = '" + active.bindingId() + "'");

        execute("UPDATE fbs_connector_binding SET principal_subject_digest = REPEAT('b', 64) "
                + "WHERE binding_id = '" + active.bindingId() + "'");
        readAt = Date.from(Instant.now().plusSeconds(2));
        List<BoardPortalConnectorBindingRow> driftedBindings =
                portalReadMapper.selectConnectorBindings(
                        TENANT_ONE, MEMBER_ONE, USER_ONE, "ACTIVE", readAt,
                        null, null, 2);
        assertEquals(1, driftedBindings.size());
        assertTrue(driftedBindings.get(0).getEntitlementActive());
        assertFalse(driftedBindings.get(0).getFamilyActive());
        readAt = Date.from(Instant.now().plusSeconds(2));
        List<BoardPortalOAuthFamilyRow> driftedFamily = portalReadMapper.selectOAuthFamilies(
                TENANT_ONE, MEMBER_ONE, USER_ONE, false, "ACTIVE", readAt,
                null, null, 3);
        assertEquals(1, driftedFamily.size());
        assertNull(driftedFamily.get(0).getCurrentBindingUserId());
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
                        + "consent_intent = 'FIRST_CONNECT', "
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

        BoardConnectorBindingSnapshot binding = confirmLegacyProtectedRequest(
                connectorBindingService, connectorAttestation(), USER_ONE);
        BoardEntitlementSnapshot active = entitlementService.getSnapshot(TENANT_ONE, USER_ONE);
        BoardConnectorBindingSnapshot replay = confirmLegacyProtectedRequest(
                connectorBindingService, connectorAttestation(), USER_ONE);

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
        confirmLegacyProtectedRequest(connectorBindingService, connectorAttestation(), USER_ONE);

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

        assertThrows(RuntimeException.class, () -> confirmLegacyProtectedRequest(
                connectorBindingService, connectorAttestation(), USER_ONE));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_scope"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt"));

        execute("DROP TRIGGER independent_board_it_fail_binding_receipt");
        BoardConnectorBindingSnapshot retry = confirmLegacyProtectedRequest(
                connectorBindingService, connectorAttestation(), USER_ONE);
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
                    return confirmLegacyProtectedRequest(
                            connectorBindingService, attestation, USER_ONE);
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
        confirmLegacyProtectedRequest(connectorBindingService, connectorAttestation(), USER_ONE);

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
        confirmLegacyProtectedRequest(connectorBindingService, connectorAttestation(), USER_ONE);

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
        confirmLegacyProtectedRequest(connectorBindingService, connectorAttestation(), USER_ONE);

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
                () -> confirmLegacyProtectedRequest(
                        connectorBindingService, connectorAttestation(), USER_ONE));
        assertEquals(409, terminalBinding.getCode());
        assertEquals("BOARD_CONNECTOR_BINDING_CONFLICT", terminalBinding.getMessage());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_operation"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_usage_budget"));
    }

    @Test
    void entitlementRevokeAlsoRevokesBindingAfterEnterpriseDisablement() throws Exception {
        grantVip();
        confirmLegacyProtectedRequest(connectorBindingService, connectorAttestation(), USER_ONE);
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

    private static OAuthClientFixture registerOAuthClient(int port, String fixtureName) {
        String redirectUri = "http://127.0.0.1:" + port + "/oauth/callback";
        BoardOAuthClientRegistrationResponse response =
                oauthClientRegistrationService.register(
                        new BoardOAuthClientRegistrationRequest(
                                List.of(redirectUri),
                                IndependentBoardOAuthClientRegistrationService
                                        .TOKEN_ENDPOINT_AUTH_METHOD,
                                List.of("authorization_code", "refresh_token"),
                                List.of("code"),
                                List.of(
                                        "board.receipt.write",
                                        "identity.read",
                                        "board.meeting.reserve",
                                        "entitlement.read"),
                                ("{\"fixture\":\"" + fixtureName + "\"}")
                                        .getBytes(StandardCharsets.UTF_8),
                                ("mysql-it:" + fixtureName)
                                        .getBytes(StandardCharsets.UTF_8)));
        assertEquals(List.of(redirectUri), response.redirectUris());
        return new OAuthClientFixture(response.clientId(), redirectUri);
    }

    private static OAuthLineage createOAuthLineage(
            OAuthClientFixture client,
            BoardOAuthConsentIntent consentIntent) {
        Date decisionAt = new Date(System.currentTimeMillis());
        String verifier = OAUTH_CRYPTO.generatePkceVerifier();
        String rawCode = OAUTH_CRYPTO.generateOpaqueSecret();
        byte[] principal = BoardOAuthPrincipalSubject.digest(
                TENANT_ONE, MEMBER_ONE, USER_ONE);
        byte[] scopeDigest = BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE);

        BoardOAuthAuthorizationRequest request = new BoardOAuthAuthorizationRequest();
        request.setRequestHandleDigest(BoardOAuthCrypto.sha256Ascii(
                OAUTH_CRYPTO.generateOpaqueSecret()));
        request.setClientId(client.clientId());
        request.setRedirectUri(client.redirectUri());
        request.setCodeChallenge(BoardOAuthCrypto.pkceS256Challenge(verifier));
        request.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        request.setStateDigest(BoardOAuthCrypto.sha256Ascii(
                OAUTH_CRYPTO.generateOpaqueSecret()));
        request.setStateKeyRef("mysql-it-state-key");
        request.setStateNonce(new byte[12]);
        request.setStateCiphertext(new byte[32]);
        request.setIssuerUri(BoardOAuthProfile.ISSUER);
        request.setResourceUri(BoardOAuthProfile.RESOURCE);
        request.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        request.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        request.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        request.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        request.setScopeDigest(scopeDigest.clone());
        request.setStatus("PENDING");
        request.setRequestedAt(copy(decisionAt));
        request.setExpiresAt(new Date(decisionAt.getTime() + TimeUnit.MINUTES.toMillis(5)));
        request.setVersion(0L);
        assertEquals(1, oauthMapper.insertAuthorizationRequest(request));
        assertNotNull(request.getId());
        assertTrue(request.getId() > 0L);

        // Follow the production decision transition. A PENDING request is born without
        // tenant identity or consent intent; the atomic approval CAS installs both before
        // the composite request/intent foreign key can authorize a code row.
        request.setTenantId(TENANT_ONE);
        request.setMemberId(MEMBER_ONE);
        request.setUserId(USER_ONE);
        request.setPrincipalSubjectDigest(principal.clone());
        request.setConsentIntent(consentIntent);
        request.setApprovedAt(copy(decisionAt));
        assertEquals(1, oauthMapper.approveAuthorizationRequestIfVersion(request, 0L));
        request.setStatus("APPROVED");
        request.setVersion(1L);

        BoardOAuthAuthorizationCode code = new BoardOAuthAuthorizationCode();
        code.setCodeDigest(BoardOAuthCrypto.sha256Ascii(rawCode));
        code.setAuthorizationRequestId(request.getId());
        code.setClientId(client.clientId());
        code.setRedirectUri(client.redirectUri());
        code.setCodeChallenge(request.getCodeChallenge());
        code.setCodeChallengeMethod(BoardOAuthProfile.PKCE_METHOD);
        code.setIssuerUri(BoardOAuthProfile.ISSUER);
        code.setResourceUri(BoardOAuthProfile.RESOURCE);
        code.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        code.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        code.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        code.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        code.setScopeDigest(scopeDigest.clone());
        code.setTenantId(TENANT_ONE);
        code.setMemberId(MEMBER_ONE);
        code.setUserId(USER_ONE);
        code.setPrincipalSubjectDigest(principal.clone());
        code.setConsentIntent(consentIntent);
        code.setStatus("ACTIVE");
        code.setIssuedAt(copy(decisionAt));
        code.setExpiresAt(new Date(decisionAt.getTime() + TimeUnit.SECONDS.toMillis(60)));
        code.setVersion(0L);
        assertEquals(1, oauthMapper.insertAuthorizationCode(code));
        assertNotNull(code.getId());
        assertTrue(code.getId() > 0L);

        return new OAuthLineage(
                client.clientId(),
                client.redirectUri(),
                request.getId(),
                code.getId(),
                decisionAt,
                new BoardOAuthTokenExchangeCommand(
                        rawCode,
                        verifier,
                        client.clientId(),
                        client.redirectUri(),
                        BoardOAuthProfile.RESOURCE));
    }

    /**
     * Intentionally incomplete stale-locator fixture. The production exchange must reject the
     * un-prelocked actual client before it reads family tokens or creation receipts.
     */
    private static String insertBarePendingFamily(OAuthLineage lineage) {
        BoardOAuthTokenFamily family = new BoardOAuthTokenFamily();
        family.setFamilyId(OAUTH_CRYPTO.generateOpaqueSecret());
        family.setOriginAuthorizationCodeId(lineage.codeId());
        family.setClientId(lineage.clientId());
        family.setTenantId(TENANT_ONE);
        family.setMemberId(MEMBER_ONE);
        family.setUserId(USER_ONE);
        family.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        family.setSourceCode(IndependentBoardConnectorBindingService.SOURCE_CODE);
        family.setConnectorCode(IndependentBoardConnectorBindingService.CONNECTOR_CODE);
        family.setIssuerUri(BoardOAuthProfile.ISSUER);
        family.setResourceUri(BoardOAuthProfile.RESOURCE);
        family.setScopeCanonical(BoardOAuthProfile.CANONICAL_SCOPE);
        family.setScopeDigest(BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE));
        family.setPrincipalSubjectDigest(BoardOAuthPrincipalSubject.digest(
                TENANT_ONE, MEMBER_ONE, USER_ONE));
        family.setConsentIntent(BoardOAuthConsentIntent.FIRST_CONNECT);
        family.setStatus("PENDING_BINDING");
        family.setCurrentRefreshGeneration(0L);
        family.setIssuedAt(copy(lineage.decisionAt()));
        family.setExpiresAt(new Date(
                lineage.decisionAt().getTime() + TimeUnit.HOURS.toMillis(1)));
        family.setVersion(0L);
        assertEquals(1, oauthMapper.insertTokenFamily(family));
        assertNotNull(family.getId());
        return family.getFamilyId();
    }

    private static void assertCrossClientPendingSupersession(
            OAuthLineage lineageA,
            String familyA,
            OAuthLineage lineageB,
            BoardOAuthTokenExchangeResult issuedB) throws SQLException {
        String familyB = familyIdForCode(lineageB.codeId());
        assertFalse(lineageA.clientId().equals(lineageB.clientId()));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyA
                + "' AND client_id = '" + lineageA.clientId()
                + "' AND status = 'REVOKED' AND version = 1 "
                + "AND terminated_at IS NOT NULL"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + familyA + "' AND status = 'REVOKED' "
                + "AND revoked_at IS NOT NULL"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyB
                + "' AND client_id = '" + lineageB.clientId()
                + "' AND status = 'PENDING_BINDING' AND version = 0 "
                + "AND binding_id IS NULL AND binding_version IS NULL"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + familyB + "' AND status = 'ACTIVE'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND member_id = " + MEMBER_ONE
                + " AND status = 'PENDING_BINDING'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE enterprise_id = " + TENANT_ONE + " AND member_id = " + MEMBER_ONE
                + " AND status = 'ACTIVE'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyA + "' "
                + "AND client_id = '" + lineageA.clientId() + "' "
                + "AND action = 'TOKEN_FAMILY_REVOKED' "
                + "AND actor_subject_digest = UNHEX('"
                + BoardOAuthCrypto.sha256HexAscii(lineageB.clientId()) + "') "
                + "AND correlation_id = '" + issuedB.correlationId() + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt revoked "
                + "INNER JOIN fbs_oauth_receipt created "
                + "ON created.correlation_id = revoked.correlation_id "
                + "AND created.created_at = revoked.created_at "
                + "WHERE revoked.family_id = '" + familyA + "' "
                + "AND revoked.action = 'TOKEN_FAMILY_REVOKED' "
                + "AND created.receipt_id = '" + issuedB.receiptId() + "' "
                + "AND created.family_id = '" + familyB + "' "
                + "AND created.action = 'TOKEN_FAMILY_CREATED'"));
    }

    private static void assertFreshLineageUnchanged(OAuthLineage lineage) throws SQLException {
        assertEquals("APPROVED", scalarString(
                "SELECT status FROM fbs_oauth_authorization_request WHERE id = "
                        + lineage.requestId()));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_authorization_request "
                + "WHERE id = " + lineage.requestId()
                + " AND version = 1 AND consumed_at IS NULL"));
        assertEquals("ACTIVE", scalarString(
                "SELECT status FROM fbs_oauth_authorization_code WHERE id = "
                        + lineage.codeId()));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_authorization_code "
                + "WHERE id = " + lineage.codeId()
                        + " AND version = 0 AND used_at IS NULL AND revoked_at IS NULL"));
    }

    private static void assertPendingFamilyUnchanged(String familyId) throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyId + "' "
                + "AND status = 'PENDING_BINDING' AND version = 0 "
                + "AND terminated_at IS NULL"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + familyId + "' "
                + "AND status = 'ACTIVE' AND version = 0 "
                + "AND used_at IS NULL AND revoked_at IS NULL"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyId + "' "
                + "AND action = 'TOKEN_FAMILY_CREATED'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyId + "' "
                + "AND action IN ('TOKEN_FAMILY_REVOKED', 'TOKEN_FAMILY_COMPROMISED', "
                + "'AUTHORIZATION_CODE_REPLAY_DETECTED')"));
    }

    private static void assertReplayContainmentCommitted(
            String familyId,
            String expectedTokenStatus,
            boolean expectRevokedAt) throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE family_id = '" + familyId + "' "
                + "AND status = 'COMPROMISED' AND version = 1 "
                + "AND terminated_at IS NOT NULL"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + familyId + "' "
                + "AND status = '" + expectedTokenStatus + "' AND version = 1 "
                + (expectRevokedAt
                        ? "AND revoked_at IS NOT NULL"
                        : "AND revoked_at IS NULL")));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyId + "' "
                + "AND action = 'TOKEN_FAMILY_COMPROMISED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_id = '" + familyId + "' "
                + "AND action = 'AUTHORIZATION_CODE_REPLAY_DETECTED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt compromised "
                + "INNER JOIN fbs_oauth_receipt replay "
                + "ON replay.family_id = compromised.family_id "
                + "AND replay.correlation_id = compromised.correlation_id "
                + "AND replay.created_at = compromised.created_at "
                + "WHERE compromised.family_id = '" + familyId + "' "
                + "AND compromised.action = 'TOKEN_FAMILY_COMPROMISED' "
                + "AND replay.action = 'AUTHORIZATION_CODE_REPLAY_DETECTED'"));
    }

    private static String familyIdForCode(Long codeId) throws SQLException {
        return scalarString("SELECT family_id FROM fbs_oauth_token_family "
                + "WHERE origin_authorization_code_id = " + codeId);
    }

    private static int familyCountForCode(Long codeId) throws SQLException {
        return scalarInt("SELECT COUNT(*) FROM fbs_oauth_token_family "
                + "WHERE origin_authorization_code_id = " + codeId);
    }

    private static AsyncOAuthAttempt captureExchange(OAuthLineage lineage) {
        try {
            return AsyncOAuthAttempt.succeeded(
                    oauthTokenExchangeFacade.exchange(lineage.command()));
        } catch (RuntimeException failure) {
            return AsyncOAuthAttempt.failed(failure);
        }
    }

    private static AsyncOAuthAttempt exchangeAfterObservedOAuthRowWait(
            OAuthLineage lineage,
            String lockedTable,
            String lockSql,
            int expectedLockedRows,
            Instant initiallyValid,
            Instant advanceTo) throws Exception {
        if (advanceTo.isBefore(initiallyValid)) {
            throw new IllegalArgumentException("advanceTo must not precede initiallyValid");
        }
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Connection blocker = dataSource.getConnection();
        boolean blockerCommitted = false;
        try {
            blocker.setAutoCommit(false);
            int lockedRows = 0;
            try (Statement statement = blocker.createStatement();
                 ResultSet rows = statement.executeQuery(lockSql)) {
                while (rows.next()) {
                    lockedRows++;
                }
            }
            assertEquals(expectedLockedRows, lockedRows,
                    "the blocker must lock the exact OAuth before-image rows");

            oauthTokenExchangeClock.setInstant(initiallyValid);
            Future<AsyncOAuthAttempt> exchangeFuture = pool.submit(() ->
                    captureExchange(lineage));
            awaitMysqlRowLockWaitersOrFailEarly(
                    lockedTable, 1, exchangeFuture, "OAuth token exchange");
            oauthTokenExchangeClock.setInstant(advanceTo);

            blocker.commit();
            blockerCommitted = true;
            return exchangeFuture.get(15, TimeUnit.SECONDS);
        } finally {
            if (!blockerCommitted) {
                blocker.rollback();
            }
            blocker.close();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static AsyncOAuthAttempt captureActivation(String rawAccessToken) {
        try {
            return AsyncOAuthAttempt.succeeded(oauthFirstProtectedRequestFacade.activate(
                    "Bearer " + rawAccessToken, "initialize"));
        } catch (RuntimeException failure) {
            return AsyncOAuthAttempt.failed(failure);
        }
    }

    private static void assertOAuthProtocolFailure(
            AsyncOAuthAttempt attempt,
            String oauthError,
            String reasonCode) {
        assertFalse(attempt.succeeded());
        assertTrue(attempt.failure() instanceof BoardOAuthProtocolException,
                String.valueOf(attempt.failure()));
        BoardOAuthProtocolException failure =
                (BoardOAuthProtocolException) attempt.failure();
        assertEquals(oauthError, failure.oauthError());
        assertEquals(reasonCode, failure.reasonCode());
    }

    private static void assertActivationLostToPendingFamilyTransition(
            AsyncOAuthAttempt attempt) {
        assertFalse(attempt.succeeded());
        assertTrue(attempt.failure() instanceof ServiceException,
                String.valueOf(attempt.failure()));
        ServiceException failure = (ServiceException) attempt.failure();
        assertEquals(409, failure.getCode());
        assertTrue(Set.of(
                        "BOARD_OAUTH_PENDING_FAMILY_STATE_INVALID",
                        "BOARD_OAUTH_PENDING_FAMILY_CONFLICT")
                .contains(failure.getMessage()), failure.getMessage());
    }

    private static void assertNotDatabaseDeadlock(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sql) {
                assertFalse(sql.getErrorCode() == 1213, "MySQL deadlock: " + sql);
                assertFalse("40001".equals(sql.getSQLState()), "serialization rollback: " + sql);
            }
            String message = String.valueOf(current.getMessage()).toLowerCase();
            assertFalse(message.contains("deadlock"), String.valueOf(current));
            assertFalse(message.contains("lock wait timeout"), String.valueOf(current));
            current = current.getCause();
        }
    }

    private static void lockEnterpriseAuthority(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT id FROM fbs_enterprise WHERE id = " + TENANT_ONE
                             + " FOR UPDATE")) {
            assertTrue(row.next());
            assertEquals(TENANT_ONE, row.getLong(1));
            assertFalse(row.next());
        }
    }

    private static void awaitMysqlRowLockWaiters(
            String tableName,
            String indexName,
            int minimumWaiters) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String sql = "SELECT COUNT(DISTINCT requested.THREAD_ID) "
                + "FROM performance_schema.data_lock_waits waits "
                + "INNER JOIN performance_schema.data_locks requested "
                + "ON requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID "
                + "WHERE requested.OBJECT_SCHEMA = DATABASE() "
                + "AND requested.OBJECT_NAME = '" + tableName + "' "
                + "AND requested.INDEX_NAME = '" + indexName + "'";
        while (System.nanoTime() < deadline) {
            if (scalarInt(sql) >= minimumWaiters) {
                return;
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("expected " + minimumWaiters + " MySQL waiter(s) on "
                + tableName + "." + indexName);
    }

    private static void awaitMysqlRowLockWaitersOrFailEarly(
            String tableName,
            String indexName,
            int minimumWaiters,
            Future<AsyncOAuthAttempt> contender,
            String contenderName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String sql = "SELECT COUNT(DISTINCT requested.THREAD_ID) "
                + "FROM performance_schema.data_lock_waits waits "
                + "INNER JOIN performance_schema.data_locks requested "
                + "ON requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID "
                + "WHERE requested.OBJECT_SCHEMA = DATABASE() "
                + "AND requested.OBJECT_NAME = '" + tableName + "' "
                + "AND requested.INDEX_NAME = '" + indexName + "'";
        while (System.nanoTime() < deadline) {
            if (scalarInt(sql) >= minimumWaiters) {
                return;
            }
            if (contender.isDone()) {
                AsyncOAuthAttempt completed = contender.get(1, TimeUnit.SECONDS);
                throw new AssertionError(
                        contenderName + " completed before entering the expected MySQL row lock",
                        completed.failure());
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("expected " + minimumWaiters + " MySQL waiter(s) on "
                + tableName + "." + indexName + " for " + contenderName);
    }

    private static void awaitMysqlRowLockWaitersOrFailEarly(
            String tableName,
            int minimumWaiters,
            Future<AsyncOAuthAttempt> contender,
            String contenderName) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        String sql = "SELECT COUNT(DISTINCT requested.THREAD_ID) "
                + "FROM performance_schema.data_lock_waits waits "
                + "INNER JOIN performance_schema.data_locks requested "
                + "ON requested.ENGINE_LOCK_ID = waits.REQUESTING_ENGINE_LOCK_ID "
                + "WHERE requested.OBJECT_SCHEMA = DATABASE() "
                + "AND requested.OBJECT_NAME = '" + tableName + "'";
        while (System.nanoTime() < deadline) {
            if (scalarInt(sql) >= minimumWaiters) {
                return;
            }
            if (contender.isDone()) {
                AsyncOAuthAttempt completed = contender.get(1, TimeUnit.SECONDS);
                throw new AssertionError(
                        contenderName + " completed before entering the expected MySQL row lock",
                        completed.failure());
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("expected " + minimumWaiters + " MySQL waiter(s) on "
                + tableName + " for " + contenderName);
    }

    private static Date copy(Date value) {
        return new Date(value.getTime());
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
                "DROP TABLE IF EXISTS fbs_usage_operation_policy_receipt",
                "DROP TABLE IF EXISTS fbs_plan_policy_head",
                "DROP TABLE IF EXISTS fbs_plan_policy_revision_receipt",
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
                "DROP TABLE IF EXISTS sys_user_role",
                "DROP TABLE IF EXISTS sys_user",
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
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + OAUTH_PROVENANCE_MIGRATION_VERSION + "'",
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + OAUTH_CONSENT_MIGRATION_VERSION + "'",
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + PLAN_POLICY_MIGRATION_VERSION + "'",
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
                "CREATE TABLE sys_user ("
                        + "user_id BIGINT NOT NULL PRIMARY KEY, status CHAR(1) NOT NULL, "
                        + "del_flag CHAR(1) NOT NULL) ENGINE=InnoDB",
                "CREATE TABLE sys_user_role ("
                        + "user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, "
                        + "PRIMARY KEY (user_id, role_id)) ENGINE=InnoDB",
                "INSERT INTO sys_role (role_id, role_key, status, del_flag) "
                        + "VALUES (10, 'user', '0', '0')",
                "INSERT INTO sys_user (user_id, status, del_flag) VALUES ("
                        + USER_ONE + ", '0', '0'), (" + USER_TWO + ", '0', '0')",
                "INSERT INTO sys_user_role (user_id, role_id) VALUES ("
                        + USER_ONE + ", 10), (" + USER_TWO + ", 10)",
                "CREATE TABLE fbs_enterprise ("
                        + "id BIGINT UNSIGNED NOT NULL PRIMARY KEY, "
                        + "enterprise_name VARCHAR(128) NOT NULL, "
                        + "contact_name VARCHAR(64), contact_phone VARCHAR(32), "
                        + "contact_email VARCHAR(128), remark VARCHAR(512), "
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
        Path oauthProvenanceMigration = locateMigration(
                "update_20260721_independent_board_oauth_receipt_provenance.sql");
        assertOAuthProvenanceMigrationRecoveryAndUniqueness(
                oauthProvenanceMigration);
        Path oauthConsentMigration = locateMigration(
                "update_20260721_independent_board_oauth_consent_intent_lineage.sql");
        assertOAuthConsentIntentMigrationMatrix(oauthConsentMigration);
        Path menuMigration = locateMigration("update_20260720_independent_board_me_menu.sql");
        executeMigration(menuMigration);
        executeMigration(menuMigration);
        Path planPolicyMigration = locateMigration(
                "update_20260722_independent_board_plan_policy.sql");
        executeMigration(planPolicyMigration);
        executeMigration(planPolicyMigration);
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MENU_MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + CONNECTOR_MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                 + "WHERE version = '" + OAUTH_PROVENANCE_MIGRATION_VERSION + "' "
                 + "AND description = '" + OAUTH_PROVENANCE_APPLIED + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_CONSENT_MIGRATION_VERSION + "' "
                + "AND description = '" + OAUTH_CONSENT_APPLIED + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + PLAN_POLICY_MIGRATION_VERSION + "' "
                + "AND description = 'Independent Board immutable plan policy revisions "
                + "and operation lineage'"));
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
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_plan_policy_revision_receipt','fbs_plan_policy_head',"
                + "'fbs_usage_operation_policy_receipt') AND engine = 'InnoDB'"));
        assertEquals(7, scalarInt("SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() AND event_object_table IN "
                + "('fbs_plan_policy_revision_receipt',"
                + "'fbs_usage_operation_policy_receipt','fbs_entitlement_receipt')"));
        assertEquals(148, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt' "
                + "AND column_name = 'family_created_slot' "
                + "AND extra = 'STORED GENERATED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM (SELECT index_name "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name = 'fbs_oauth_receipt' "
                + "AND index_name = 'uk_oauth_receipt_family_created_slot' "
                + "GROUP BY index_name HAVING MIN(non_unique) = 0 "
                + "AND COUNT(*) = 1) exact_provenance_index"));
    }

    private static void assertOAuthProvenanceMigrationRecoveryAndUniqueness(
            Path provenanceMigration) throws Exception {
        String lockFreeSql = "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_oauth_receipt_provenance_v1'), 256))";
        seedOAuthProvenanceLineage();

        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                provenanceCreatedReceiptInsert(
                        "receipt-provenance-orphan", "correlation-provenance-orphan",
                        999L, "family-provenance-orphan"),
                "SET FOREIGN_KEY_CHECKS = 1");
        SQLException missingLineage = assertThrows(
                SQLException.class, () -> executeMigration(provenanceMigration));
        assertTrue(missingLineage.getMessage().contains(
                "receipt lineage is missing or drifted"), missingLineage.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_PROVENANCE_MIGRATION_VERSION + "'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt' "
                + "AND column_name = 'family_created_slot'"));
        execute("TRUNCATE TABLE fbs_oauth_receipt");

        execute(
                provenanceCreatedReceiptInsert(
                        "receipt-provenance-duplicate-1", "correlation-provenance-duplicate-1",
                        1L, "family-provenance-1"),
                provenanceCreatedReceiptInsert(
                        "receipt-provenance-duplicate-2", "correlation-provenance-duplicate-2",
                        1L, "family-provenance-1"));
        SQLException duplicateHistory = assertThrows(
                SQLException.class, () -> executeMigration(provenanceMigration));
        assertTrue(duplicateHistory.getMessage().contains(
                "Duplicate TOKEN_FAMILY_CREATED receipt families require reviewed recovery"),
                duplicateHistory.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_PROVENANCE_MIGRATION_VERSION + "'"));
        execute("TRUNCATE TABLE fbs_oauth_receipt");

        try {
            executeMigration(provenanceMigration);
        } catch (SQLException migrationFailure) {
            throw new SQLException(
                    migrationFailure.getMessage() + " | provenanceDiagnostic="
                            + oauthProvenanceDiagnosticSummary(),
                    migrationFailure.getSQLState(),
                    migrationFailure.getErrorCode(),
                    migrationFailure);
        }
        assertOAuthProvenanceAppliedShape();
        executeMigration(provenanceMigration);
        assertOAuthProvenanceAppliedShape();

        dropOAuthProvenanceShapeAndReceipt();
        execute(
                "INSERT INTO u3w_schema_migration (version, description) VALUES ('"
                        + OAUTH_PROVENANCE_MIGRATION_VERSION + "', '"
                        + OAUTH_PROVENANCE_RUNNING + "')",
                oauthProvenanceColumnOnlyDdl());
        SQLException columnOnly = assertThrows(
                SQLException.class, () -> executeMigration(provenanceMigration));
        assertTrue(columnOnly.getMessage().contains(
                "provenance DDL is partial, orphaned or drifted"), columnOnly.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        execute(
                "ALTER TABLE fbs_oauth_receipt DROP COLUMN family_created_slot",
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + OAUTH_PROVENANCE_MIGRATION_VERSION + "'");

        execute("INSERT INTO u3w_schema_migration (version, description) VALUES ('"
                + OAUTH_PROVENANCE_MIGRATION_VERSION + "', '"
                + OAUTH_PROVENANCE_RUNNING + "')");
        executeMigration(provenanceMigration);
        assertOAuthProvenanceAppliedShape();

        dropOAuthProvenanceShapeAndReceipt();
        execute(oauthProvenanceCompleteDdl());
        SQLException orphanDdl = assertThrows(
                SQLException.class, () -> executeMigration(provenanceMigration));
        assertTrue(orphanDdl.getMessage().contains(
                "provenance DDL is partial, orphaned or drifted"), orphanDdl.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        execute("ALTER TABLE fbs_oauth_receipt "
                + "DROP INDEX uk_oauth_receipt_family_created_slot, "
                + "DROP COLUMN family_created_slot");

        execute(
                "INSERT INTO u3w_schema_migration (version, description) VALUES ('"
                        + OAUTH_PROVENANCE_MIGRATION_VERSION + "', '"
                        + OAUTH_PROVENANCE_RUNNING + "')",
                oauthProvenanceCompleteDdl());
        executeMigration(provenanceMigration);
        assertOAuthProvenanceAppliedShape();

        execute("ALTER TABLE fbs_oauth_receipt "
                + "ALTER INDEX uk_oauth_receipt_family_created_slot INVISIBLE");
        SQLException invisibleIndex = assertThrows(
                SQLException.class, () -> executeMigration(provenanceMigration));
        assertTrue(invisibleIndex.getMessage().contains(
                "provenance DDL is partial, orphaned or drifted"),
                invisibleIndex.getMessage());
        assertEquals(1, scalarInt(lockFreeSql));
        execute("ALTER TABLE fbs_oauth_receipt "
                + "ALTER INDEX uk_oauth_receipt_family_created_slot VISIBLE");
        executeMigration(provenanceMigration);

        assertOAuthProvenanceNullSemantics();
        assertOAuthProvenanceRollbackRetry();
        assertOAuthProvenanceSameFamilyConcurrency();
        assertOAuthProvenanceDifferentFamilyConcurrency();
        assertOAuthProvenanceReceiptImmutability();
        assertOAuthProvenanceAppliedShape();
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.routines "
                + "WHERE routine_schema = DATABASE() "
                + "AND routine_name = "
                + "'u3w_migrate_independent_board_oauth_receipt_provenance_20260721'"));
        assertEquals(1, scalarInt(lockFreeSql));
    }

    private static void assertOAuthProvenanceAppliedShape() throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_PROVENANCE_MIGRATION_VERSION + "' "
                + "AND description = '" + OAUTH_PROVENANCE_APPLIED + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt' "
                + "AND column_name = 'family_created_slot' "
                + "AND column_type = 'varchar(128)' AND is_nullable = 'YES' "
                + "AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' "
                + "AND extra = 'STORED GENERATED'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM (SELECT index_name "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name = 'fbs_oauth_receipt' "
                + "AND index_name = 'uk_oauth_receipt_family_created_slot' "
                + "GROUP BY index_name HAVING MIN(non_unique) = 0 "
                + "AND MIN(is_visible) = 'YES' AND COUNT(*) = 1 "
                + "AND MIN(column_name) = 'family_created_slot') exact_index"));
    }

    private static void assertOAuthConsentIntentMigrationMatrix(Path consentMigration)
            throws Exception {
        resetOAuthConsentToProvenanceShape();
        assertOAuthConsentLegacyDataIsRejectedBeforeAnyDdl(consentMigration);

        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentAppliedShape();
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentAppliedShape();

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentAppliedShape();

        assertOAuthConsentRequestStageRecovery(consentMigration);
        assertOAuthConsentCodeStageRecovery(consentMigration);
        assertOAuthConsentFamilyStageRecovery(consentMigration);
        assertOAuthConsentColumnDriftIsRejected(consentMigration);
        assertOAuthConsentIndexDriftIsRejected(consentMigration);
        assertOAuthConsentForeignKeyDriftIsRejected(consentMigration);

        resetOAuthConsentToProvenanceShape();
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentCheckDriftIsRejected(consentMigration);
        assertOAuthConsentTriggerDriftIsRejected(consentMigration);
        assertOAuthConsentDecisionAndLineageSemantics();
        assertOAuthConsentAppliedShape();
    }

    private static void assertOAuthConsentLegacyDataIsRejectedBeforeAnyDdl(
            Path consentMigration) throws Exception {
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                invalidOAuthRequestInsert(
                        "71", "APPROVED", true,
                        ", approved_at = '2026-07-20 00:00:01.000'"),
                "SET FOREIGN_KEY_CHECKS = 1");
        assertOAuthConsentLegacyRejection(consentMigration);

        resetOAuthConsentToProvenanceShape();
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                invalidOAuthCodeInsert("72", "ACTIVE"),
                "SET FOREIGN_KEY_CHECKS = 1");
        assertOAuthConsentLegacyRejection(consentMigration);

        resetOAuthConsentToProvenanceShape();
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                invalidOAuthFamilyInsert("family-consent-legacy", "PENDING_BINDING", ""),
                "SET FOREIGN_KEY_CHECKS = 1");
        assertOAuthConsentLegacyRejection(consentMigration);

        resetOAuthConsentToProvenanceShape();
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                oauthLegacyTokenInsert("73", "family-consent-legacy-token"),
                "SET FOREIGN_KEY_CHECKS = 1");
        assertOAuthConsentLegacyRejection(consentMigration);

        resetOAuthConsentToProvenanceShape();
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                provenanceRevokedReceiptInsert(
                        "receipt-consent-legacy-business",
                        "correlation-consent-legacy-business",
                        "family-consent-legacy-business"),
                "SET FOREIGN_KEY_CHECKS = 1");
        assertOAuthConsentLegacyRejection(consentMigration);
        resetOAuthConsentToProvenanceShape();
    }

    private static void assertOAuthConsentLegacyRejection(Path consentMigration)
            throws Exception {
        assertOAuthConsentMigrationRejected(
                consentMigration,
                "Legacy OAuth data cannot be assigned a consent intent without reviewed evidence");
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_CONSENT_MIGRATION_VERSION + "'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND column_name = 'consent_intent' "
                + "AND table_name IN ('fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family')"));
    }

    private static void assertOAuthConsentRequestStageRecovery(Path consentMigration)
            throws Exception {
        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute("ALTER TABLE fbs_oauth_authorization_request ADD COLUMN consent_intent "
                + "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth request consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        execute(oauthConsentRequestStageDdl(
                "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth consent-intent DDL exists without its RUNNING receipt");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(oauthConsentRequestStageDdl(
                "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true));
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentAppliedShape();
    }

    private static void assertOAuthConsentCodeStageRecovery(Path consentMigration)
            throws Exception {
        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                "ALTER TABLE fbs_oauth_authorization_code ADD COLUMN consent_intent "
                        + "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth code consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth consent-intent DDL exists without its RUNNING receipt");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true));
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentAppliedShape();
    }

    private static void assertOAuthConsentFamilyStageRecovery(Path consentMigration)
            throws Exception {
        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true),
                "ALTER TABLE fbs_oauth_token_family ADD COLUMN consent_intent "
                        + "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth family consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true),
                oauthConsentFamilyStageDdl(
                        "origin_authorization_code_id, consent_intent", "RESTRICT", true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth consent-intent DDL exists without its RUNNING receipt");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true),
                oauthConsentFamilyStageDdl(
                        "origin_authorization_code_id, consent_intent", "RESTRICT", true));
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
        assertOAuthConsentAppliedShape();
    }

    private static void assertOAuthConsentColumnDriftIsRejected(Path consentMigration)
            throws Exception {
        assertOAuthConsentRequestColumnDrift(
                consentMigration, "VARCHAR(31) CHARACTER SET ascii COLLATE ascii_bin NULL");
        assertOAuthConsentRequestColumnDrift(
                consentMigration, "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL");
        assertOAuthConsentRequestColumnDrift(
                consentMigration, "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NULL");
    }

    private static void assertOAuthConsentRequestColumnDrift(
            Path consentMigration,
            String columnDefinition) throws Exception {
        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(oauthConsentRequestStageDdl(columnDefinition, true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth request consent-intent DDL is partial or drifted");
    }

    private static void assertOAuthConsentIndexDriftIsRejected(Path consentMigration)
            throws Exception {
        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(oauthConsentRequestStageDdl(
                "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", false));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth request consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "consent_intent, authorization_request_id", true, "RESTRICT", false));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth code consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", false, "RESTRICT", true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth code consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true),
                oauthConsentFamilyStageDdl(
                        "consent_intent, origin_authorization_code_id", "RESTRICT", false));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth family consent-intent DDL is partial or drifted");
    }

    private static void assertOAuthConsentForeignKeyDriftIsRejected(Path consentMigration)
            throws Exception {
        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "CASCADE", true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth code consent-intent DDL is partial or drifted");

        resetOAuthConsentToProvenanceShape();
        insertOAuthConsentRunningReceipt();
        execute(
                oauthConsentRequestStageDdl(
                        "VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL", true),
                oauthConsentCodeStageDdl(
                        "authorization_request_id, consent_intent", true, "RESTRICT", true),
                oauthConsentFamilyStageDdl(
                        "origin_authorization_code_id, consent_intent", "CASCADE", true));
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth family consent-intent DDL is partial or drifted");
    }

    private static void assertOAuthConsentCheckDriftIsRejected(Path consentMigration)
            throws Exception {
        execute("ALTER TABLE fbs_oauth_authorization_request "
                + "DROP CHECK chk_oauth_request_consent_intent, "
                + "ADD CONSTRAINT chk_oauth_request_consent_intent "
                + "CHECK (consent_intent IS NULL OR consent_intent IN "
                + "('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION'))");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth request consent-intent DDL is partial or drifted");
        execute("ALTER TABLE fbs_oauth_authorization_request "
                + "DROP CHECK chk_oauth_request_consent_intent, "
                + oauthConsentRequestCheckClause());
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);

        execute("ALTER TABLE fbs_oauth_authorization_code "
                + "DROP CHECK chk_oauth_code_consent_intent, "
                + "ADD CONSTRAINT chk_oauth_code_consent_intent "
                + "CHECK (consent_intent IN "
                + "('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION','DRIFT'))");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth code consent-intent DDL is partial or drifted");
        execute("ALTER TABLE fbs_oauth_authorization_code "
                + "DROP CHECK chk_oauth_code_consent_intent, "
                + oauthConsentCodeCheckClause());
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);

        execute("ALTER TABLE fbs_oauth_token_family "
                + "DROP CHECK chk_oauth_family_consent_intent, "
                + "ADD CONSTRAINT chk_oauth_family_consent_intent "
                + "CHECK (consent_intent IN "
                + "('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION','DRIFT'))");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth family consent-intent DDL is partial or drifted");
        execute("ALTER TABLE fbs_oauth_token_family "
                + "DROP CHECK chk_oauth_family_consent_intent, "
                + oauthConsentFamilyCheckClause());
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
    }

    private static void assertOAuthConsentTriggerDriftIsRejected(Path consentMigration)
            throws Exception {
        execute(
                "DROP TRIGGER trg_oauth_request_consent_intent_once",
                "CREATE TRIGGER trg_oauth_request_consent_intent_once "
                        + "BEFORE UPDATE ON fbs_oauth_authorization_request FOR EACH ROW "
                        + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'consent drift'");
        assertOAuthConsentMigrationRejected(
                consentMigration, "OAuth request consent-intent trigger is partial or drifted");
        execute(
                "DROP TRIGGER trg_oauth_request_consent_intent_once",
                oauthConsentTriggerDdl());
        executeOAuthConsentMigrationWithDiagnosis(consentMigration);
    }

    private static void assertOAuthConsentDecisionAndLineageSemantics() throws Exception {
        clearOAuthBusinessRowsForConsentMatrix();
        seedOAuthConsentEntitlement();
        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                oauthPendingConsentRequestInsert("81"),
                "SET FOREIGN_KEY_CHECKS = 1");
        int requestId = scalarInt("SELECT id FROM fbs_oauth_authorization_request "
                + "WHERE request_handle_digest = UNHEX(REPEAT('81', 32))");

        SQLException prematureIntent = assertThrows(SQLException.class, () -> execute(
                "UPDATE fbs_oauth_authorization_request SET "
                        + "consent_intent = 'FIRST_CONNECT' WHERE id = " + requestId));
        assertTrue(prematureIntent.getMessage().contains(
                "OAuth consent intent must be set by a decision transition"),
                prematureIntent.getMessage());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_authorization_request "
                + "WHERE id = " + requestId + " AND consent_intent IS NULL "
                + "AND status = 'PENDING'"));

        execute(
                "SET FOREIGN_KEY_CHECKS = 0",
                "UPDATE fbs_oauth_authorization_request SET "
                        + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                        + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                        + "consent_intent = 'FIRST_CONNECT', status = 'APPROVED', "
                        + "approved_at = '2026-07-20 00:00:01.000', version = version + 1 "
                        + "WHERE id = " + requestId + " AND status = 'PENDING' "
                        + "AND consent_intent IS NULL",
                "SET FOREIGN_KEY_CHECKS = 1");
        assertEquals("FIRST_CONNECT", scalarString(
                "SELECT consent_intent FROM fbs_oauth_authorization_request WHERE id = "
                        + requestId));

        SQLException changed = assertThrows(SQLException.class, () -> execute(
                "UPDATE fbs_oauth_authorization_request "
                        + "SET consent_intent = 'EXPLICIT_REAUTHORIZATION' WHERE id = "
                        + requestId));
        assertTrue(changed.getMessage().contains("OAuth consent intent is immutable"),
                changed.getMessage());
        SQLException cleared = assertThrows(SQLException.class, () -> execute(
                "UPDATE fbs_oauth_authorization_request "
                        + "SET consent_intent = NULL WHERE id = " + requestId));
        assertTrue(cleared.getMessage().contains("OAuth consent intent is immutable"),
                cleared.getMessage());

        SQLException mismatchedCode = assertThrows(SQLException.class, () -> execute(
                oauthConsentCodeInsert(requestId, "82", "EXPLICIT_REAUTHORIZATION")));
        assertTrue(mismatchedCode.getMessage().contains("fk_oauth_code_request_consent"),
                mismatchedCode.getMessage());
        execute(oauthConsentCodeInsert(requestId, "83", "FIRST_CONNECT"));
        int codeId = scalarInt("SELECT id FROM fbs_oauth_authorization_code "
                + "WHERE code_digest = UNHEX(REPEAT('83', 32))");

        SQLException mismatchedFamily = assertThrows(SQLException.class, () -> execute(
                oauthConsentFamilyInsert(
                        codeId, "family-consent-mismatch", "EXPLICIT_REAUTHORIZATION")));
        assertTrue(mismatchedFamily.getMessage().contains("fk_oauth_family_code_consent"),
                mismatchedFamily.getMessage());
        execute(oauthConsentFamilyInsert(codeId, "family-consent-correct", "FIRST_CONNECT"));
        assertEquals("FIRST_CONNECT", scalarString("SELECT consent_intent "
                + "FROM fbs_oauth_token_family WHERE family_id = 'family-consent-correct'"));
        clearOAuthBusinessRowsForConsentMatrix();
    }

    private static void resetOAuthConsentToProvenanceShape() throws Exception {
        String externalForeignKeys = scalarString("SELECT COALESCE(GROUP_CONCAT(CONCAT("
                + "table_schema, '.', table_name, '.', constraint_name, '->', "
                + "referenced_table_schema, '.', referenced_table_name) "
                + "ORDER BY table_schema, table_name, constraint_name SEPARATOR ','), '') FROM ("
                + "SELECT DISTINCT table_schema, table_name, constraint_name, "
                + "referenced_table_schema, referenced_table_name "
                + "FROM information_schema.key_column_usage "
                + "WHERE referenced_table_schema = DATABASE() "
                + "AND referenced_table_name IN ('fbs_oauth_client',"
                + "'fbs_oauth_authorization_request','fbs_oauth_authorization_code',"
                + "'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt') "
                + "AND NOT (table_schema = DATABASE() AND table_name IN ('fbs_oauth_client',"
                + "'fbs_oauth_authorization_request','fbs_oauth_authorization_code',"
                + "'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt'))"
                + ") external_oauth_foreign_keys");
        if (!externalForeignKeys.isEmpty()) {
            throw new SQLException("OAuth clean 034 rebuild is blocked by external foreign keys: "
                    + externalForeignKeys);
        }
        int exactOauthTableCount = scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.tables WHERE table_schema = DATABASE() "
                + "AND table_type = 'BASE TABLE' AND engine = 'InnoDB' "
                + "AND table_name IN ('fbs_oauth_client',"
                + "'fbs_oauth_authorization_request','fbs_oauth_authorization_code',"
                + "'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')");
        if (exactOauthTableCount != 6) {
            throw new SQLException("OAuth clean 034 rebuild requires all six InnoDB tables; found "
                    + exactOauthTableCount);
        }
        execute(
                "DROP PROCEDURE IF EXISTS u3w_assert_ib_oauth_consent_intent_20260721",
                "DROP PROCEDURE IF EXISTS u3w_migrate_ib_oauth_consent_intent_20260721",
                "DROP PROCEDURE IF EXISTS u3w_finalize_ib_oauth_consent_intent_20260721",
                "DROP TABLE fbs_oauth_receipt",
                "DROP TABLE fbs_oauth_token",
                "DROP TABLE fbs_oauth_token_family",
                "DROP TABLE fbs_oauth_authorization_code",
                "DROP TABLE fbs_oauth_authorization_request",
                "DROP TABLE fbs_oauth_client",
                "DELETE FROM u3w_schema_migration WHERE version IN ('"
                        + OAUTH_MIGRATION_VERSION + "','"
                        + OAUTH_PROVENANCE_MIGRATION_VERSION + "','"
                        + OAUTH_CONSENT_MIGRATION_VERSION + "')");
        executeMigration(locateMigration(
                "update_20260721_independent_board_oauth_foundation.sql"));
        executeMigration(locateMigration(
                "update_20260721_independent_board_oauth_receipt_provenance.sql"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_MIGRATION_VERSION + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_PROVENANCE_MIGRATION_VERSION + "' "
                + "AND description = '" + OAUTH_PROVENANCE_APPLIED + "'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_CONSENT_MIGRATION_VERSION + "'"));
        assertEquals(145, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(53, scalarInt("SELECT COUNT(*) FROM (SELECT table_name,index_name "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt') "
                + "GROUP BY table_name,index_name) oauth_indexes"));
        assertEquals(14, scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_authorization_request','fbs_oauth_authorization_code',"
                + "'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(57, scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.key_column_usage "
                + "WHERE constraint_schema = DATABASE() AND referenced_table_name IS NOT NULL "
                + "AND table_name IN ('fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(33, scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.table_constraints "
                + "WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() AND event_object_table IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
    }

    private static void clearOAuthBusinessRowsForConsentMatrix() throws SQLException {
        execute(
                "TRUNCATE TABLE fbs_oauth_receipt",
                "DELETE FROM fbs_oauth_token",
                "DELETE FROM fbs_oauth_token_family",
                "DELETE FROM fbs_oauth_authorization_code",
                "DELETE FROM fbs_oauth_authorization_request",
                "DELETE FROM fbs_oauth_client",
                "DELETE FROM fbs_product_entitlement");
    }

    private static void insertOAuthConsentRunningReceipt() throws SQLException {
        execute("INSERT INTO u3w_schema_migration (version, description) VALUES ('"
                + OAUTH_CONSENT_MIGRATION_VERSION + "', '" + OAUTH_CONSENT_RUNNING + "')");
    }

    private static String oauthConsentRequestStageDdl(
            String columnDefinition,
            boolean uniqueIndex) {
        String indexType = uniqueIndex ? "UNIQUE KEY" : "KEY";
        return "ALTER TABLE fbs_oauth_authorization_request "
                + "ADD COLUMN consent_intent " + columnDefinition + ", "
                + oauthConsentRequestCheckClause() + ", ADD " + indexType
                + " uk_oauth_request_id_consent (id, consent_intent)";
    }

    private static String oauthConsentCodeStageDdl(
            String parentIndexColumns,
            boolean identityUnique,
            String deleteRule,
            boolean addForeignKey) {
        String identityIndexType = identityUnique ? "UNIQUE KEY" : "KEY";
        String foreignKey = addForeignKey
                ? ", ADD CONSTRAINT fk_oauth_code_request_consent "
                        + "FOREIGN KEY (authorization_request_id, consent_intent) "
                        + "REFERENCES fbs_oauth_authorization_request (id, consent_intent) "
                        + "ON DELETE " + deleteRule + " ON UPDATE RESTRICT"
                : "";
        return "ALTER TABLE fbs_oauth_authorization_code "
                + "ADD COLUMN consent_intent VARCHAR(32) CHARACTER SET ascii "
                + "COLLATE ascii_bin NOT NULL, "
                + oauthConsentCodeCheckClause() + ", ADD " + identityIndexType
                + " uk_oauth_code_id_consent (id, consent_intent), "
                + "ADD KEY idx_oauth_code_request_consent (" + parentIndexColumns + ")"
                + foreignKey;
    }

    private static String oauthConsentFamilyStageDdl(
            String parentIndexColumns,
            String deleteRule,
            boolean addForeignKey) {
        String foreignKey = addForeignKey
                ? ", ADD CONSTRAINT fk_oauth_family_code_consent "
                        + "FOREIGN KEY (origin_authorization_code_id, consent_intent) "
                        + "REFERENCES fbs_oauth_authorization_code (id, consent_intent) "
                        + "ON DELETE " + deleteRule + " ON UPDATE RESTRICT"
                : "";
        return "ALTER TABLE fbs_oauth_token_family "
                + "ADD COLUMN consent_intent VARCHAR(32) CHARACTER SET ascii "
                + "COLLATE ascii_bin NOT NULL, "
                + oauthConsentFamilyCheckClause()
                + ", ADD KEY idx_oauth_family_code_consent (" + parentIndexColumns + ")"
                + foreignKey;
    }

    private static String oauthConsentRequestCheckClause() {
        return "ADD CONSTRAINT chk_oauth_request_consent_intent "
                + "CHECK ((consent_intent IS NULL AND principal_subject_digest IS NULL) "
                + "OR (consent_intent IN ('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION') "
                + "AND principal_subject_digest IS NOT NULL))";
    }

    private static String oauthConsentCodeCheckClause() {
        return "ADD CONSTRAINT chk_oauth_code_consent_intent "
                + "CHECK (consent_intent IN ('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION'))";
    }

    private static String oauthConsentFamilyCheckClause() {
        return "ADD CONSTRAINT chk_oauth_family_consent_intent "
                + "CHECK (consent_intent IN ('FIRST_CONNECT','EXPLICIT_REAUTHORIZATION'))";
    }

    private static String oauthConsentTriggerDdl() {
        return "CREATE TRIGGER trg_oauth_request_consent_intent_once "
                + "BEFORE UPDATE ON fbs_oauth_authorization_request FOR EACH ROW BEGIN "
                + "IF OLD.consent_intent IS NOT NULL "
                + "AND NOT (NEW.consent_intent <=> OLD.consent_intent) THEN "
                + "SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'OAuth consent intent is immutable'; END IF; "
                + "IF OLD.consent_intent IS NULL AND NEW.consent_intent IS NOT NULL "
                + "AND NOT (OLD.status = 'PENDING' "
                + "AND NEW.status IN ('APPROVED','DENIED')) THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = "
                + "'OAuth consent intent must be set by a decision transition'; END IF; END";
    }

    private static void assertOAuthConsentMigrationRejected(
            Path consentMigration,
            String expectedMessage) throws Exception {
        SQLException failure = assertThrows(
                SQLException.class, () -> executeMigration(consentMigration));
        String diagnostic = oauthConsentDiagnosticSummary();
        assertTrue(failure.getMessage().contains(expectedMessage),
                failure.getMessage() + " | consentDiagnostic=" + diagnostic);
        assertEquals(1, scalarInt(oauthConsentMigrationLockFreeSql()),
                "consent migration lock leaked | " + diagnostic);
    }

    private static void executeOAuthConsentMigrationWithDiagnosis(Path consentMigration)
            throws Exception {
        try {
            executeMigration(consentMigration);
        } catch (SQLException migrationFailure) {
            throw new SQLException(
                    migrationFailure.getMessage() + " | consentDiagnostic="
                            + oauthConsentDiagnosticSummary(),
                    migrationFailure.getSQLState(),
                    migrationFailure.getErrorCode(),
                    migrationFailure);
        }
    }

    private static String oauthConsentMigrationLockFreeSql() {
        return "SELECT IS_FREE_LOCK(SHA2(CONCAT(DATABASE(), "
                + "':20260721_independent_board_oauth_consent_intent_lineage_v1'), 256))";
    }

    private static String oauthConsentDiagnosticSummary() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            String originalGroupConcatMaxLen;
            try (ResultSet result = statement.executeQuery(
                    "SELECT @@SESSION.group_concat_max_len")) {
                if (!result.next() || result.getString(1) == null) {
                    throw new SQLException(
                            "OAuth consent metadata snapshot could not read group_concat_max_len");
                }
                originalGroupConcatMaxLen = result.getString(1);
            }

            boolean groupConcatLimitChanged = false;
            Throwable primaryFailure = null;
            try {
                statement.execute("SET SESSION group_concat_max_len = 1048576");
                groupConcatLimitChanged = true;
                return oauthConsentMetadataSnapshot(statement);
            } catch (SQLException | RuntimeException | Error failure) {
                primaryFailure = failure;
                throw failure;
            } finally {
                if (groupConcatLimitChanged) {
                    try {
                        statement.execute("SET SESSION group_concat_max_len = "
                                + originalGroupConcatMaxLen);
                    } catch (SQLException restoreFailure) {
                        if (primaryFailure != null) {
                            primaryFailure.addSuppressed(restoreFailure);
                        } else {
                            throw restoreFailure;
                        }
                    }
                }
            }
        }
    }

    private static String oauthConsentMetadataSnapshot(Statement statement)
            throws SQLException {
        StringBuilder snapshot = new StringBuilder(2048);
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                "SELECT COALESCE((SELECT description FROM u3w_schema_migration "
                        + "WHERE version = '" + OAUTH_CONSENT_MIGRATION_VERSION
                        + "'), 'ABSENT')",
                "receipt");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*),
                       COALESCE(SUM(table_type = 'BASE TABLE'), 0),
                       COALESCE(SUM(engine = 'InnoDB'), 0),
                       COALESCE(SUM(table_collation = 'utf8mb4_unicode_ci'), 0)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                """,
                "tableCount", "baseTableCount", "engineCount", "collationCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                  AND NOT (table_name = 'fbs_oauth_receipt'
                           AND column_name = 'family_created_slot')
                  AND NOT (table_name = 'fbs_oauth_authorization_request'
                           AND column_name = 'consent_intent')
                  AND NOT (table_name = 'fbs_oauth_authorization_code'
                           AND column_name = 'consent_intent')
                  AND NOT (table_name = 'fbs_oauth_token_family'
                           AND column_name = 'consent_intent')
                """,
                "foundationColumnCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                  AND extra = 'STORED GENERATED'
                  AND generation_expression <> ''
                """,
                "generatedColumnCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT SHA2(GROUP_CONCAT(CONCAT(
                           'T:', HEX(CAST(table_name AS BINARY)),
                           '|O:', LPAD(ordinal_position, 3, '0'),
                           '|N:', HEX(CAST(column_name AS BINARY)),
                           '|Y:', HEX(CAST(column_type AS BINARY)),
                           '|U:', HEX(CAST(is_nullable AS BINARY)),
                           '|D:', IF(column_default IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(column_default AS BINARY)))),
                           '|C:', IF(character_set_name IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(character_set_name AS BINARY)))),
                           '|L:', IF(collation_name IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(collation_name AS BINARY)))),
                           '|E:', HEX(CAST(extra AS BINARY)),
                           '|G:', IF(generation_expression IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(generation_expression AS BINARY)))))
                           ORDER BY table_name, ordinal_position SEPARATOR 0x0A), 256)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                  AND NOT (table_name = 'fbs_oauth_receipt'
                           AND column_name = 'family_created_slot')
                  AND NOT (table_name = 'fbs_oauth_authorization_request'
                           AND column_name = 'consent_intent')
                  AND NOT (table_name = 'fbs_oauth_authorization_code'
                           AND column_name = 'consent_intent')
                  AND NOT (table_name = 'fbs_oauth_token_family'
                           AND column_name = 'consent_intent')
                """,
                "foundationColumnDigest");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*),
                       COALESCE(SUM(column_type = 'varchar(128)'
                           AND is_nullable = 'YES'
                           AND column_default IS NULL
                           AND character_set_name = 'ascii'
                           AND collation_name = 'ascii_bin'
                           AND extra = 'STORED GENERATED'
                           AND LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                                   REPLACE(REPLACE(generation_expression, '`', ''), ' ', ''),
                                   CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), ''),
                                   '(', ''), ')', '')) IN (
                               'casewhenaction=_utf8mb4''token_family_created''thenfamily_idelsenullend',
                               'casewhenaction=_ascii''token_family_created''thenfamily_idelsenullend',
                               'casewhenaction=''token_family_created''thenfamily_idelsenullend'
                           )), 0)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'fbs_oauth_receipt'
                  AND column_name = 'family_created_slot'
                """,
                "familySlotColumnNameCount", "familySlotColumnContractCount");
        appendOAuthConsentColumnProbe(
                snapshot,
                statement,
                "fbs_oauth_authorization_request",
                31,
                "YES",
                "requestColumnNameCount",
                "requestColumnContractCount");
        appendOAuthConsentColumnProbe(
                snapshot,
                statement,
                "fbs_oauth_authorization_code",
                27,
                "NO",
                "codeColumnNameCount",
                "codeColumnContractCount");
        appendOAuthConsentColumnProbe(
                snapshot,
                statement,
                "fbs_oauth_token_family",
                28,
                "NO",
                "familyColumnNameCount",
                "familyColumnContractCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*)
                FROM (
                    SELECT table_name, index_name
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND table_name IN (
                          'fbs_oauth_client', 'fbs_oauth_authorization_request',
                          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                          'fbs_oauth_token', 'fbs_oauth_receipt'
                      )
                      AND NOT (table_name = 'fbs_oauth_receipt'
                               AND index_name = 'uk_oauth_receipt_family_created_slot')
                      AND NOT (table_name = 'fbs_oauth_authorization_request'
                               AND index_name = 'uk_oauth_request_id_consent')
                      AND NOT (table_name = 'fbs_oauth_authorization_code'
                               AND index_name IN (
                                   'idx_oauth_code_request_consent',
                                   'uk_oauth_code_id_consent'))
                      AND NOT (table_name = 'fbs_oauth_token_family'
                               AND index_name = 'idx_oauth_family_code_consent')
                    GROUP BY table_name, index_name
                ) base_indexes
                """,
                "foundationIndexCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT SHA2(GROUP_CONCAT(CONCAT(
                           'T:', HEX(CAST(table_name AS BINARY)),
                           '|I:', HEX(CAST(index_name AS BINARY)),
                           '|U:', non_unique,
                           '|Y:', HEX(CAST(index_type AS BINARY)),
                           '|V:', HEX(CAST(is_visible AS BINARY)),
                           '|S:', seq_in_index,
                           '|N:', IF(column_name IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(column_name AS BINARY)))),
                           '|X:', IF(expression IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(expression AS BINARY)))),
                           '|C:', IF(collation IS NULL, 'N',
                                    CONCAT('V:', HEX(CAST(collation AS BINARY)))),
                           '|P:', IF(sub_part IS NULL, 'N', CONCAT('V:', sub_part)),
                           '|Q:', HEX(CAST(nullable AS BINARY)))
                           ORDER BY table_name, index_name, seq_in_index SEPARATOR 0x0A), 256)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                  AND NOT (table_name = 'fbs_oauth_receipt'
                           AND index_name = 'uk_oauth_receipt_family_created_slot')
                  AND NOT (table_name = 'fbs_oauth_authorization_request'
                           AND index_name = 'uk_oauth_request_id_consent')
                  AND NOT (table_name = 'fbs_oauth_authorization_code'
                           AND index_name IN (
                               'idx_oauth_code_request_consent',
                               'uk_oauth_code_id_consent'))
                  AND NOT (table_name = 'fbs_oauth_token_family'
                           AND index_name = 'idx_oauth_family_code_consent')
                """,
                "foundationIndexDigest");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT
                    COALESCE(SUM(table_name = 'fbs_oauth_receipt'
                        AND index_name = 'uk_oauth_receipt_family_created_slot'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_receipt'
                        AND index_name = 'uk_oauth_receipt_family_created_slot'
                        AND non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
                        AND key_part_count = 1 AND non_column_key_parts = 0
                        AND partial_columns = 0
                        AND CAST(column_signature AS BINARY) =
                            CAST('family_created_slot:A' AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
                        AND index_name = 'uk_oauth_request_id_consent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
                        AND index_name = 'uk_oauth_request_id_consent'
                        AND non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
                        AND key_part_count = 2 AND non_column_key_parts = 0
                        AND partial_columns = 0
                        AND CAST(column_signature AS BINARY) =
                            CAST('id:A,consent_intent:A' AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND index_name = 'idx_oauth_code_request_consent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND index_name = 'idx_oauth_code_request_consent'
                        AND non_unique = 1 AND index_type = 'BTREE' AND visibility = 'YES'
                        AND key_part_count = 2 AND non_column_key_parts = 0
                        AND partial_columns = 0
                        AND CAST(column_signature AS BINARY) =
                            CAST('authorization_request_id:A,consent_intent:A' AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND index_name = 'uk_oauth_code_id_consent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND index_name = 'uk_oauth_code_id_consent'
                        AND non_unique = 0 AND index_type = 'BTREE' AND visibility = 'YES'
                        AND key_part_count = 2 AND non_column_key_parts = 0
                        AND partial_columns = 0
                        AND CAST(column_signature AS BINARY) =
                            CAST('id:A,consent_intent:A' AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_token_family'
                        AND index_name = 'idx_oauth_family_code_consent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_token_family'
                        AND index_name = 'idx_oauth_family_code_consent'
                        AND non_unique = 1 AND index_type = 'BTREE' AND visibility = 'YES'
                        AND key_part_count = 2 AND non_column_key_parts = 0
                        AND partial_columns = 0
                        AND CAST(column_signature AS BINARY) =
                            CAST('origin_authorization_code_id:A,consent_intent:A' AS BINARY)), 0)
                FROM (
                    SELECT table_name, index_name, non_unique, index_type,
                           MIN(is_visible) AS visibility,
                           COUNT(*) AS key_part_count,
                           SUM(column_name IS NULL OR expression IS NOT NULL)
                               AS non_column_key_parts,
                           SUM(sub_part IS NOT NULL) AS partial_columns,
                           GROUP_CONCAT(CONCAT(column_name, ':', COALESCE(collation, 'NULL'))
                                        ORDER BY seq_in_index SEPARATOR ',')
                               AS column_signature
                    FROM information_schema.statistics
                    WHERE table_schema = DATABASE()
                      AND ((table_name = 'fbs_oauth_receipt'
                            AND index_name = 'uk_oauth_receipt_family_created_slot')
                        OR (table_name = 'fbs_oauth_authorization_request'
                            AND index_name = 'uk_oauth_request_id_consent')
                        OR (table_name = 'fbs_oauth_authorization_code'
                            AND index_name IN (
                                'idx_oauth_code_request_consent',
                                'uk_oauth_code_id_consent'))
                        OR (table_name = 'fbs_oauth_token_family'
                            AND index_name = 'idx_oauth_family_code_consent'))
                    GROUP BY table_name, index_name, non_unique, index_type
                ) named_indexes
                """,
                "familySlotIndexNameCount", "familySlotIndexContractCount",
                "requestIndexNameCount", "requestIndexContractCount",
                "codeParentIndexNameCount", "codeParentIndexContractCount",
                "codeIdentityIndexNameCount", "codeIdentityIndexContractCount",
                "familyIndexNameCount", "familyIndexContractCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                "SELECT VERSION()",
                "serverVersion");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT
                    (SELECT COUNT(*)
                     FROM information_schema.referential_constraints
                     WHERE constraint_schema = DATABASE()
                       AND unique_constraint_schema = DATABASE()
                       AND table_name IN (
                           'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code',
                           'fbs_oauth_token_family', 'fbs_oauth_token',
                           'fbs_oauth_receipt'
                       )
                       AND NOT (table_name = 'fbs_oauth_authorization_code'
                                AND constraint_name = 'fk_oauth_code_request_consent')
                       AND NOT (table_name = 'fbs_oauth_token_family'
                                AND constraint_name = 'fk_oauth_family_code_consent')),
                    (SELECT COUNT(*)
                     FROM information_schema.key_column_usage
                     WHERE constraint_schema = DATABASE()
                       AND referenced_table_schema = DATABASE()
                       AND referenced_table_name IS NOT NULL
                       AND table_name IN (
                           'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code',
                           'fbs_oauth_token_family', 'fbs_oauth_token',
                           'fbs_oauth_receipt'
                       )
                       AND NOT (table_name = 'fbs_oauth_authorization_code'
                                AND constraint_name = 'fk_oauth_code_request_consent')
                       AND NOT (table_name = 'fbs_oauth_token_family'
                                AND constraint_name = 'fk_oauth_family_code_consent'))
                """,
                "foundationForeignKeyCount", "foundationForeignKeyColumnCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT SHA2(GROUP_CONCAT(CONCAT(
                           'T:', HEX(CAST(rc.table_name AS BINARY)),
                           '|C:', HEX(CAST(rc.constraint_name AS BINARY)),
                           '|S:', IF(rc.unique_constraint_schema = DATABASE(), 'SAME', 'OTHER'),
                           '|K:', HEX(CAST(rc.unique_constraint_name AS BINARY)),
                           '|R:', HEX(CAST(rc.referenced_table_name AS BINARY)),
                           '|U:', HEX(CAST(rc.update_rule AS BINARY)),
                           '|D:', HEX(CAST(rc.delete_rule AS BINARY)),
                           '|M:', HEX(CAST(rc.match_option AS BINARY)),
                           '|O:', kcu.ordinal_position,
                           '|N:', HEX(CAST(kcu.column_name AS BINARY)),
                           '|Q:', IF(kcu.referenced_table_schema = DATABASE(), 'SAME', 'OTHER'),
                           '|P:', HEX(CAST(kcu.referenced_column_name AS BINARY)),
                           '|I:', IF(kcu.position_in_unique_constraint IS NULL, 'N',
                                    CONCAT('V:', kcu.position_in_unique_constraint)))
                           ORDER BY rc.table_name, rc.constraint_name,
                                    kcu.ordinal_position SEPARATOR 0x0A), 256)
                FROM information_schema.referential_constraints rc
                INNER JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_schema = rc.constraint_schema
                 AND kcu.table_name = rc.table_name
                 AND kcu.constraint_name = rc.constraint_name
                WHERE rc.constraint_schema = DATABASE()
                  AND rc.unique_constraint_schema = DATABASE()
                  AND kcu.referenced_table_schema = DATABASE()
                  AND rc.table_name IN (
                      'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code',
                      'fbs_oauth_token_family', 'fbs_oauth_token',
                      'fbs_oauth_receipt'
                  )
                  AND NOT (rc.table_name = 'fbs_oauth_authorization_code'
                           AND rc.constraint_name = 'fk_oauth_code_request_consent')
                  AND NOT (rc.table_name = 'fbs_oauth_token_family'
                           AND rc.constraint_name = 'fk_oauth_family_code_consent')
                """,
                "foundationForeignKeyDigest");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND constraint_name = 'fk_oauth_code_request_consent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND constraint_name = 'fk_oauth_code_request_consent'
                        AND unique_constraint_schema = DATABASE()
                        AND CAST(unique_constraint_name AS BINARY) =
                            CAST('uk_oauth_request_id_consent' AS BINARY)
                        AND CAST(referenced_table_name AS BINARY) =
                            CAST('fbs_oauth_authorization_request' AS BINARY)
                        AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
                        AND match_option = 'NONE'
                        AND key_part_count = 2 AND same_schema_part_count = 2
                        AND CAST(key_signature AS BINARY) = CAST(
                            '1:authorization_request_id>id:1,2:consent_intent>consent_intent:2'
                            AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_token_family'
                        AND constraint_name = 'fk_oauth_family_code_consent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_token_family'
                        AND constraint_name = 'fk_oauth_family_code_consent'
                        AND unique_constraint_schema = DATABASE()
                        AND CAST(unique_constraint_name AS BINARY) =
                            CAST('uk_oauth_code_id_consent' AS BINARY)
                        AND CAST(referenced_table_name AS BINARY) =
                            CAST('fbs_oauth_authorization_code' AS BINARY)
                        AND update_rule = 'RESTRICT' AND delete_rule = 'RESTRICT'
                        AND match_option = 'NONE'
                        AND key_part_count = 2 AND same_schema_part_count = 2
                        AND CAST(key_signature AS BINARY) = CAST(
                            '1:origin_authorization_code_id>id:1,2:consent_intent>consent_intent:2'
                            AS BINARY)), 0)
                FROM (
                    SELECT rc.table_name, rc.constraint_name,
                           rc.unique_constraint_schema, rc.unique_constraint_name,
                           rc.referenced_table_name, rc.update_rule,
                           rc.delete_rule, rc.match_option,
                           COUNT(*) AS key_part_count,
                           SUM(kcu.referenced_table_schema = DATABASE())
                               AS same_schema_part_count,
                           GROUP_CONCAT(CONCAT(
                               kcu.ordinal_position, ':', kcu.column_name, '>',
                               kcu.referenced_column_name, ':',
                               kcu.position_in_unique_constraint)
                               ORDER BY kcu.ordinal_position SEPARATOR ',') AS key_signature
                    FROM information_schema.referential_constraints rc
                    INNER JOIN information_schema.key_column_usage kcu
                      ON kcu.constraint_schema = rc.constraint_schema
                     AND kcu.table_name = rc.table_name
                     AND kcu.constraint_name = rc.constraint_name
                    WHERE rc.constraint_schema = DATABASE()
                      AND ((rc.table_name = 'fbs_oauth_authorization_code'
                            AND rc.constraint_name = 'fk_oauth_code_request_consent')
                        OR (rc.table_name = 'fbs_oauth_token_family'
                            AND rc.constraint_name = 'fk_oauth_family_code_consent'))
                    GROUP BY rc.table_name, rc.constraint_name,
                             rc.unique_constraint_schema, rc.unique_constraint_name,
                             rc.referenced_table_name, rc.update_rule,
                             rc.delete_rule, rc.match_option
                ) named_foreign_keys
                """,
                "codeForeignKeyNameCount", "codeForeignKeyContractCount",
                "familyForeignKeyNameCount", "familyForeignKeyContractCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*), COALESCE(SUM(enforced = 'YES'), 0)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'CHECK'
                  AND table_name IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                  AND NOT (table_name = 'fbs_oauth_authorization_request'
                           AND constraint_name = 'chk_oauth_request_consent_intent')
                  AND NOT (table_name = 'fbs_oauth_authorization_code'
                           AND constraint_name = 'chk_oauth_code_consent_intent')
                  AND NOT (table_name = 'fbs_oauth_token_family'
                           AND constraint_name = 'chk_oauth_family_consent_intent')
                """,
                "foundationCheckCount", "foundationEnforcedCheckCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT SHA2(GROUP_CONCAT(CONCAT(
                           'T:', HEX(CAST(table_name AS BINARY)),
                           '|C:', HEX(CAST(constraint_name AS BINARY)),
                           '|E:', HEX(CAST(enforced AS BINARY)),
                           '|X:', HEX(CAST(check_clause AS BINARY)))
                           ORDER BY table_name, constraint_name SEPARATOR 0x0A), 256)
                FROM (
                    SELECT tc.table_name, tc.constraint_name,
                           tc.enforced, cc.check_clause
                    FROM information_schema.table_constraints tc
                    INNER JOIN information_schema.check_constraints cc
                      ON cc.constraint_schema = tc.constraint_schema
                     AND cc.constraint_name = tc.constraint_name
                    WHERE tc.constraint_schema = DATABASE()
                      AND tc.constraint_type = 'CHECK'
                      AND tc.table_name IN (
                          'fbs_oauth_client', 'fbs_oauth_authorization_request',
                          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                          'fbs_oauth_token', 'fbs_oauth_receipt'
                      )
                      AND NOT (tc.table_name = 'fbs_oauth_authorization_request'
                               AND tc.constraint_name = 'chk_oauth_request_consent_intent')
                      AND NOT (tc.table_name = 'fbs_oauth_authorization_code'
                               AND tc.constraint_name = 'chk_oauth_code_consent_intent')
                      AND NOT (tc.table_name = 'fbs_oauth_token_family'
                               AND tc.constraint_name = 'chk_oauth_family_consent_intent')
                ) base_checks
                """,
                "foundationCheckDigest");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
                        AND constraint_name = 'chk_oauth_request_consent_intent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND constraint_name = 'chk_oauth_code_consent_intent'), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_token_family'
                        AND constraint_name = 'chk_oauth_family_consent_intent'), 0)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND ((table_name = 'fbs_oauth_authorization_request'
                        AND constraint_name = 'chk_oauth_request_consent_intent')
                    OR (table_name = 'fbs_oauth_authorization_code'
                        AND constraint_name = 'chk_oauth_code_consent_intent')
                    OR (table_name = 'fbs_oauth_token_family'
                        AND constraint_name = 'chk_oauth_family_consent_intent'))
                """,
                "requestCheckNameCount", "codeCheckNameCount", "familyCheckNameCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_request'
                        AND constraint_name = 'chk_oauth_request_consent_intent'
                        AND enforced = 'YES'
                        AND CAST(normalized_clause AS BINARY) = CAST(
                            'consent_intentisnullandprincipal_subject_digestisnullorconsent_intentin''first_connect'',''explicit_reauthorization''andprincipal_subject_digestisnotnull'
                            AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_authorization_code'
                        AND constraint_name = 'chk_oauth_code_consent_intent'
                        AND enforced = 'YES'
                        AND CAST(normalized_clause AS BINARY) = CAST(
                            'consent_intentin''first_connect'',''explicit_reauthorization'''
                            AS BINARY)), 0),
                    COALESCE(SUM(table_name = 'fbs_oauth_token_family'
                        AND constraint_name = 'chk_oauth_family_consent_intent'
                        AND enforced = 'YES'
                        AND CAST(normalized_clause AS BINARY) = CAST(
                            'consent_intentin''first_connect'',''explicit_reauthorization'''
                            AS BINARY)), 0)
                FROM (
                    SELECT tc.table_name, tc.constraint_name, tc.enforced,
                           LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                               REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(cc.check_clause,
                                   '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                                   '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                                   CHAR(13), ''), CHAR(92), ''), '(', ''), ')', ''))
                               AS normalized_clause
                    FROM information_schema.table_constraints tc
                    INNER JOIN information_schema.check_constraints cc
                      ON cc.constraint_schema = tc.constraint_schema
                     AND cc.constraint_name = tc.constraint_name
                    WHERE tc.constraint_schema = DATABASE()
                      AND tc.constraint_type = 'CHECK'
                      AND ((tc.table_name = 'fbs_oauth_authorization_request'
                            AND tc.constraint_name = 'chk_oauth_request_consent_intent')
                        OR (tc.table_name = 'fbs_oauth_authorization_code'
                            AND tc.constraint_name = 'chk_oauth_code_consent_intent')
                        OR (tc.table_name = 'fbs_oauth_token_family'
                            AND tc.constraint_name = 'chk_oauth_family_consent_intent'))
                ) named_checks
                """,
                "requestCheckContractCount", "codeCheckContractCount",
                "familyCheckContractCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*)
                FROM information_schema.triggers
                WHERE trigger_schema = DATABASE()
                  AND event_object_table IN (
                      'fbs_oauth_client', 'fbs_oauth_authorization_request',
                      'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                      'fbs_oauth_token', 'fbs_oauth_receipt'
                  )
                  AND NOT (event_object_table = 'fbs_oauth_authorization_request'
                           AND trigger_name = 'trg_oauth_request_consent_intent_once')
                """,
                "foundationTriggerCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*)
                FROM (
                    SELECT trigger_name, event_object_table, event_manipulation,
                           action_timing, action_orientation,
                           LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                               action_statement, '`', ''), ' ', ''), CHAR(9), ''),
                               CHAR(10), ''), CHAR(13), ''), CHAR(92), ''))
                               AS normalized_action
                    FROM information_schema.triggers
                    WHERE trigger_schema = DATABASE()
                      AND event_object_table IN (
                          'fbs_oauth_client', 'fbs_oauth_authorization_request',
                          'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                          'fbs_oauth_token', 'fbs_oauth_receipt'
                      )
                      AND NOT (event_object_table = 'fbs_oauth_authorization_request'
                               AND trigger_name = 'trg_oauth_request_consent_intent_once')
                      AND action_condition IS NULL
                ) receipt_triggers
                WHERE event_object_table = 'fbs_oauth_receipt'
                  AND action_timing = 'BEFORE'
                  AND action_orientation = 'ROW'
                  AND CAST(normalized_action AS BINARY) = CAST(
                      'signalsqlstate''45000''setmessage_text=''oauthreceiptsareimmutable'''
                      AS BINARY)
                  AND ((trigger_name = 'trg_oauth_receipt_no_update'
                        AND event_manipulation = 'UPDATE')
                    OR (trigger_name = 'trg_oauth_receipt_no_delete'
                        AND event_manipulation = 'DELETE'))
                """,
                "foundationTriggerContractCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT COUNT(*), COALESCE(SUM(
                           event_object_table = 'fbs_oauth_authorization_request'
                           AND event_manipulation = 'UPDATE'
                           AND action_timing = 'BEFORE'
                           AND action_orientation = 'ROW'
                           AND action_condition IS NULL
                           AND CAST(normalized_action AS BINARY) = CAST(
                               'beginifold.consent_intentisnotnullandnotnew.consent_intent<=>old.consent_intentthensignalsqlstate''45000''setmessage_text=''oauthconsentintentisimmutable'';endif;ifold.consent_intentisnullandnew.consent_intentisnotnullandnotold.status=''pending''andnew.statusin''approved'',''denied''thensignalsqlstate''45000''setmessage_text=''oauthconsentintentmustbesetbyadecisiontransition'';endif;end'
                               AS BINARY)), 0)
                FROM (
                    SELECT trigger_name, event_object_table, event_manipulation,
                           action_timing, action_orientation, action_condition,
                           LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                               REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(action_statement,
                                   '_utf8mb4', ''), '_utf8mb3', ''), '_ascii', ''),
                                   '`', ''), ' ', ''), CHAR(9), ''), CHAR(10), ''),
                                   CHAR(13), ''), CHAR(92), ''), '(', ''), ')', ''))
                               AS normalized_action
                    FROM information_schema.triggers
                    WHERE trigger_schema = DATABASE()
                      AND event_object_table = 'fbs_oauth_authorization_request'
                      AND trigger_name = 'trg_oauth_request_consent_intent_once'
                ) named_trigger
                """,
                "requestTriggerNameCount", "requestTriggerContractCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                """
                SELECT
                    (SELECT COUNT(*)
                     FROM information_schema.columns
                     WHERE table_schema = DATABASE()
                       AND table_name IN (
                           'fbs_oauth_client', 'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                           'fbs_oauth_token', 'fbs_oauth_receipt'
                       )),
                    (SELECT COUNT(*)
                     FROM (
                         SELECT table_name, index_name
                         FROM information_schema.statistics
                         WHERE table_schema = DATABASE()
                           AND table_name IN (
                               'fbs_oauth_client', 'fbs_oauth_authorization_request',
                               'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                               'fbs_oauth_token', 'fbs_oauth_receipt'
                           )
                         GROUP BY table_name, index_name
                     ) all_indexes),
                    (SELECT COUNT(*)
                     FROM information_schema.referential_constraints
                     WHERE constraint_schema = DATABASE()
                       AND table_name IN (
                           'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code',
                           'fbs_oauth_token_family', 'fbs_oauth_token',
                           'fbs_oauth_receipt'
                       )),
                    (SELECT COUNT(*)
                     FROM information_schema.key_column_usage
                     WHERE constraint_schema = DATABASE()
                       AND referenced_table_name IS NOT NULL
                       AND table_name IN (
                           'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code',
                           'fbs_oauth_token_family', 'fbs_oauth_token',
                           'fbs_oauth_receipt'
                       )),
                    (SELECT COUNT(*)
                     FROM information_schema.table_constraints
                     WHERE constraint_schema = DATABASE()
                       AND constraint_type = 'CHECK'
                       AND table_name IN (
                           'fbs_oauth_client', 'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                           'fbs_oauth_token', 'fbs_oauth_receipt'
                       )),
                    (SELECT COALESCE(SUM(enforced = 'YES'), 0)
                     FROM information_schema.table_constraints
                     WHERE constraint_schema = DATABASE()
                       AND constraint_type = 'CHECK'
                       AND table_name IN (
                           'fbs_oauth_client', 'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                           'fbs_oauth_token', 'fbs_oauth_receipt'
                       )),
                    (SELECT COUNT(*)
                     FROM information_schema.triggers
                     WHERE trigger_schema = DATABASE()
                       AND event_object_table IN (
                           'fbs_oauth_client', 'fbs_oauth_authorization_request',
                           'fbs_oauth_authorization_code', 'fbs_oauth_token_family',
                           'fbs_oauth_token', 'fbs_oauth_receipt'
                       ))
                """,
                "totalColumnCount", "totalIndexCount", "totalForeignKeyCount",
                "totalForeignKeyColumnCount", "totalCheckCount",
                "totalEnforcedCheckCount", "totalTriggerCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                "SELECT COUNT(*) FROM information_schema.routines "
                        + "WHERE routine_schema = DATABASE() AND routine_name IN "
                        + "('u3w_assert_ib_oauth_consent_intent_20260721',"
                        + "'u3w_migrate_ib_oauth_consent_intent_20260721',"
                        + "'u3w_finalize_ib_oauth_consent_intent_20260721')",
                "routineCount");
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                oauthConsentMigrationLockFreeSql(),
                "lockFree");
        return snapshot.toString();
    }

    private static void appendOAuthConsentColumnProbe(
            StringBuilder snapshot,
            Statement statement,
            String tableName,
            int ordinalPosition,
            String nullable,
            String nameLabel,
            String contractLabel) throws SQLException {
        appendOAuthConsentMetadataProbe(
                snapshot,
                statement,
                "SELECT COUNT(*), COALESCE(SUM(ordinal_position = " + ordinalPosition
                        + " AND column_type = 'varchar(32)' AND is_nullable = '"
                        + nullable + "' AND column_default IS NULL "
                        + "AND character_set_name = 'ascii' "
                        + "AND collation_name = 'ascii_bin' AND extra = '' "
                        + "AND COALESCE(generation_expression, '') = ''), 0) "
                        + "FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = '"
                        + tableName + "' AND column_name = 'consent_intent'",
                nameLabel,
                contractLabel);
    }

    private static void appendOAuthConsentMetadataProbe(
            StringBuilder snapshot,
            Statement statement,
            String sql,
            String... labels) throws SQLException {
        try (ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new SQLException("OAuth consent metadata probe returned no row: "
                        + String.join(",", labels));
            }
            int columnCount = result.getMetaData().getColumnCount();
            if (columnCount != labels.length) {
                throw new SQLException("OAuth consent metadata probe column mismatch for "
                        + String.join(",", labels) + ": " + columnCount);
            }
            for (int index = 0; index < labels.length; index++) {
                if (snapshot.length() > 0) {
                    snapshot.append('|');
                }
                String value = result.getString(index + 1);
                snapshot.append(labels[index]).append('=')
                        .append(value == null ? "NULL" : value);
            }
            if (result.next()) {
                throw new SQLException("OAuth consent metadata probe returned multiple rows: "
                        + String.join(",", labels));
            }
        }
    }

    private static void assertOAuthConsentAppliedShape() throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + OAUTH_CONSENT_MIGRATION_VERSION + "' "
                + "AND description = '" + OAUTH_CONSENT_APPLIED + "'"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND column_name = 'consent_intent' "
                + "AND column_type = 'varchar(32)' AND column_default IS NULL "
                + "AND character_set_name = 'ascii' AND collation_name = 'ascii_bin' "
                + "AND extra = '' AND COALESCE(generation_expression, '') = '' AND ("
                + "(table_name = 'fbs_oauth_authorization_request' "
                + "AND ordinal_position = 31 AND is_nullable = 'YES') OR "
                + "(table_name = 'fbs_oauth_authorization_code' "
                + "AND ordinal_position = 27 AND is_nullable = 'NO') OR "
                + "(table_name = 'fbs_oauth_token_family' "
                + "AND ordinal_position = 28 AND is_nullable = 'NO'))"));

        assertOAuthConsentIndex(
                "fbs_oauth_authorization_request",
                "uk_oauth_request_id_consent",
                0,
                2,
                "id:A,consent_intent:A");
        assertOAuthConsentIndex(
                "fbs_oauth_authorization_code",
                "uk_oauth_code_id_consent",
                0,
                2,
                "id:A,consent_intent:A");
        assertOAuthConsentIndex(
                "fbs_oauth_authorization_code",
                "idx_oauth_code_request_consent",
                1,
                2,
                "authorization_request_id:A,consent_intent:A");
        assertOAuthConsentIndex(
                "fbs_oauth_token_family",
                "idx_oauth_family_code_consent",
                1,
                2,
                "origin_authorization_code_id:A,consent_intent:A");

        assertOAuthConsentForeignKey(
                "fbs_oauth_authorization_code",
                "fk_oauth_code_request_consent",
                "uk_oauth_request_id_consent",
                "fbs_oauth_authorization_request",
                "1:authorization_request_id>id:1,2:consent_intent>consent_intent:2");
        assertOAuthConsentForeignKey(
                "fbs_oauth_token_family",
                "fk_oauth_family_code_consent",
                "uk_oauth_code_id_consent",
                "fbs_oauth_authorization_code",
                "1:origin_authorization_code_id>id:1,2:consent_intent>consent_intent:2");

        assertOAuthConsentCheck(
                "fbs_oauth_authorization_request",
                "chk_oauth_request_consent_intent",
                "consent_intentisnullandprincipal_subject_digestisnullorconsent_intentin"
                        + "'first_connect','explicit_reauthorization'"
                        + "andprincipal_subject_digestisnotnull");
        assertOAuthConsentCheck(
                "fbs_oauth_authorization_code",
                "chk_oauth_code_consent_intent",
                "consent_intentin'first_connect','explicit_reauthorization'");
        assertOAuthConsentCheck(
                "fbs_oauth_token_family",
                "chk_oauth_family_consent_intent",
                "consent_intentin'first_connect','explicit_reauthorization'");

        String expectedTrigger = "beginifold.consent_intentisnotnullandnotnew."
                + "consent_intent<=>old.consent_intentthensignalsqlstate'45000'"
                + "setmessage_text='oauthconsentintentisimmutable';endif;ifold."
                + "consent_intentisnullandnew.consent_intentisnotnullandnotold.status="
                + "'pending'andnew.statusin'approved','denied'thensignalsqlstate'45000'"
                + "setmessage_text='oauthconsentintentmustbesetbyadecisiontransition';"
                + "endif;end";
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM (SELECT trigger_name, "
                + "event_object_table, event_manipulation, action_timing, "
                + "action_orientation, action_condition, "
                + consentNormalizedMetadataExpression("action_statement")
                + " AS normalized_action FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() "
                + "AND trigger_name = 'trg_oauth_request_consent_intent_once') t "
                + "WHERE event_object_table = 'fbs_oauth_authorization_request' "
                + "AND event_manipulation = 'UPDATE' AND action_timing = 'BEFORE' "
                + "AND action_orientation = 'ROW' AND action_condition IS NULL "
                + "AND CAST(normalized_action AS BINARY) = CAST('"
                + escapeOAuthConsentSqlLiteral(expectedTrigger)
                + "' AS BINARY)"));

        assertEquals(148, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(57, scalarInt("SELECT COUNT(*) FROM (SELECT table_name,index_name "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt') "
                + "GROUP BY table_name,index_name) oauth_indexes"));
        assertEquals(16, scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.referential_constraints "
                + "WHERE constraint_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_authorization_request','fbs_oauth_authorization_code',"
                + "'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(61, scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.key_column_usage "
                + "WHERE constraint_schema = DATABASE() AND referenced_table_name IS NOT NULL "
                + "AND table_name IN ('fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(36, scalarInt("SELECT COUNT(*) "
                + "FROM information_schema.table_constraints "
                + "WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(36, scalarInt("SELECT COALESCE(SUM(enforced = 'YES'), 0) "
                + "FROM information_schema.table_constraints "
                + "WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(3, scalarInt("SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() AND event_object_table IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM information_schema.routines "
                + "WHERE routine_schema = DATABASE() AND routine_name IN "
                + "('u3w_assert_ib_oauth_consent_intent_20260721',"
                + "'u3w_migrate_ib_oauth_consent_intent_20260721',"
                + "'u3w_finalize_ib_oauth_consent_intent_20260721')"));
        assertEquals(1, scalarInt(oauthConsentMigrationLockFreeSql()));
    }

    private static void assertOAuthConsentIndex(
            String tableName,
            String indexName,
            int nonUnique,
            int keyPartCount,
            String columnSignature) throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM (SELECT index_name "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name = '" + tableName + "' AND index_name = '" + indexName
                + "' GROUP BY index_name HAVING COUNT(*) = " + keyPartCount
                + " AND MIN(non_unique) = " + nonUnique
                + " AND MAX(non_unique) = " + nonUnique
                + " AND MIN(index_type) = 'BTREE' AND MAX(index_type) = 'BTREE' "
                + "AND MIN(is_visible) = 'YES' AND MAX(is_visible) = 'YES' "
                + "AND SUM(column_name IS NULL OR expression IS NOT NULL) = 0 "
                + "AND SUM(sub_part IS NOT NULL) = 0 "
                + "AND CAST(GROUP_CONCAT(CONCAT(column_name, ':', "
                + "COALESCE(collation, 'NULL')) ORDER BY seq_in_index SEPARATOR ',') "
                + "AS BINARY) = CAST('" + columnSignature + "' AS BINARY)) exact_index"));
    }

    private static void assertOAuthConsentForeignKey(
            String tableName,
            String constraintName,
            String uniqueConstraintName,
            String referencedTableName,
            String keySignature) throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM (SELECT rc.constraint_name "
                + "FROM information_schema.referential_constraints rc "
                + "INNER JOIN information_schema.key_column_usage kcu "
                + "ON kcu.constraint_schema = rc.constraint_schema "
                + "AND kcu.table_name = rc.table_name "
                + "AND kcu.constraint_name = rc.constraint_name "
                + "WHERE rc.constraint_schema = DATABASE() "
                + "AND rc.table_name = '" + tableName + "' "
                + "AND rc.constraint_name = '" + constraintName + "' "
                + "AND rc.unique_constraint_schema = DATABASE() "
                + "AND rc.unique_constraint_name = '" + uniqueConstraintName + "' "
                + "AND rc.referenced_table_name = '" + referencedTableName + "' "
                + "AND rc.update_rule = 'RESTRICT' AND rc.delete_rule = 'RESTRICT' "
                + "AND rc.match_option = 'NONE' "
                + "GROUP BY rc.constraint_name HAVING COUNT(*) = 2 "
                + "AND SUM(kcu.referenced_table_schema = DATABASE()) = 2 "
                + "AND CAST(GROUP_CONCAT(CONCAT(kcu.ordinal_position, ':', "
                + "kcu.column_name, '>', kcu.referenced_column_name, ':', "
                + "kcu.position_in_unique_constraint) ORDER BY kcu.ordinal_position "
                + "SEPARATOR ',') AS BINARY) = CAST('" + keySignature
                + "' AS BINARY)) exact_foreign_key"));
    }

    private static void assertOAuthConsentCheck(
            String tableName,
            String constraintName,
            String normalizedClause) throws SQLException {
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM (SELECT tc.table_name, "
                + "tc.constraint_name, tc.enforced, "
                + consentNormalizedMetadataExpression("cc.check_clause")
                + " AS normalized_clause FROM information_schema.table_constraints tc "
                + "INNER JOIN information_schema.check_constraints cc "
                + "ON cc.constraint_schema = tc.constraint_schema "
                + "AND cc.constraint_name = tc.constraint_name "
                + "WHERE tc.constraint_schema = DATABASE() "
                + "AND tc.constraint_type = 'CHECK' AND tc.table_name = '" + tableName
                + "' AND tc.constraint_name = '" + constraintName + "') c "
                + "WHERE enforced = 'YES' AND CAST(normalized_clause AS BINARY) = CAST('"
                + escapeOAuthConsentSqlLiteral(normalizedClause) + "' AS BINARY)"));
    }

    private static String consentNormalizedMetadataExpression(String expression) {
        return "LOWER(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE("
                + "REPLACE(REPLACE(REPLACE(" + expression + ", '_utf8mb4', ''), "
                + "'_utf8mb3', ''), '_ascii', ''), CHAR(96), ''), ' ', ''), "
                + "CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), ''), "
                + "'(', ''), ')', ''))";
    }

    private static String escapeOAuthConsentSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private static String oauthLegacyTokenInsert(String marker, String familyId) {
        return "INSERT INTO fbs_oauth_token SET "
                + "token_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "family_id = '" + familyId + "', token_type = 'ACCESS', generation = 0, "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "scope_canonical = 'identity.read entitlement.read "
                + "board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "status = 'ACTIVE', issued_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:10:00.000'";
    }

    private static void seedOAuthConsentEntitlement() throws SQLException {
        execute("INSERT INTO fbs_product_entitlement "
                + "(enterprise_id, member_id, user_id, product_code, plan_code, status, "
                + "valid_from, valid_until, version) VALUES "
                + "(1001, 101, 501, 'FBSIR_INDEPENDENT_BOARD', 'BOARD_VIP', 'ACTIVE', "
                + "'2026-07-20 00:00:00.000', '2026-08-20 00:00:00.000', 1)");
    }

    private static String oauthPendingConsentRequestInsert(String marker) {
        return "INSERT INTO fbs_oauth_authorization_request SET "
                + "request_handle_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "client_id = REPEAT('A', 43), "
                + "redirect_uri = 'http://127.0.0.1:49152/oauth/callback', "
                + "code_challenge = REPEAT('B', 43), code_challenge_method = 'S256', "
                + "state_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "state_key_ref = 'kms:test', "
                + "state_nonce = UNHEX('000102030405060708090a0b'), "
                + "state_ciphertext = UNHEX(REPEAT('ab', 32)), "
                + "issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', "
                + "scope_canonical = 'identity.read entitlement.read "
                + "board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "consent_intent = NULL, status = 'PENDING', "
                + "requested_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:05:00.000'";
    }

    private static String oauthConsentCodeInsert(
            long requestId,
            String marker,
            String consentIntent) {
        return "INSERT INTO fbs_oauth_authorization_code SET "
                + "code_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                + "authorization_request_id = " + requestId + ", "
                + "client_id = REPEAT('A', 43), "
                + "redirect_uri = 'http://127.0.0.1:49152/oauth/callback', "
                + "code_challenge = REPEAT('B', 43), code_challenge_method = 'S256', "
                + "issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', "
                + "scope_canonical = 'identity.read entitlement.read "
                + "board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                + "consent_intent = '" + consentIntent + "', status = 'ACTIVE', "
                + "issued_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:01:00.000'";
    }

    private static String oauthConsentFamilyInsert(
            long authorizationCodeId,
            String familyId,
            String consentIntent) {
        return "INSERT INTO fbs_oauth_token_family SET "
                + "family_id = '" + familyId + "', "
                + "origin_authorization_code_id = " + authorizationCodeId + ", "
                + "client_id = REPEAT('A', 43), enterprise_id = 1001, "
                + "member_id = 101, user_id = 501, "
                + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                + "connector_code = 'fbs-connector', issuer_uri = 'https://api2.u3w.com', "
                + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                + "scope_canonical = 'identity.read entitlement.read "
                + "board.meeting.reserve board.receipt.write', "
                + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "consent_intent = '" + consentIntent + "', "
                + "status = 'PENDING_BINDING', current_refresh_generation = 0, "
                + "issued_at = '2026-07-20 00:00:00.000', "
                + "expires_at = '2026-07-20 00:30:00.000'";
    }

    private static String oauthProvenanceDiagnosticSummary() throws SQLException {
        String shape = scalarString("SELECT CONCAT("
                + "'columns=', (SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')), "
                + "';indexes=', (SELECT COUNT(*) FROM (SELECT table_name,index_name "
                + "FROM information_schema.statistics WHERE table_schema = DATABASE() "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt') "
                + "GROUP BY table_name,index_name) i), "
                + "';generated=', (SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND extra = 'STORED GENERATED' "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')), "
                + "';expression=', COALESCE((SELECT generation_expression "
                + "FROM information_schema.columns WHERE table_schema = DATABASE() "
                + "AND table_name = 'fbs_oauth_receipt' "
                + "AND column_name = 'family_created_slot'), 'NULL'), "
                + "';normalized=', COALESCE((SELECT LOWER(REPLACE(REPLACE(REPLACE(REPLACE("
                + "REPLACE(REPLACE(REPLACE(REPLACE(generation_expression, CHAR(96), ''), "
                + "' ', ''), CHAR(9), ''), CHAR(10), ''), CHAR(13), ''), CHAR(92), ''), "
                + "'(', ''), ')', '')) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt' "
                + "AND column_name = 'family_created_slot'), 'NULL'), "
                + "';index=', COALESCE((SELECT GROUP_CONCAT(CONCAT(index_name,':',non_unique,':'"
                + ",is_visible,':',column_name,':',COALESCE(collation,'NULL')) "
                + "ORDER BY seq_in_index SEPARATOR ',') FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt' "
                + "AND index_name = 'uk_oauth_receipt_family_created_slot'), 'NULL'), "
                + "';fks=', (SELECT COUNT(*) FROM information_schema.referential_constraints "
                + "WHERE constraint_schema = DATABASE() AND table_name IN "
                + "('fbs_oauth_authorization_request','fbs_oauth_authorization_code',"
                + "'fbs_oauth_token_family','fbs_oauth_token','fbs_oauth_receipt')), "
                + "';checks=', (SELECT COUNT(*) FROM information_schema.table_constraints "
                + "WHERE constraint_schema = DATABASE() AND constraint_type = 'CHECK' "
                + "AND table_name IN ('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')), "
                + "';triggers=', (SELECT COUNT(*) FROM information_schema.triggers "
                + "WHERE trigger_schema = DATABASE() AND event_object_table IN "
                + "('fbs_oauth_client','fbs_oauth_authorization_request',"
                + "'fbs_oauth_authorization_code','fbs_oauth_token_family',"
                + "'fbs_oauth_token','fbs_oauth_receipt')))" );
        String checkDigest;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET SESSION group_concat_max_len = 1048576");
            try (ResultSet result = statement.executeQuery("SELECT SHA2(GROUP_CONCAT(CONCAT("
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
                    + "'fbs_oauth_token','fbs_oauth_receipt')) exact_checks")) {
                assertTrue(result.next());
                checkDigest = result.getString(1);
                assertNotNull(checkDigest);
            }
        }
        return shape + ";checkDigest=" + checkDigest;
    }

    private static void dropOAuthProvenanceShapeAndReceipt() throws SQLException {
        execute(
                "ALTER TABLE fbs_oauth_receipt "
                        + "DROP INDEX uk_oauth_receipt_family_created_slot, "
                        + "DROP COLUMN family_created_slot",
                "DELETE FROM u3w_schema_migration WHERE version = '"
                        + OAUTH_PROVENANCE_MIGRATION_VERSION + "'");
    }

    private static String oauthProvenanceColumnOnlyDdl() {
        return "ALTER TABLE fbs_oauth_receipt ADD COLUMN family_created_slot "
                + "VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin "
                + "GENERATED ALWAYS AS (CASE WHEN action = 'TOKEN_FAMILY_CREATED' "
                + "THEN family_id ELSE NULL END) STORED";
    }

    private static String oauthProvenanceCompleteDdl() {
        return oauthProvenanceColumnOnlyDdl()
                + ", ADD UNIQUE KEY uk_oauth_receipt_family_created_slot "
                + "(family_created_slot)";
    }

    private static void seedOAuthProvenanceLineage() throws SQLException {
        List<String> statements = new ArrayList<>();
        statements.add("SET FOREIGN_KEY_CHECKS = 0");
        statements.add(invalidOAuthClientInsert("ACTIVE"));
        for (int ordinal = 1; ordinal <= 9; ordinal++) {
            String marker = String.format("%02x", 32 + ordinal);
            statements.add("INSERT INTO fbs_oauth_authorization_code SET "
                    + "id = " + ordinal + ", code_digest = UNHEX(REPEAT('" + marker + "', 32)), "
                    + "authorization_request_id = " + ordinal + ", "
                    + "client_id = REPEAT('A', 43), "
                    + "redirect_uri = 'http://127.0.0.1:49152/oauth/callback', "
                    + "code_challenge = REPEAT('B', 43), code_challenge_method = 'S256', "
                    + "issuer_uri = 'https://api2.u3w.com', "
                    + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                    + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                    + "connector_code = 'fbs-connector', "
                    + "scope_canonical = 'identity.read entitlement.read "
                    + "board.meeting.reserve board.receipt.write', "
                    + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                    + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                    + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                    + "status = 'ACTIVE', issued_at = '2026-07-20 00:00:00.000', "
                    + "expires_at = '2026-07-20 00:01:00.000'");
            String terminal = ordinal == 1
                    ? "status = 'PENDING_BINDING'"
                    : "status = 'REVOKED', terminated_at = '2026-07-20 00:10:00.000'";
            statements.add("INSERT INTO fbs_oauth_token_family SET "
                    + "family_id = 'family-provenance-" + ordinal + "', "
                    + "origin_authorization_code_id = " + ordinal + ", "
                    + "client_id = REPEAT('A', 43), enterprise_id = 1001, "
                    + "member_id = 101, user_id = 501, "
                    + "product_code = 'FBSIR_INDEPENDENT_BOARD', source_code = 'WORKBUDDY', "
                    + "connector_code = 'fbs-connector', issuer_uri = 'https://api2.u3w.com', "
                    + "resource_uri = 'https://api2.u3w.com/fbs-mcp/mcp', "
                    + "scope_canonical = 'identity.read entitlement.read "
                    + "board.meeting.reserve board.receipt.write', "
                    + "scope_digest = UNHEX('351185152796016cff0c4aba15af369a4891a1e484d9a1cb3513b001e5b8e1d1'), "
                    + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                    + terminal + ", current_refresh_generation = 0, "
                    + "issued_at = '2026-07-20 00:00:00.000', "
                    + "expires_at = '2026-07-20 00:30:00.000'");
        }
        statements.add("SET FOREIGN_KEY_CHECKS = 1");
        execute(statements.toArray(String[]::new));
    }

    private static String provenanceCreatedReceiptInsert(
            String receiptId,
            String correlationId,
            long authorizationCodeId,
            String familyId) {
        return "INSERT INTO fbs_oauth_receipt SET "
                + "receipt_id = '" + receiptId + "', action = 'TOKEN_FAMILY_CREATED', "
                + "client_id = REPEAT('A', 43), authorization_code_id = "
                + authorizationCodeId + ", family_id = '" + familyId + "', "
                + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "actor_type = 'CLIENT', actor_subject_digest = UNHEX(REPEAT('52', 32)), "
                + "correlation_id = '" + correlationId + "', "
                + "payload_digest = UNHEX(REPEAT('53', 32)), "
                + "evidence_level = 'ACTION_COMPLETED'";
    }

    private static String provenanceRevokedReceiptInsert(
            String receiptId,
            String correlationId,
            String familyId) {
        return "INSERT INTO fbs_oauth_receipt SET "
                + "receipt_id = '" + receiptId + "', action = 'TOKEN_FAMILY_REVOKED', "
                + "client_id = REPEAT('A', 43), family_id = '" + familyId + "', "
                + "enterprise_id = 1001, member_id = 101, user_id = 501, "
                + "principal_subject_digest = UNHEX(REPEAT('51', 32)), "
                + "actor_type = 'CLIENT', actor_subject_digest = UNHEX(REPEAT('52', 32)), "
                + "correlation_id = '" + correlationId + "', "
                + "payload_digest = UNHEX(REPEAT('54', 32)), "
                + "evidence_level = 'ACTION_COMPLETED'";
    }

    private static void assertOAuthProvenanceNullSemantics() throws SQLException {
        execute(
                provenanceRevokedReceiptInsert(
                        "receipt-provenance-revoked-1", "correlation-provenance-revoked-1",
                        "family-provenance-1"),
                provenanceRevokedReceiptInsert(
                        "receipt-provenance-revoked-2", "correlation-provenance-revoked-2",
                        "family-provenance-1"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE action = 'TOKEN_FAMILY_REVOKED' AND family_id = 'family-provenance-1' "
                + "AND family_created_slot IS NULL"));
        execute("TRUNCATE TABLE fbs_oauth_receipt");
    }

    private static void assertOAuthProvenanceRollbackRetry() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            connection.setAutoCommit(false);
            statement.execute(provenanceCreatedReceiptInsert(
                    "receipt-provenance-rolled-back", "correlation-provenance-rolled-back",
                    1L, "family-provenance-1"));
            connection.rollback();
        }
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_created_slot = 'family-provenance-1'"));
        execute(provenanceCreatedReceiptInsert(
                "receipt-provenance-retry", "correlation-provenance-retry",
                1L, "family-provenance-1"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_created_slot = 'family-provenance-1'"));
        execute("TRUNCATE TABLE fbs_oauth_receipt");
    }

    private static void assertOAuthProvenanceSameFamilyConcurrency() throws Exception {
        int contenders = 32;
        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < contenders; index++) {
                int contender = index;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(30, TimeUnit.SECONDS);
                    try {
                        execute(provenanceCreatedReceiptInsert(
                                "receipt-provenance-race-" + contender,
                                "correlation-provenance-race-" + contender,
                                1L, "family-provenance-1"));
                        winners.incrementAndGet();
                    } catch (SQLException conflict) {
                        if (conflict.getErrorCode() != 1062
                                && !"23000".equals(conflict.getSQLState())) {
                            throw conflict;
                        }
                        duplicates.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS));
            start.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        assertEquals(1, winners.get());
        assertEquals(contenders - 1, duplicates.get());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE family_created_slot = 'family-provenance-1'"));
        execute("TRUNCATE TABLE fbs_oauth_receipt");
    }

    private static void assertOAuthProvenanceDifferentFamilyConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int ordinal = 2; ordinal <= 9; ordinal++) {
                int familyOrdinal = ordinal;
                futures.add(pool.submit(() -> {
                    execute(provenanceCreatedReceiptInsert(
                            "receipt-provenance-distinct-" + familyOrdinal,
                            "correlation-provenance-distinct-" + familyOrdinal,
                            familyOrdinal, "family-provenance-" + familyOrdinal));
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
        assertEquals(8, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE action = 'TOKEN_FAMILY_CREATED' "
                + "AND family_created_slot BETWEEN 'family-provenance-2' "
                + "AND 'family-provenance-9'"));
        execute("TRUNCATE TABLE fbs_oauth_receipt");
    }

    private static void assertOAuthProvenanceReceiptImmutability() throws Exception {
        execute(provenanceCreatedReceiptInsert(
                "receipt-provenance-immutable", "correlation-provenance-immutable",
                1L, "family-provenance-1"));
        SQLException updateRejected = assertThrows(
                SQLException.class,
                () -> execute("UPDATE fbs_oauth_receipt SET "
                        + "payload_digest = UNHEX(REPEAT('55', 32)) "
                        + "WHERE receipt_id = 'receipt-provenance-immutable'"));
        assertTrue(updateRejected.getMessage().contains("OAuth receipts are immutable"),
                updateRejected.getMessage());
        SQLException deleteRejected = assertThrows(
                SQLException.class,
                () -> execute("DELETE FROM fbs_oauth_receipt "
                        + "WHERE receipt_id = 'receipt-provenance-immutable'"));
        assertTrue(deleteRejected.getMessage().contains("OAuth receipts are immutable"),
                deleteRejected.getMessage());
        execute("TRUNCATE TABLE fbs_oauth_receipt");
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
                String neutralNameUtf8Hex =
                        "e69caae9aa8ce8af81e79a84e69cace59cb0e585ace585b1e5aea2e688b7e7abaf";
                String metadataProjectionName = "CONVERT(0x"
                        + byteProjectionUtf8Hex(neutralNameUtf8Hex) + " USING utf8mb4)";
                assertCheckConstraintRejects(statement, "chk_oauth_client_fixed_profile",
                        invalidOAuthClientInsert("ACTIVE").replace(
                                "CONVERT(0x" + neutralNameUtf8Hex + " USING utf8mb4)",
                                metadataProjectionName));
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

    private static String byteProjectionUtf8Hex(String sourceUtf8Hex) {
        byte[] source = new byte[sourceUtf8Hex.length() / 2];
        for (int index = 0; index < source.length; index++) {
            source[index] = (byte) Integer.parseInt(
                    sourceUtf8Hex.substring(index * 2, index * 2 + 2), 16);
        }
        byte[] projected = new String(source, StandardCharsets.ISO_8859_1)
                .getBytes(StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder(projected.length * 2);
        for (byte value : projected) {
            int unsigned = value & 0xff;
            hex.append(Character.forDigit(unsigned >>> 4, 16));
            hex.append(Character.forDigit(unsigned & 0x0f, 16));
        }
        return hex.toString();
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

    private record OAuthClientFixture(String clientId, String redirectUri) {
    }

    private record OAuthLineage(
            String clientId,
            String redirectUri,
            Long requestId,
            Long codeId,
            Date decisionAt,
            BoardOAuthTokenExchangeCommand command) {
        private OAuthLineage {
            decisionAt = copy(decisionAt);
        }

        @Override
        public Date decisionAt() {
            return copy(decisionAt);
        }
    }

    private record AsyncOAuthAttempt(Object value, RuntimeException failure) {
        static AsyncOAuthAttempt succeeded(Object value) {
            return new AsyncOAuthAttempt(value, null);
        }

        static AsyncOAuthAttempt failed(RuntimeException failure) {
            return new AsyncOAuthAttempt(null, failure);
        }

        boolean succeeded() {
            return value != null && failure == null;
        }
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
        IndependentBoardPortalReadMapper independentBoardPortalReadMapper(
                SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory)
                    .getMapper(IndependentBoardPortalReadMapper.class);
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
                IndependentBoardOAuthMapper oauthMapper,
                IndependentBoardConnectorProperties properties,
                DataSource dataSource) {
            return new IndependentBoardConnectorBindingService(
                    mapper, oauthMapper, properties, dataSource);
        }

        @Bean
        LegacyConnectorBindingTestAdapter legacyConnectorBindingTestAdapter(
                IndependentBoardMapper mapper,
                IndependentBoardConnectorBindingService connectorBindingService) {
            return new LegacyConnectorBindingTestAdapter(mapper, connectorBindingService);
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
