package com.wx.fbsir.business.board.plan.integration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.wx.fbsir.business.board.domain.BoardUsageOperationPolicyReceipt;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicyReceipt;
import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionView;
import com.wx.fbsir.business.board.plan.mapper.IndependentBoardPlanPolicyMapper;
import com.wx.fbsir.business.board.plan.service.IndependentBoardPlanPolicyService;
import com.wx.fbsir.business.board.plan.service.IndependentBoardPlanPolicyTransactionService;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real MySQL concurrency matrix for the W3h versioned plan-policy transition.
 *
 * <p>The normal lifecycle does not discover {@code *IT}. The dedicated PowerShell runner starts
 * an exact disposable MySQL version, applies the current migrations, and supplies the guarded
 * loopback JDBC URL. Each scenario uses a real Spring transaction proxy, MyBatis mapper XML and
 * a bounded Hikari connection pool.</p>
 */
@TestMethodOrder(MethodOrderer.MethodName.class)
class IndependentBoardPlanPolicyMysqlConcurrencyIT {
    private static final int ITERATIONS = 20;
    private static final int FUTURE_TIMEOUT_SECONDS = 10;
    private static final int NON_BLOCKING_TIMEOUT_MILLIS = 1_500;
    private static final String PRODUCT_CODE = "FBSIR_INDEPENDENT_BOARD";
    private static final String FREE_PLAN = "BOARD_FREE";
    private static final String VIP_PLAN = "BOARD_VIP";

    private static AnnotationConfigApplicationContext context;
    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;
    private static IndependentBoardPlanPolicyService service;
    private static IndependentBoardPlanPolicyTransactionService transactionService;
    private static FaultInjectingPlanPolicyMapper planPolicyMapper;
    private static IndependentBoardMapper boardMapper;

    @BeforeAll
    static void startRealSpringMyBatisBoundary() {
        String url = requiredProperty("independent.board.plan.policy.mysql.it.url");
        String expectedVersion = requiredProperty(
                "independent.board.plan.policy.mysql.it.version");
        if (!"true".equals(requiredProperty(
                "independent.board.plan.policy.mysql.it.allowDrop"))) {
            throw new IllegalStateException(
                    "independent.board.plan.policy.mysql.it.allowDrop must equal true");
        }
        assertSafeDedicatedUrl(url);

        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        dataSource = context.getBean(HikariDataSource.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(IndependentBoardPlanPolicyService.class);
        transactionService = context.getBean(IndependentBoardPlanPolicyTransactionService.class);
        planPolicyMapper = context.getBean(FaultInjectingPlanPolicyMapper.class);
        boardMapper = context.getBean(IndependentBoardMapper.class);

        assertTrue(AopUtils.isAopProxy(transactionService),
                "transaction service must be a real Spring AOP proxy");
        assertEquals(8, dataSource.getMaximumPoolSize());
        assertEquals("w3h-plan-policy-concurrency-it", dataSource.getPoolName());
        assertEquals(expectedVersion, jdbc.queryForObject("SELECT VERSION()", String.class));
        assertEquals("w3h_policy_concurrency",
                jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals("REPEATABLE-READ",
                jdbc.queryForObject("SELECT @@SESSION.transaction_isolation", String.class));
        assertEquals(2,
                jdbc.queryForObject("SELECT @@SESSION.innodb_lock_wait_timeout", Integer.class));
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                        + "WHERE version='20260722_independent_board_plan_policy_v1'"));
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM fbs_plan_policy_head"));
        assertEquals(2L, scalarLong(
                "SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt"));
    }

    @AfterEach
    void clearTestFaults() {
        planPolicyMapper.clearHooks();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
        System.out.println(
                "W3H_PLAN_POLICY_CONCURRENCY_EVIDENCE iterationsPerCase=" + ITERATIONS
                        + " springProxy=true myBatis=true hikari=true lockWaitSeconds=2");
    }

    @Test
    void sameActorSameKeySamePayloadReturnsSameReceipt() throws Exception {
        long actor = 71_001L;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            BoardPlanPolicySnapshot before = current(VIP_PLAN);
            long receiptCount = receiptCount(VIP_PLAN);
            BoardPlanPolicyRevisionRequest request = request(
                    before,
                    "VIP same-key replay " + iteration,
                    key("same-payload", iteration));

            List<Attempt> attempts = race(request, actor, request, actor);

            assertTrue(attempts.get(0).succeeded());
            assertTrue(attempts.get(1).succeeded());
            assertEquals(attempts.get(0).view().receiptId(),
                    attempts.get(1).view().receiptId());
            assertEquals(attempts.get(0).view().policyVersion(),
                    attempts.get(1).view().policyVersion());
            assertEquals(before.getPolicyVersion() + 1,
                    attempts.get(0).view().policyVersion());
            assertEquals(receiptCount + 1, receiptCount(VIP_PLAN));
            assertEquals(attempts.get(0).view().receiptId(), current(VIP_PLAN).getReceiptId());
            assertDenseReceiptChain(VIP_PLAN);
        }
    }

    @Test
    void sameActorSameKeyDifferentPayloadHasOneWinnerAndOneIdempotencyConflict()
            throws Exception {
        long actor = 72_001L;
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            BoardPlanPolicySnapshot before = current(VIP_PLAN);
            long receiptCount = receiptCount(VIP_PLAN);
            String idempotencyKey = key("different-payload", iteration);
            BoardPlanPolicyRevisionRequest first = request(
                    before, "VIP conflicting payload A " + iteration, idempotencyKey);
            BoardPlanPolicyRevisionRequest second = request(
                    before, "VIP conflicting payload B " + iteration, idempotencyKey);

            List<Attempt> attempts = race(first, actor, second, actor);

            Attempt winner = onlySuccess(attempts);
            Attempt loser = onlyFailure(attempts);
            assertEquals("BOARD_PLAN_POLICY_IDEMPOTENCY_CONFLICT", loser.error());
            assertEquals(409, loser.status());
            assertEquals(before.getPolicyVersion() + 1, winner.view().policyVersion());
            assertEquals(receiptCount + 1, receiptCount(VIP_PLAN));
            assertEquals(winner.view().receiptId(), current(VIP_PLAN).getReceiptId());
            assertDenseReceiptChain(VIP_PLAN);
        }
    }

    @Test
    void differentKeysSameExpectedVersionHaveOneWinnerAndNoOrphan() throws Exception {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            BoardPlanPolicySnapshot before = current(VIP_PLAN);
            long receiptCount = receiptCount(VIP_PLAN);
            BoardPlanPolicyRevisionRequest first = request(
                    before,
                    "VIP expected-version contender A " + iteration,
                    key("expected-a", iteration));
            BoardPlanPolicyRevisionRequest second = request(
                    before,
                    "VIP expected-version contender B " + iteration,
                    key("expected-b", iteration));

            List<Attempt> attempts = race(first, 73_001L, second, 73_002L);

            Attempt winner = onlySuccess(attempts);
            Attempt loser = onlyFailure(attempts);
            assertEquals("BOARD_PLAN_POLICY_VERSION_CONFLICT", loser.error());
            assertEquals(409, loser.status());
            assertEquals(before.getPolicyVersion() + 1, winner.view().policyVersion());
            assertEquals(receiptCount + 1, receiptCount(VIP_PLAN));
            BoardPlanPolicySnapshot head = current(VIP_PLAN);
            assertEquals(winner.view().receiptId(), head.getReceiptId());
            assertEquals(winner.view().policyVersion(), head.getPolicyVersion());
            assertDenseReceiptChain(VIP_PLAN);
        }
    }

    @Test
    void insertThenForcedCasFailurePhysicallyRollsBack() {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            BoardPlanPolicySnapshot before = current(VIP_PLAN);
            long receiptCount = receiptCount(VIP_PLAN);
            BoardPlanPolicyRevisionRequest request = request(
                    before,
                    "VIP forced CAS rollback " + iteration,
                    key("forced-cas", iteration));
            planPolicyMapper.failNextHeadCasAfterRealInsert();

            ServiceException failure = null;
            try {
                service.revise(request, 74_001L);
            } catch (ServiceException expected) {
                failure = expected;
            }

            assertNotNull(failure);
            assertEquals("BOARD_PLAN_POLICY_VERSION_CONFLICT", failure.getMessage());
            assertEquals(409, failure.getCode());
            String insertedReceiptId = planPolicyMapper.consumeInsertedBeforeFailedCas();
            assertNotNull(insertedReceiptId,
                    "the real INSERT must occur before the injected zero-row CAS");
            assertFalse(planPolicyMapper.isCasFailureArmed());
            assertEquals(0L, scalarLong(
                    "SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt WHERE receipt_id=?",
                    insertedReceiptId));
            assertEquals(receiptCount, receiptCount(VIP_PLAN));
            assertPolicyStateEquals(before, current(VIP_PLAN));
            assertDenseReceiptChain(VIP_PLAN);
        }
    }

    @Test
    void freeAndVipConcurrentRevisionsDoNotDeadlockAndPreserveInvariant()
            throws Exception {
        for (int iteration = 0; iteration < ITERATIONS; iteration++) {
            BoardPlanPolicySnapshot freeBefore = current(FREE_PLAN);
            BoardPlanPolicySnapshot vipBefore = current(VIP_PLAN);
            BoardPlanPolicyRevisionRequest free = request(
                    freeBefore,
                    "FREE concurrent catalog " + iteration,
                    key("free-catalog", iteration));
            BoardPlanPolicyRevisionRequest vip = request(
                    vipBefore,
                    "VIP concurrent catalog " + iteration,
                    key("vip-catalog", iteration));

            List<Attempt> attempts = race(free, 75_001L, vip, 75_002L);

            assertTrue(attempts.get(0).succeeded(), attempts.get(0).error());
            assertTrue(attempts.get(1).succeeded(), attempts.get(1).error());
            assertEquals(freeBefore.getPolicyVersion() + 1,
                    current(FREE_PLAN).getPolicyVersion());
            assertEquals(vipBefore.getPolicyVersion() + 1,
                    current(VIP_PLAN).getPolicyVersion());
            assertCatalogInvariant();
            assertDenseReceiptChain(FREE_PLAN);
            assertDenseReceiptChain(VIP_PLAN);
        }
    }

    @Test
    void meetingReadsAreAllOldOrAllNewAndIgnoreImmutableReceiptLocks()
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int iteration = 0; iteration < ITERATIONS; iteration++) {
                BoardPlanPolicySnapshot before = boardMapper.selectCurrentPlanPolicy(
                        PRODUCT_CODE, FREE_PLAN);
                PauseGate pause = planPolicyMapper.pauseAfterNextSuccessfulHeadUpdate();
                BoardPlanPolicyRevisionRequest request = request(
                        before,
                        "FREE snapshot boundary " + iteration,
                        key("snapshot-boundary", iteration));
                Future<BoardPlanPolicyRevisionView> revision = pool.submit(
                        () -> service.revise(request, 76_001L));
                assertTrue(pause.headUpdated().await(
                        FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                        "revision did not reach its uncommitted head update");

                try {
                    BoardPlanPolicySnapshot during;
                    try {
                        Future<BoardPlanPolicySnapshot> read = pool.submit(
                                () -> boardMapper.selectCurrentPlanPolicy(
                                        PRODUCT_CODE, FREE_PLAN));
                        during = read.get(
                                NON_BLOCKING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException blocked) {
                        fail("meeting current-policy read waited on the uncommitted head", blocked);
                        return;
                    }
                    assertPolicyStateEquals(before, during);
                    assertFreshLineageInsertIgnoresRevisionLocks(pool, iteration, before);
                } finally {
                    pause.release().countDown();
                }

                BoardPlanPolicyRevisionView committed = revision.get(
                        FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                BoardPlanPolicySnapshot after = boardMapper.selectCurrentPlanPolicy(
                        PRODUCT_CODE, FREE_PLAN);
                assertNotEquals(before.getReceiptId(), after.getReceiptId());
                assertEquals(committed.receiptId(), after.getReceiptId());
                assertEquals(committed.policyVersion(), after.getPolicyVersion());
                assertEquals(request.planName(), after.getPlanName());
                assertEquals(request.dailyMeetingLimit(), after.getDailyMeetingLimit());
                assertEquals(request.agendaLimit(), after.getAgendaLimit());
                assertEquals(request.seatLimit(), after.getSeatLimit());
                assertEquals(request.secretaryEnabled(), after.getSecretaryEnabled());
                planPolicyMapper.clearPauseGate();

                assertHistoricalLineageReadIgnoresReceiptLock(pool, iteration, after);
            }
        } finally {
            planPolicyMapper.releasePauseGate();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    private static void assertFreshLineageInsertIgnoresRevisionLocks(
            ExecutorService pool,
            int iteration,
            BoardPlanPolicySnapshot policy) throws Exception {
        long tenantId = 87_000L + iteration;
        String operationId = "w3h-live-lineage-" + iteration + "-" + UUID.randomUUID();
        jdbc.update(
                "INSERT INTO fbs_usage_operation "
                        + "(operation_id,request_digest,enterprise_id,member_id,user_id,"
                        + "product_code,metric_code,bucket_date,units,status,effective_plan_code,"
                        + "agenda_count,seat_count,remaining_count,completed_at) "
                        + "VALUES (?,SHA2(?,256),?,?,?,?,'MEETING',CURRENT_DATE,1,'COMMITTED',"
                        + "?,1,1,0,CURRENT_TIMESTAMP(3))",
                operationId, operationId, tenantId, tenantId + 1000, tenantId + 2000,
                PRODUCT_CODE, policy.getPlanCode());

        Future<Integer> insert = pool.submit(() -> jdbc.update(
                "INSERT INTO fbs_usage_operation_policy_receipt "
                        + "(enterprise_id,operation_id,product_code,plan_code,policy_receipt_id,"
                        + "policy_version,policy_digest) VALUES (?,?,?,?,?,?,?)",
                tenantId, operationId, PRODUCT_CODE, policy.getPlanCode(),
                policy.getReceiptId(), policy.getPolicyVersion(), policy.getPolicyDigest()));
        int inserted;
        try {
            inserted = insert.get(NON_BLOCKING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException blocked) {
            fail("fresh meeting lineage insert waited on an immutable policy receipt", blocked);
            return;
        }
        assertEquals(1, inserted);
        BoardUsageOperationPolicyReceipt lineage =
                boardMapper.selectOperationPolicyReceipt(tenantId, operationId);
        assertNotNull(lineage);
        assertEquals(operationId, lineage.getOperationId());
        assertEquals(policy.getReceiptId(), lineage.getPolicyReceiptId());
    }

    private static void assertHistoricalLineageReadIgnoresReceiptLock(
            ExecutorService pool,
            int iteration,
            BoardPlanPolicySnapshot policy) throws Exception {
        long tenantId = 88_000L + iteration;
        String operationId = "w3h-receipt-lock-" + iteration + "-"
                + UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO fbs_usage_operation "
                        + "(operation_id,request_digest,enterprise_id,member_id,user_id,"
                        + "product_code,metric_code,bucket_date,units,status,effective_plan_code,"
                        + "agenda_count,seat_count,remaining_count,completed_at) "
                        + "VALUES (?,SHA2(?,256),?,?,?,?,'MEETING',CURRENT_DATE,1,'COMMITTED',"
                        + "?,1,1,0,CURRENT_TIMESTAMP(3))",
                operationId, operationId, tenantId, tenantId + 1000, tenantId + 2000,
                PRODUCT_CODE, policy.getPlanCode());
        jdbc.update(
                "INSERT INTO fbs_usage_operation_policy_receipt "
                        + "(enterprise_id,operation_id,product_code,plan_code,policy_receipt_id,"
                        + "policy_version,policy_digest) VALUES (?,?,?,?,?,?,?)",
                tenantId, operationId, PRODUCT_CODE, policy.getPlanCode(),
                policy.getReceiptId(), policy.getPolicyVersion(), policy.getPolicyDigest());

        try (Connection lock = dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try (PreparedStatement statement = lock.prepareStatement(
                    "SELECT receipt_id FROM fbs_plan_policy_revision_receipt "
                            + "WHERE product_code=? AND receipt_id=? FOR UPDATE")) {
                statement.setString(1, PRODUCT_CODE);
                statement.setString(2, policy.getReceiptId());
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(policy.getReceiptId(), result.getString(1));
                }
            }

            Future<BoardUsageOperationPolicyReceipt> replay = pool.submit(
                    () -> boardMapper.selectOperationPolicyReceipt(tenantId, operationId));
            BoardUsageOperationPolicyReceipt lineage;
            try {
                lineage = replay.get(NON_BLOCKING_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException blocked) {
                fail("historical replay waited on a shared immutable receipt lock", blocked);
                return;
            } finally {
                lock.rollback();
            }
            assertNotNull(lineage);
            assertEquals(operationId, lineage.getOperationId());
            assertEquals(policy.getReceiptId(), lineage.getPolicyReceiptId());
            assertEquals(policy.getPolicyVersion(), lineage.getPolicyVersion());
            assertEquals(policy.getPolicyDigest(), lineage.getPolicyDigest());
        }
    }

    private static List<Attempt> race(
            BoardPlanPolicyRevisionRequest first,
            long firstActor,
            BoardPlanPolicyRevisionRequest second,
            long secondActor) throws Exception {
        planPolicyMapper.alignNextCatalogLockPair();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Attempt> firstFuture = pool.submit(
                    () -> attempt(first, firstActor, ready, start));
            Future<Attempt> secondFuture = pool.submit(
                    () -> attempt(second, secondActor, ready, start));
            assertTrue(ready.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    firstFuture.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    secondFuture.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            planPolicyMapper.clearCatalogBarrier();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
    }

    private static Attempt attempt(
            BoardPlanPolicyRevisionRequest request,
            long actor,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrency start barrier timed out");
        }
        try {
            return Attempt.succeeded(service.revise(request, actor));
        } catch (ServiceException failure) {
            return Attempt.failed(failure);
        }
    }

    private static Attempt onlySuccess(List<Attempt> attempts) {
        List<Attempt> winners = attempts.stream().filter(Attempt::succeeded).toList();
        assertEquals(1, winners.size(), attempts.toString());
        return winners.get(0);
    }

    private static Attempt onlyFailure(List<Attempt> attempts) {
        List<Attempt> failures = attempts.stream().filter(value -> !value.succeeded()).toList();
        assertEquals(1, failures.size(), attempts.toString());
        return failures.get(0);
    }

    private static BoardPlanPolicyRevisionRequest request(
            BoardPlanPolicyReceipt current,
            String name,
            String idempotencyKey) {
        return new BoardPlanPolicyRevisionRequest(
                current.getPlanCode(),
                current.getPolicyVersion(),
                name,
                current.getDailyMeetingLimit(),
                current.getAgendaLimit(),
                current.getSeatLimit(),
                current.getSecretaryEnabled(),
                null,
                idempotencyKey);
    }

    private static String key(String scenario, int iteration) {
        return "w3h-" + scenario + "-" + iteration + "-20260722";
    }

    private static BoardPlanPolicySnapshot current(String planCode) {
        BoardPlanPolicySnapshot value = planPolicyMapper.selectCurrentPolicy(
                PRODUCT_CODE, planCode);
        assertNotNull(value);
        return value;
    }

    private static long receiptCount(String planCode) {
        return scalarLong(
                "SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt "
                        + "WHERE product_code=? AND plan_code=?",
                PRODUCT_CODE, planCode);
    }

    private static void assertDenseReceiptChain(String planCode) {
        BoardPlanPolicySnapshot head = current(planCode);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fbs_plan_policy_revision_receipt "
                        + "WHERE product_code=? AND plan_code=?",
                Long.class, PRODUCT_CODE, planCode);
        Long minimum = jdbc.queryForObject(
                "SELECT MIN(policy_version) FROM fbs_plan_policy_revision_receipt "
                        + "WHERE product_code=? AND plan_code=?",
                Long.class, PRODUCT_CODE, planCode);
        Long maximum = jdbc.queryForObject(
                "SELECT MAX(policy_version) FROM fbs_plan_policy_revision_receipt "
                        + "WHERE product_code=? AND plan_code=?",
                Long.class, PRODUCT_CODE, planCode);
        assertEquals(1L, minimum);
        assertEquals(head.getPolicyVersion(), maximum);
        assertEquals(head.getPolicyVersion(), count,
                "every committed version must have exactly one receipt and no failed orphan");
    }

    private static void assertCatalogInvariant() {
        BoardPlanPolicySnapshot free = current(FREE_PLAN);
        BoardPlanPolicySnapshot vip = current(VIP_PLAN);
        assertTrue(vip.getDailyMeetingLimit() >= free.getDailyMeetingLimit());
        assertTrue(vip.getAgendaLimit() >= free.getAgendaLimit());
        assertTrue(vip.getSeatLimit() == null
                || vip.getSeatLimit() >= free.getSeatLimit());
        assertTrue(!Boolean.TRUE.equals(free.getSecretaryEnabled())
                || Boolean.TRUE.equals(vip.getSecretaryEnabled()));
    }

    private static void assertPolicyStateEquals(
            BoardPlanPolicyReceipt expected,
            BoardPlanPolicyReceipt actual) {
        assertNotNull(actual);
        assertEquals(expected.getReceiptId(), actual.getReceiptId());
        assertEquals(expected.getProductCode(), actual.getProductCode());
        assertEquals(expected.getPlanCode(), actual.getPlanCode());
        assertEquals(expected.getPolicyVersion(), actual.getPolicyVersion());
        assertEquals(expected.getPolicyDigest(), actual.getPolicyDigest());
        assertEquals(expected.getPlanName(), actual.getPlanName());
        assertEquals(expected.getVip(), actual.getVip());
        assertEquals(expected.getConnectorRequired(), actual.getConnectorRequired());
        assertEquals(expected.getDailyMeetingLimit(), actual.getDailyMeetingLimit());
        assertEquals(expected.getAgendaLimit(), actual.getAgendaLimit());
        assertEquals(expected.getSeatLimit(), actual.getSeatLimit());
        assertEquals(expected.getSecretaryEnabled(), actual.getSecretaryEnabled());
        assertEquals(expected.getStatus(), actual.getStatus());
    }

    private static long scalarLong(String sql, Object... arguments) {
        Long value = jdbc.queryForObject(sql, Long.class, arguments);
        assertNotNull(value);
        return value;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required system property: " + name);
        }
        return value;
    }

    private static void assertSafeDedicatedUrl(String url) {
        if (!url.matches(
                "^jdbc:mysql://127\\.0\\.0\\.1:(?!3306(?:/|\\?))\\d+/"
                        + "w3h_policy_concurrency(?:\\?.*)?$")) {
            throw new IllegalStateException(
                    "Refusing a non-loopback, production-port, or non-dedicated JDBC URL");
        }
    }

    private record Attempt(BoardPlanPolicyRevisionView view, String error, Integer status) {
        static Attempt succeeded(BoardPlanPolicyRevisionView view) {
            return new Attempt(Objects.requireNonNull(view), null, null);
        }

        static Attempt failed(ServiceException failure) {
            return new Attempt(null, failure.getMessage(), failure.getCode());
        }

        boolean succeeded() {
            return view != null;
        }
    }

    private record PauseGate(CountDownLatch headUpdated, CountDownLatch release) {
        void awaitRelease() {
            try {
                if (!release.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("head-update pause release timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("head-update pause interrupted", interrupted);
            }
        }
    }

    static final class FaultInjectingPlanPolicyMapper
            implements IndependentBoardPlanPolicyMapper {
        private final IndependentBoardPlanPolicyMapper delegate;
        private final AtomicReference<CyclicBarrier> catalogBarrier = new AtomicReference<>();
        private final AtomicReference<PauseGate> pauseAfterHeadUpdate = new AtomicReference<>();
        private final AtomicBoolean failNextHeadCas = new AtomicBoolean();
        private final AtomicReference<String> insertedBeforeFailedCas = new AtomicReference<>();

        FaultInjectingPlanPolicyMapper(IndependentBoardPlanPolicyMapper delegate) {
            this.delegate = delegate;
        }

        void alignNextCatalogLockPair() {
            if (!catalogBarrier.compareAndSet(null, new CyclicBarrier(2))) {
                throw new IllegalStateException("catalog barrier is already armed");
            }
        }

        void clearCatalogBarrier() {
            catalogBarrier.set(null);
        }

        void failNextHeadCasAfterRealInsert() {
            insertedBeforeFailedCas.set(null);
            if (!failNextHeadCas.compareAndSet(false, true)) {
                throw new IllegalStateException("CAS fault is already armed");
            }
        }

        boolean isCasFailureArmed() {
            return failNextHeadCas.get();
        }

        String consumeInsertedBeforeFailedCas() {
            return insertedBeforeFailedCas.getAndSet(null);
        }

        PauseGate pauseAfterNextSuccessfulHeadUpdate() {
            PauseGate gate = new PauseGate(new CountDownLatch(1), new CountDownLatch(1));
            if (!pauseAfterHeadUpdate.compareAndSet(null, gate)) {
                throw new IllegalStateException("head-update pause is already armed");
            }
            return gate;
        }

        void clearPauseGate() {
            pauseAfterHeadUpdate.set(null);
        }

        void releasePauseGate() {
            PauseGate gate = pauseAfterHeadUpdate.getAndSet(null);
            if (gate != null) {
                gate.release().countDown();
            }
        }

        void clearHooks() {
            clearCatalogBarrier();
            releasePauseGate();
            failNextHeadCas.set(false);
            insertedBeforeFailedCas.set(null);
        }

        @Override
        public List<BoardPlanPolicySnapshot> selectCurrentPolicies(String productCode) {
            return delegate.selectCurrentPolicies(productCode);
        }

        @Override
        public List<String> selectPolicyHeadCodesForUpdate(
                String productCode) {
            CyclicBarrier barrier = catalogBarrier.get();
            if (barrier != null) {
                try {
                    barrier.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException("catalog lock barrier failed", failure);
                }
            }
            return delegate.selectPolicyHeadCodesForUpdate(productCode);
        }

        @Override
        public BoardPlanPolicySnapshot selectCurrentPolicy(
                String productCode,
                String planCode) {
            return delegate.selectCurrentPolicy(productCode, planCode);
        }

        @Override
        public BoardPlanPolicyReceipt selectReceiptByActorAndIdempotencyDigest(
                String productCode,
                String actorType,
                Long actorUserId,
                String idempotencyKeyDigest) {
            return delegate.selectReceiptByActorAndIdempotencyDigest(
                    productCode, actorType, actorUserId, idempotencyKeyDigest);
        }

        @Override
        public BoardPlanPolicyReceipt selectReceiptByReceiptId(
                String productCode,
                String receiptId) {
            return delegate.selectReceiptByReceiptId(productCode, receiptId);
        }

        @Override
        public int insertReceipt(BoardPlanPolicyReceipt receipt) {
            int inserted = delegate.insertReceipt(receipt);
            if (inserted == 1 && failNextHeadCas.get()) {
                insertedBeforeFailedCas.set(receipt.getReceiptId());
            }
            return inserted;
        }

        @Override
        public int updateHeadIfCurrent(
                String productCode,
                String planCode,
                String expectedReceiptId,
                long expectedVersion,
                String nextReceiptId,
                long nextVersion,
                Date updatedAt) {
            if (failNextHeadCas.compareAndSet(true, false)) {
                return 0;
            }
            int updated = delegate.updateHeadIfCurrent(
                    productCode, planCode, expectedReceiptId, expectedVersion,
                    nextReceiptId, nextVersion, updatedAt);
            PauseGate gate = pauseAfterHeadUpdate.get();
            if (updated == 1 && gate != null) {
                gate.headUpdated().countDown();
                gate.awaitRelease();
            }
            return updated;
        }

        @Override
        public List<BoardPlanPolicyReceipt> selectPolicyReceipts(
                String productCode,
                int limit) {
            return delegate.selectPolicyReceipts(productCode, limit);
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean(destroyMethod = "close")
        HikariDataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setJdbcUrl(requiredProperty(
                    "independent.board.plan.policy.mysql.it.url"));
            config.setUsername(requiredProperty(
                    "independent.board.plan.policy.mysql.it.username"));
            config.setPassword(System.getProperty(
                    "independent.board.plan.policy.mysql.it.password", ""));
            config.setMaximumPoolSize(8);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(5_000);
            config.setValidationTimeout(2_000);
            config.setInitializationFailTimeout(5_000);
            config.setPoolName("w3h-plan-policy-concurrency-it");
            config.setConnectionInitSql("SET SESSION innodb_lock_wait_timeout=2");
            return new HikariDataSource(config);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Resource[] mapperLocations = {
                resolver.getResource(
                        "classpath:mapper/board/IndependentBoardPlanPolicyMapper.xml"),
                resolver.getResource("classpath:mapper/board/IndependentBoardMapper.xml")
            };
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(mapperLocations);
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean("realPlanPolicyMapper")
        IndependentBoardPlanPolicyMapper realPlanPolicyMapper(SqlSessionTemplate template) {
            return template.getMapper(IndependentBoardPlanPolicyMapper.class);
        }

        @Bean
        @Primary
        FaultInjectingPlanPolicyMapper planPolicyMapper(
                @Qualifier("realPlanPolicyMapper") IndependentBoardPlanPolicyMapper delegate) {
            return new FaultInjectingPlanPolicyMapper(delegate);
        }

        @Bean
        IndependentBoardMapper independentBoardMapper(SqlSessionTemplate template) {
            return template.getMapper(IndependentBoardMapper.class);
        }

        @Bean
        IndependentBoardPlanPolicyTransactionService transactionService(
                FaultInjectingPlanPolicyMapper mapper) {
            return new IndependentBoardPlanPolicyTransactionService(mapper);
        }

        @Bean
        IndependentBoardPlanPolicyService planPolicyService(
                IndependentBoardPlanPolicyTransactionService transactionService) {
            return new IndependentBoardPlanPolicyService(transactionService);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
