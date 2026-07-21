package com.wx.fbsir.business.board.oauth.service;

import com.wx.fbsir.business.board.config.IndependentBoardConnectorProperties;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.oauth.BoardOAuthCrypto;
import com.wx.fbsir.business.board.oauth.BoardOAuthPrincipalSubject;
import com.wx.fbsir.business.board.oauth.BoardOAuthProfile;
import com.wx.fbsir.business.board.oauth.BoardOAuthProtocolException;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationRequest;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthClientRegistrationResponse;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshCommand;
import com.wx.fbsir.business.board.oauth.dto.BoardOAuthRefreshResult;
import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import com.wx.fbsir.business.board.service.IndependentBoardConnectorBindingService;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
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
 * Explicit real-MySQL proof for refresh rotation, replay containment and rollback.
 *
 * <p>The normal Maven lifecycle returns no dynamic tests unless the same dedicated
 * loopback MySQL contract used by {@code IndependentBoardMysqlTransactionIT} is
 * explicitly supplied. This avoids both environmental skips and synthetic passes.</p>
 */
class IndependentBoardOAuthRefreshSecurityServiceTest {
    private static final String DATABASE = "u3w_independent_board_it";
    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String PLAN_CODE = "BOARD_VIP";
    private static final String SOURCE_CODE = "WORKBUDDY";
    private static final String CONNECTOR_CODE = "fbs-connector";
    private static final String BINDING_ID =
            "11111111-2222-4333-8444-555555555555";
    private static final String FAMILY_ID = "f".repeat(43);
    private static final String RAW_ACCESS = "a".repeat(43);
    private static final String RAW_REFRESH = "s".repeat(43);
    private static final String CREATION_RECEIPT_ID = "k".repeat(43);
    private static final long TENANT_ID = 91_001L;
    private static final long MEMBER_ID = 91_101L;
    private static final long USER_ID = 91_501L;
    private static final long REQUEST_ID = 91_201L;
    private static final long CODE_ID = 91_202L;
    private static final long ACCESS_ID = 91_301L;
    private static final long REFRESH_ID = 91_302L;
    private static final String ROTATION_DELAY_TRIGGER =
            "independent_board_refresh_it_delay_rotation";
    private static final String RECEIPT_FAILURE_TRIGGER =
            "independent_board_refresh_it_fail_receipt";

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static IndependentBoardOAuthRefreshFacade refreshFacade;
    private static IndependentBoardOAuthClientRegistrationService registrationService;
    private static IndependentBoardOAuthRefreshMysqlTestConfiguration.SwitchableClock
            refreshClock;
    private static Fixture fixture;

    @TestFactory
    Stream<DynamicTest> realMysqlRefreshSecurityScenarios() throws Exception {
        if (!explicitMysqlEnvironmentPresent()) {
            return Stream.empty();
        }
        startContextAndAssertCurrentSchema();
        return Stream.of(
                DynamicTest.dynamicTest(
                        "same refresh linearizes to rotation then replay containment",
                        () -> runIsolated(
                                this::sameRefreshLinearizesToRotationThenReplayContainment)),
                DynamicTest.dynamicTest(
                        "late rotation receipt failure rolls the whole transaction back",
                        () -> runIsolated(
                                this::lateRotationReceiptFailureRollsBackEverything)),
                DynamicTest.dynamicTest(
                        "late replay receipt failure rolls W4b W4a tokens and binding back",
                        () -> runIsolated(
                                this::lateReplayReceiptFailureRollsBackEverything)));
    }

    @AfterAll
    static void closeContext() {
        dropTestTriggersQuietly();
        if (context != null) {
            context.close();
        }
        context = null;
        dataSource = null;
        jdbc = null;
        refreshFacade = null;
        registrationService = null;
        refreshClock = null;
        fixture = null;
    }

    private void sameRefreshLinearizesToRotationThenReplayContainment()
            throws Exception {
        createRotationDelayTrigger();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Attempt> first = pool.submit(
                    () -> concurrentAttempt(ready, start));
            Future<Attempt> second = pool.submit(
                    () -> concurrentAttempt(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            awaitMysqlRowLockWait("fbs_enterprise", "PRIMARY", 1, first, second);
            Attempt left = first.get(20, TimeUnit.SECONDS);
            Attempt right = second.get(20, TimeUnit.SECONDS);

            List<Attempt> successes = Stream.of(left, right)
                    .filter(Attempt::succeeded)
                    .toList();
            List<Attempt> failures = Stream.of(left, right)
                    .filter(value -> !value.succeeded())
                    .toList();
            assertEquals(1, successes.size());
            assertEquals(1, failures.size());
            assertNotNull(successes.get(0).result());
            assertFalse(RAW_REFRESH.equals(
                    successes.get(0).result().rawRefreshToken()));
            assertTrue(failures.get(0).failure()
                    instanceof BoardOAuthProtocolException);
            BoardOAuthProtocolException replay =
                    (BoardOAuthProtocolException) failures.get(0).failure();
            assertEquals("invalid_grant", replay.oauthError());
            assertEquals(IndependentBoardOAuthRefreshService.REPLAY_DETECTED,
                    replay.reasonCode());

            Snapshot contained = snapshot();
            assertEquals("COMPROMISED", contained.familyStatus());
            assertEquals(1L, contained.currentGeneration());
            assertEquals(3L, contained.familyVersion());
            assertEquals("REVOKED", contained.bindingStatus());
            assertEquals(4L, contained.bindingVersion());
            assertEquals(0, contained.activeTokens());
            assertEquals(4, contained.totalTokens());
            assertEquals(1, contained.rotationReceipts());
            assertEquals(1, contained.replayReceipts());
            assertEquals(1, contained.bindingRevocationReceipts());

            assertReplayRejected();
            assertReplayRejected();
            assertEquals(contained, snapshot(),
                    "terminal replay retries must not append or mutate anything");
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
            dropTrigger(ROTATION_DELAY_TRIGGER);
        }
    }

    private void lateRotationReceiptFailureRollsBackEverything() throws Exception {
        Snapshot before = snapshot();
        createReceiptFailureTrigger("TOKEN_FAMILY_ROTATED");

        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> refreshFacade.refresh(fixture.command()));

        assertEquals("temporarily_unavailable", failure.oauthError());
        assertEquals(IndependentBoardOAuthRefreshService.PERSISTENCE_UNAVAILABLE,
                failure.reasonCode());
        assertEquals(before, snapshot(),
                "source use, successor inserts and family advance must roll back");
        assertEquals("ACTIVE", tokenStatus(REFRESH_ID));
        assertNull(tokenUsedAt(REFRESH_ID));
    }

    private void lateReplayReceiptFailureRollsBackEverything() throws Exception {
        BoardOAuthRefreshResult rotated = refreshFacade.refresh(fixture.command());
        assertNotNull(rotated.rawRefreshToken());
        Snapshot afterRotation = snapshot();
        assertEquals(1, afterRotation.rotationReceipts());
        assertEquals(0, afterRotation.replayReceipts());
        assertEquals("ACTIVE", afterRotation.bindingStatus());
        assertEquals(3, afterRotation.activeTokens());

        createReceiptFailureTrigger("REFRESH_REPLAY_DETECTED");
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> refreshFacade.refresh(fixture.command()));

        assertEquals("temporarily_unavailable", failure.oauthError());
        assertEquals(IndependentBoardOAuthRefreshService.PERSISTENCE_UNAVAILABLE,
                failure.reasonCode());
        assertEquals(afterRotation, snapshot(),
                "family compromise, token revocation, binding revoke and W4a receipt "
                        + "must roll back with the failed W4b receipt");
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt "
                + "WHERE action = 'CONNECTOR_BINDING_REVOKED'"));
    }

    private static void runIsolated(ThrowingRunnable scenario) throws Exception {
        resetAndSeed();
        try {
            scenario.run();
        } finally {
            dropTestTriggersQuietly();
        }
    }

    private static Attempt concurrentAttempt(
            CountDownLatch ready,
            CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                return Attempt.failed(new AssertionError("concurrency start timed out"));
            }
            return Attempt.succeeded(refreshFacade.refresh(fixture.command()));
        } catch (Throwable failure) {
            return Attempt.failed(failure);
        }
    }

    private static void assertReplayRejected() {
        BoardOAuthProtocolException failure = assertThrows(
                BoardOAuthProtocolException.class,
                () -> refreshFacade.refresh(fixture.command()));
        assertEquals("invalid_grant", failure.oauthError());
        assertEquals(IndependentBoardOAuthRefreshService.REPLAY_DETECTED,
                failure.reasonCode());
    }

    private static synchronized void startContextAndAssertCurrentSchema()
            throws Exception {
        if (context != null) {
            return;
        }
        String url = required("INDEPENDENT_BOARD_MYSQL_IT_URL");
        if (!"true".equals(required("INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP"))) {
            throw new IllegalStateException(
                    "INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP must equal true");
        }
        assertSafeDedicatedUrl(url);
        System.setProperty("independent.board.mysql.it.url", url);
        System.setProperty("independent.board.mysql.it.username",
                required("INDEPENDENT_BOARD_MYSQL_IT_USERNAME"));
        String password = value("INDEPENDENT_BOARD_MYSQL_IT_PASSWORD");
        System.setProperty("independent.board.mysql.it.password",
                password == null ? "" : password);

        context = new AnnotationConfigApplicationContext(
                DatabaseConfiguration.class,
                IndependentBoardOAuthRefreshMysqlTestConfiguration.class);
        dataSource = context.getBean(DataSource.class);
        jdbc = new JdbcTemplate(dataSource);
        refreshFacade = context.getBean(IndependentBoardOAuthRefreshFacade.class);
        registrationService = context.getBean(
                IndependentBoardOAuthClientRegistrationService.class);
        refreshClock = context.getBean(
                IndependentBoardOAuthRefreshMysqlTestConfiguration
                        .SwitchableClock.class);
        assertTrue(AopUtils.isAopProxy(context.getBean(
                IndependentBoardOAuthRefreshMysqlTestConfiguration.RUNNER_BEAN_NAME)));
        assertDedicatedDatabaseAndCurrentRefreshSchema();
    }

    private static void assertDedicatedDatabaseAndCurrentRefreshSchema()
            throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertEquals(DATABASE, connection.getCatalog());
            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("MySQL", metadata.getDatabaseProductName());
            assertEquals(8, metadata.getDatabaseMajorVersion());
            assertTrue(metadata.getDriverName().contains("MySQL Connector/J"));
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                         "SELECT @@transaction_isolation")) {
                assertTrue(row.next());
                assertEquals("REPEATABLE-READ", row.getString(1));
            }
        }
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = "
                + "'20260721_independent_board_oauth_refresh_security_v1' "
                + "AND description = "
                + "'APPLIED:Independent Board OAuth refresh security receipt v2'"),
                "public_init_036 must be applied before this explicit MySQL test");
        assertEquals(8, scalarInt("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'fbs_oauth_receipt' "
                + "AND column_name IN ('receipt_format_version','subject_generation',"
                + "'result_generation','causation_receipt_id','before_state_digest',"
                + "'after_state_digest','subject_token_type','security_event_slot')"));
    }

    private static void resetAndSeed() throws Exception {
        refreshClock.reset();
        dropTestTriggersQuietly();
        execute(
                "TRUNCATE TABLE fbs_oauth_receipt",
                "DELETE FROM fbs_oauth_token",
                "DELETE FROM fbs_oauth_token_family",
                "DELETE FROM fbs_oauth_authorization_code",
                "DELETE FROM fbs_oauth_authorization_request",
                "DELETE FROM fbs_oauth_client",
                "TRUNCATE TABLE fbs_connector_binding_receipt",
                "DELETE FROM fbs_connector_binding_scope",
                "DELETE FROM fbs_connector_binding",
                "DELETE FROM fbs_product_entitlement",
                "DELETE FROM fbs_enterprise_member",
                "DELETE FROM fbs_enterprise");
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_product_plan "
                + "WHERE product_code = '" + PRODUCT_CODE + "' "
                + "AND plan_code = '" + PLAN_CODE + "' AND vip = 1 "
                + "AND connector_required = 1 AND daily_meeting_limit = 5 "
                + "AND agenda_limit = 30 AND seat_limit IS NULL "
                + "AND secretary_enabled = 1 AND status = 'ACTIVE'"));

        jdbc.update("INSERT INTO fbs_enterprise "
                + "(id, enterprise_name, status, del_flag) VALUES (?, ?, 1, '0')",
                TENANT_ID, "Refresh Security Tenant");
        jdbc.update("INSERT INTO fbs_enterprise_member "
                + "(id, enterprise_id, user_id, role, status, del_flag) "
                + "VALUES (?, ?, ?, 'MEMBER', 1, '0')",
                MEMBER_ID, TENANT_ID, USER_ID);

        BoardOAuthClientRegistrationResponse registration =
                registrationService.register(new BoardOAuthClientRegistrationRequest(
                        List.of("http://127.0.0.1:54321/oauth/callback"),
                        "none",
                        List.of("authorization_code", "refresh_token"),
                        List.of("code"),
                        BoardOAuthProfile.REQUIRED_SCOPES,
                        "refresh-mysql-it-metadata".getBytes(StandardCharsets.US_ASCII),
                        "refresh-mysql-it-source".getBytes(StandardCharsets.US_ASCII)));
        String clientId = registration.clientId();
        Instant clientRegisteredAt = jdbc.queryForObject(
                "SELECT registered_at FROM fbs_oauth_client WHERE client_id = ?",
                (row, index) -> row.getTimestamp(1).toInstant(), clientId);
        Thread.sleep(10L);
        Instant lineageAt = clientRegisteredAt.plusMillis(1)
                .truncatedTo(ChronoUnit.MILLIS);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (lineageAt.isAfter(now)) {
            lineageAt = now;
        }
        byte[] principal = BoardOAuthPrincipalSubject.digest(
                TENANT_ID, MEMBER_ID, USER_ID);
        String principalHex = HexFormat.of().formatHex(principal);
        byte[] scopeDigest = BoardOAuthCrypto.sha256Ascii(
                BoardOAuthProfile.CANONICAL_SCOPE);

        jdbc.update("INSERT INTO fbs_product_entitlement "
                        + "(enterprise_id, member_id, user_id, product_code, plan_code, "
                        + "status, connector_binding_id, connector_verified_at, "
                        + "valid_from, valid_until, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, 2, ?, ?)",
                TENANT_ID, MEMBER_ID, USER_ID, PRODUCT_CODE, PLAN_CODE,
                BINDING_ID, timestamp(lineageAt),
                timestamp(lineageAt.minusSeconds(60)),
                timestamp(lineageAt.plus(30, ChronoUnit.DAYS)),
                timestamp(lineageAt), timestamp(lineageAt));
        jdbc.update("INSERT INTO fbs_connector_binding "
                        + "(binding_id, enterprise_id, member_id, user_id, product_code, "
                        + "source_code, connector_code, issuer_uri, resource_uri, client_id, "
                        + "principal_subject_digest, status, verification_method, "
                        + "evidence_digest, verified_at, last_seen_at, valid_until, "
                        + "revoked_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', "
                        + "'MCP_INITIALIZE', ?, ?, ?, ?, NULL, 3, ?, ?)",
                BINDING_ID, TENANT_ID, MEMBER_ID, USER_ID, PRODUCT_CODE,
                SOURCE_CODE, CONNECTOR_CODE, BoardOAuthProfile.ISSUER,
                BoardOAuthProfile.RESOURCE, clientId, principalHex,
                sha256Hex("binding-evidence"),
                timestamp(lineageAt), timestamp(lineageAt),
                timestamp(lineageAt.plus(30, ChronoUnit.DAYS)),
                timestamp(lineageAt), timestamp(lineageAt));
        for (String scope : BoardOAuthProfile.REQUIRED_SCOPES) {
            jdbc.update("INSERT INTO fbs_connector_binding_scope "
                            + "(binding_id, scope_code, created_at) VALUES (?, ?, ?)",
                    BINDING_ID, scope, timestamp(lineageAt));
        }
        jdbc.update("INSERT INTO fbs_connector_binding_receipt "
                        + "(id, receipt_id, binding_id, enterprise_id, member_id, user_id, "
                        + "actor_user_id, action, payload_digest, evidence_level, created_at) "
                        + "VALUES (100, ?, ?, ?, ?, ?, ?, 'CONNECTOR_BINDING_VERIFIED', "
                        + "?, 'ACTION_COMPLETED', ?)",
                "22222222-3333-4444-8555-666666666666", BINDING_ID,
                TENANT_ID, MEMBER_ID, USER_ID, USER_ID,
                sha256Hex("binding-verified-receipt"), timestamp(lineageAt));

        Instant requestedAt = lineageAt;
        Instant requestExpiresAt = requestedAt.plusSeconds(300);
        Instant approvedAt = lineageAt.plusMillis(1);
        Instant consumedAt = lineageAt.plusMillis(2);
        Instant codeIssuedAt = consumedAt;
        Instant codeExpiresAt = codeIssuedAt.plusSeconds(60);
        Instant codeUsedAt = codeIssuedAt.plusMillis(1);
        Instant familyIssuedAt = codeUsedAt;
        Instant familyActivatedAt = familyIssuedAt.plusMillis(1);
        Instant familyExpiresAt = familyIssuedAt.plus(29, ChronoUnit.DAYS);
        String redirect = registration.redirectUris().get(0);
        String challenge = "v".repeat(43);

        jdbc.update("INSERT INTO fbs_oauth_authorization_request "
                        + "(id, request_handle_digest, client_id, redirect_uri, "
                        + "code_challenge, code_challenge_method, state_digest, "
                        + "state_key_ref, state_nonce, state_ciphertext, issuer_uri, "
                        + "resource_uri, product_code, source_code, connector_code, "
                        + "scope_canonical, scope_digest, principal_subject_digest, "
                        + "consent_intent, enterprise_id, member_id, user_id, status, "
                        + "requested_at, expires_at, approved_at, denied_at, consumed_at, "
                        + "version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'S256', ?, NULL, NULL, NULL, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, 'FIRST_CONNECT', ?, ?, ?, 'CONSUMED', ?, ?, ?, NULL, ?, "
                        + "2, ?, ?)",
                REQUEST_ID, BoardOAuthCrypto.sha256Ascii("request-handle"),
                clientId, redirect, challenge,
                BoardOAuthCrypto.sha256Ascii("request-state"),
                BoardOAuthProfile.ISSUER, BoardOAuthProfile.RESOURCE,
                PRODUCT_CODE, SOURCE_CODE, CONNECTOR_CODE,
                BoardOAuthProfile.CANONICAL_SCOPE, scopeDigest, principal,
                TENANT_ID, MEMBER_ID, USER_ID,
                timestamp(requestedAt), timestamp(requestExpiresAt),
                timestamp(approvedAt), timestamp(consumedAt),
                timestamp(requestedAt), timestamp(consumedAt));
        jdbc.update("INSERT INTO fbs_oauth_authorization_code "
                        + "(id, code_digest, authorization_request_id, client_id, "
                        + "redirect_uri, code_challenge, code_challenge_method, issuer_uri, "
                        + "resource_uri, product_code, source_code, connector_code, "
                        + "scope_canonical, scope_digest, principal_subject_digest, "
                        + "consent_intent, enterprise_id, member_id, user_id, status, "
                        + "issued_at, expires_at, used_at, revoked_at, version, "
                        + "created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, 'S256', ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'FIRST_CONNECT', ?, ?, ?, 'USED', ?, ?, ?, NULL, 1, ?, ?)",
                CODE_ID, BoardOAuthCrypto.sha256Ascii("authorization-code"),
                REQUEST_ID, clientId, redirect, challenge,
                BoardOAuthProfile.ISSUER, BoardOAuthProfile.RESOURCE,
                PRODUCT_CODE, SOURCE_CODE, CONNECTOR_CODE,
                BoardOAuthProfile.CANONICAL_SCOPE, scopeDigest, principal,
                TENANT_ID, MEMBER_ID, USER_ID,
                timestamp(codeIssuedAt), timestamp(codeExpiresAt),
                timestamp(codeUsedAt), timestamp(codeIssuedAt),
                timestamp(codeUsedAt));
        jdbc.update("INSERT INTO fbs_oauth_token_family "
                        + "(id, family_id, origin_authorization_code_id, client_id, "
                        + "enterprise_id, member_id, user_id, product_code, source_code, "
                        + "connector_code, issuer_uri, resource_uri, scope_canonical, "
                        + "scope_digest, principal_subject_digest, consent_intent, binding_id, "
                        + "binding_version, status, current_refresh_generation, issued_at, "
                        + "activated_at, expires_at, terminated_at, version, created_at, updated_at) "
                        + "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'FIRST_CONNECT', ?, 3, 'ACTIVE', 0, ?, ?, ?, NULL, 1, ?, ?)",
                FAMILY_ID, CODE_ID, clientId,
                TENANT_ID, MEMBER_ID, USER_ID, PRODUCT_CODE, SOURCE_CODE,
                CONNECTOR_CODE, BoardOAuthProfile.ISSUER, BoardOAuthProfile.RESOURCE,
                BoardOAuthProfile.CANONICAL_SCOPE, scopeDigest, principal,
                BINDING_ID, timestamp(familyIssuedAt), timestamp(familyActivatedAt),
                timestamp(familyExpiresAt), timestamp(familyIssuedAt),
                timestamp(familyActivatedAt));
        jdbc.update("INSERT INTO fbs_oauth_token "
                        + "(id, token_digest, family_id, token_type, generation, resource_uri, "
                        + "scope_canonical, scope_digest, status, issued_at, used_at, "
                        + "revoked_at, expires_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'ACCESS', 0, ?, ?, ?, 'ACTIVE', ?, NULL, NULL, "
                        + "?, 0, ?, ?)",
                ACCESS_ID, BoardOAuthCrypto.sha256Ascii(RAW_ACCESS), FAMILY_ID,
                BoardOAuthProfile.RESOURCE, BoardOAuthProfile.CANONICAL_SCOPE,
                scopeDigest, timestamp(familyIssuedAt),
                timestamp(familyIssuedAt.plusSeconds(600)),
                timestamp(familyIssuedAt), timestamp(familyIssuedAt));
        jdbc.update("INSERT INTO fbs_oauth_token "
                        + "(id, token_digest, family_id, token_type, generation, resource_uri, "
                        + "scope_canonical, scope_digest, status, issued_at, used_at, "
                        + "revoked_at, expires_at, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, 'REFRESH', 0, ?, ?, ?, 'ACTIVE', ?, NULL, NULL, "
                        + "?, 0, ?, ?)",
                REFRESH_ID, BoardOAuthCrypto.sha256Ascii(RAW_REFRESH), FAMILY_ID,
                BoardOAuthProfile.RESOURCE, BoardOAuthProfile.CANONICAL_SCOPE,
                scopeDigest, timestamp(familyIssuedAt), timestamp(familyExpiresAt),
                timestamp(familyIssuedAt), timestamp(familyIssuedAt));
        jdbc.update("INSERT INTO fbs_oauth_receipt "
                        + "(id, receipt_id, action, client_id, authorization_request_id, "
                        + "authorization_code_id, family_id, token_id, binding_id, enterprise_id, "
                        + "member_id, user_id, principal_subject_digest, actor_type, actor_user_id, "
                        + "actor_subject_digest, correlation_id, payload_digest, evidence_level, "
                        + "created_at) "
                        + "VALUES (100, ?, 'TOKEN_FAMILY_CREATED', ?, NULL, ?, ?, NULL, NULL, "
                        + "?, ?, ?, ?, 'CLIENT', NULL, ?, ?, ?, 'ACTION_COMPLETED', ?)",
                CREATION_RECEIPT_ID, clientId, CODE_ID, FAMILY_ID,
                TENANT_ID, MEMBER_ID, USER_ID, principal,
                BoardOAuthCrypto.sha256Ascii(clientId), "g".repeat(43),
                BoardOAuthCrypto.sha256Ascii("token-family-created"),
                timestamp(familyIssuedAt));

        fixture = new Fixture(clientId, new BoardOAuthRefreshCommand(
                RAW_REFRESH, clientId, BoardOAuthProfile.RESOURCE, null));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_oauth_token "
                + "WHERE family_id = '" + FAMILY_ID + "'"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt "
                + "WHERE action = 'TOKEN_FAMILY_CREATED'"));
    }

    private static void createRotationDelayTrigger() throws SQLException {
        execute("CREATE TRIGGER " + ROTATION_DELAY_TRIGGER + " "
                + "BEFORE UPDATE ON fbs_oauth_token FOR EACH ROW "
                + "BEGIN "
                + "IF OLD.token_type = 'REFRESH' AND OLD.status = 'ACTIVE' "
                + "AND NEW.status = 'USED' THEN DO SLEEP(2); END IF; "
                + "END");
    }

    private static void createReceiptFailureTrigger(String action) throws SQLException {
        execute("CREATE TRIGGER " + RECEIPT_FAILURE_TRIGGER + " "
                + "BEFORE INSERT ON fbs_oauth_receipt FOR EACH ROW "
                + "BEGIN IF NEW.action = '" + action + "' THEN "
                + "SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT = 'refresh mysql it injected receipt failure'; "
                + "END IF; END");
    }

    private static void awaitMysqlRowLockWait(
            String tableName,
            String indexName,
            int minimumWaiters,
            Future<Attempt> first,
            Future<Attempt> second) throws Exception {
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
            if (first.isDone() && second.isDone()) {
                throw new AssertionError(
                        "both refresh attempts completed before a real MySQL row wait");
            }
            Thread.sleep(25L);
        }
        throw new AssertionError("expected a MySQL row-lock waiter on "
                + tableName + "." + indexName);
    }

    private static Snapshot snapshot() {
        return new Snapshot(
                scalarString("SELECT status FROM fbs_oauth_token_family "
                        + "WHERE family_id = '" + FAMILY_ID + "'"),
                scalarLong("SELECT current_refresh_generation "
                        + "FROM fbs_oauth_token_family WHERE family_id = '"
                        + FAMILY_ID + "'"),
                scalarLong("SELECT version FROM fbs_oauth_token_family "
                        + "WHERE family_id = '" + FAMILY_ID + "'"),
                scalarString("SELECT status FROM fbs_connector_binding "
                        + "WHERE binding_id = '" + BINDING_ID + "'"),
                scalarLong("SELECT version FROM fbs_connector_binding "
                        + "WHERE binding_id = '" + BINDING_ID + "'"),
                scalarInt("SELECT COUNT(*) FROM fbs_oauth_token WHERE family_id = '"
                        + FAMILY_ID + "' AND status = 'ACTIVE'"),
                scalarInt("SELECT COUNT(*) FROM fbs_oauth_token WHERE family_id = '"
                        + FAMILY_ID + "'"),
                scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt WHERE family_id = '"
                        + FAMILY_ID + "' AND action = 'TOKEN_FAMILY_ROTATED'"),
                scalarInt("SELECT COUNT(*) FROM fbs_oauth_receipt WHERE family_id = '"
                        + FAMILY_ID + "' AND action = 'REFRESH_REPLAY_DETECTED'"),
                scalarInt("SELECT COUNT(*) FROM fbs_connector_binding_receipt "
                        + "WHERE binding_id = '" + BINDING_ID + "' "
                        + "AND action = 'CONNECTOR_BINDING_REVOKED'"));
    }

    private static String tokenStatus(long id) {
        return jdbc.queryForObject(
                "SELECT status FROM fbs_oauth_token WHERE id = ?",
                String.class, id);
    }

    private static Timestamp tokenUsedAt(long id) {
        return jdbc.queryForObject(
                "SELECT used_at FROM fbs_oauth_token WHERE id = ?",
                (row, index) -> row.getTimestamp(1), id);
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private static int scalarInt(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }

    private static long scalarLong(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        if (value == null) {
            throw new AssertionError("expected one non-null long for: " + sql);
        }
        return value;
    }

    private static String scalarString(String sql) {
        String value = jdbc.queryForObject(sql, String.class);
        if (value == null) {
            throw new AssertionError("expected one non-null string for: " + sql);
        }
        return value;
    }

    private static void dropTestTriggersQuietly() {
        if (dataSource == null) {
            return;
        }
        try {
            dropTrigger(ROTATION_DELAY_TRIGGER);
            dropTrigger(RECEIPT_FAILURE_TRIGGER);
        } catch (SQLException ignored) {
            // The primary test failure remains the authoritative signal.
        }
    }

    private static void dropTrigger(String name) throws SQLException {
        execute("DROP TRIGGER IF EXISTS " + name);
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static String sha256Hex(String value) {
        return HexFormat.of().formatHex(BoardOAuthCrypto.sha256Ascii(value));
    }

    private static boolean explicitMysqlEnvironmentPresent() {
        String url = value("INDEPENDENT_BOARD_MYSQL_IT_URL");
        if (url == null || url.isBlank()) {
            return false;
        }
        required("INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP");
        required("INDEPENDENT_BOARD_MYSQL_IT_USERNAME");
        return true;
    }

    private static void assertSafeDedicatedUrl(String url) {
        if (!url.matches("^jdbc:mysql://127\\.0\\.0\\.1:(?!3306(?:/|$))\\d+/"
                + DATABASE + "(?:\\?.*)?$")
                || !hasExactlyOneParameter(url, "useAffectedRows", "false")) {
            throw new IllegalStateException(
                    "MySQL IT URL must target the dedicated 127.0.0.1 database "
                            + "on a non-3306 port with useAffectedRows=false");
        }
    }

    private static boolean hasExactlyOneParameter(
            String url,
            String key,
            String expectedValue) {
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
            throw new IllegalStateException(name
                    + " is required for the explicit refresh MySQL IT");
        }
        return result;
    }

    private static String value(String name) {
        String system = System.getProperty(name);
        return system != null ? system : System.getenv(name);
    }

    private record Fixture(String clientId, BoardOAuthRefreshCommand command) {
    }

    private record Attempt(
            BoardOAuthRefreshResult result,
            Throwable failure) {
        private static Attempt succeeded(BoardOAuthRefreshResult result) {
            return new Attempt(result, null);
        }

        private static Attempt failed(Throwable failure) {
            return new Attempt(null, failure);
        }

        private boolean succeeded() {
            return result != null && failure == null;
        }
    }

    private record Snapshot(
            String familyStatus,
            long currentGeneration,
            long familyVersion,
            String bindingStatus,
            long bindingVersion,
            int activeTokens,
            int totalTokens,
            int rotationReceipts,
            int replayReceipts,
            int bindingRevocationReceipts) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class DatabaseConfiguration {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(System.getProperty("independent.board.mysql.it.url"));
            source.setUsername(System.getProperty(
                    "independent.board.mysql.it.username"));
            source.setPassword(System.getProperty(
                    "independent.board.mysql.it.password"));
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
        IndependentBoardMapper independentBoardMapper(
                SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory)
                    .getMapper(IndependentBoardMapper.class);
        }

        @Bean
        IndependentBoardOAuthMapper independentBoardOAuthMapper(
                SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory)
                    .getMapper(IndependentBoardOAuthMapper.class);
        }

        @Bean
        IndependentBoardOAuthClientRegistrationService registrationService(
                IndependentBoardOAuthMapper mapper) {
            return new IndependentBoardOAuthClientRegistrationService(mapper);
        }

        @Bean
        IndependentBoardConnectorProperties connectorProperties() {
            IndependentBoardConnectorProperties properties =
                    new IndependentBoardConnectorProperties();
            properties.setIssuerUri(BoardOAuthProfile.ISSUER);
            properties.setResourceUri(BoardOAuthProfile.RESOURCE);
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
    }
}
