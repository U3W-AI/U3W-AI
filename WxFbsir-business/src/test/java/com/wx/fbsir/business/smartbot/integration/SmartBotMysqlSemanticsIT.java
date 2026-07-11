package com.wx.fbsir.business.smartbot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotContentArtifactPayload;
import com.wx.fbsir.business.smartbot.dto.SmartBotInboundEnvelope;
import com.wx.fbsir.business.smartbot.dto.SmartBotIngressResult;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import com.wx.fbsir.business.smartbot.mapper.SmartBotInputArtifactMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotMemberBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomInboundEventMapper;
import com.wx.fbsir.business.smartbot.service.ExternalIdentityHasher;
import com.wx.fbsir.business.smartbot.service.InternalOutboxDispatcherService;
import com.wx.fbsir.business.smartbot.service.InternalRunActivationService;
import com.wx.fbsir.business.smartbot.service.OutboxClaimTransactionService;
import com.wx.fbsir.business.smartbot.service.OutboxLeaseService;
import com.wx.fbsir.business.smartbot.service.SmartBotIngressService;
import com.wx.fbsir.business.smartbot.service.SmartBotInputArtifactService;
import com.wx.fbsir.business.smartbot.service.SmartBotInputCryptoService;
import com.wx.fbsir.business.smartbot.service.SecretReferenceResolver;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit real-MySQL contract test. The normal Maven test lifecycle does not
 * discover *IT classes; run it by name with a disposable database only.
 */
class SmartBotMysqlSemanticsIT {

    private static final String DATABASE = "u3w_smartbot_it";
    private static final byte[] SOURCE_PAYLOAD = ("{\"msgid\":\"msg-01\",\"aibotid\":\"AIBOT-01\","
        + "\"from\":{\"userid\":\"opaque-user-01\"},\"msgtype\":\"text\","
        + "\"text\":{\"content\":\"mysql-contract-test\"}}").getBytes(StandardCharsets.UTF_8);
    private static final String PAYLOAD_HASH = sha256(SOURCE_PAYLOAD);
    private static final String HMAC_KEY_BASE64 =
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static SmartBotIngressService ingressService;
    private static OutboxLeaseService outboxLeaseService;
    private static InternalOutboxDispatcherService internalDispatcher;
    private static InternalRunActivationService activationService;
    private static ExternalIdentityHasher identityHasher;
    private static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void startContextAndApplyCurrentMigration() throws Exception {
        String url = required("SMARTBOT_MYSQL_IT_URL");
        if (!"true".equals(required("SMARTBOT_MYSQL_IT_ALLOW_DROP"))) {
            throw new IllegalStateException("SMARTBOT_MYSQL_IT_ALLOW_DROP must equal true");
        }
        if (!url.matches("^jdbc:mysql://(127\\.0\\.0\\.1|localhost|mysql):\\d+/"
                + DATABASE + "(?:\\?.*)?$") || !url.contains("useAffectedRows=false")) {
            throw new IllegalStateException("MySQL IT URL must target the dedicated database on an allowed host with useAffectedRows=false");
        }

        System.setProperty("smartbot.mysql.it.url", url);
        System.setProperty("smartbot.mysql.it.username", required("SMARTBOT_MYSQL_IT_USERNAME"));
        String password = value("SMARTBOT_MYSQL_IT_PASSWORD");
        System.setProperty("smartbot.mysql.it.password", password == null ? "" : password);

        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        dataSource = context.getBean(DataSource.class);
        ingressService = context.getBean(SmartBotIngressService.class);
        outboxLeaseService = context.getBean(OutboxLeaseService.class);
        internalDispatcher = context.getBean(InternalOutboxDispatcherService.class);
        activationService = context.getBean(InternalRunActivationService.class);
        identityHasher = context.getBean(ExternalIdentityHasher.class);
        transactionTemplate = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));

        assertDedicatedDatabase();
        dropTestTables();
        createFbsIdentityTables();
        applyCurrentMigrations();
        assertMySqlContract();
    }

    @AfterAll
    static void closeContext() {
        if (context != null) {
            context.close();
        }
        System.clearProperty("smartbot.mysql.it.url");
        System.clearProperty("smartbot.mysql.it.username");
        System.clearProperty("smartbot.mysql.it.password");
    }

    @BeforeEach
    void resetAndSeed() throws Exception {
        execute(
            "DROP TRIGGER IF EXISTS smartbot_it_fail_step",
            "DROP TRIGGER IF EXISTS smartbot_it_fail_activation_step",
            "DROP TRIGGER IF EXISTS smartbot_it_fail_activation_run",
            "DROP TRIGGER IF EXISTS smartbot_it_fail_activation_outbox",
            "DELETE FROM fbs_delivery_outbox",
            "DELETE FROM fbs_orchestration_receipt",
            "DELETE FROM fbs_orchestration_step",
            "DELETE FROM fbs_smartbot_input_artifact",
            "DELETE FROM fbs_orchestration_run",
            "DELETE FROM fbs_inbound_event",
            "DELETE FROM fbs_bot_member_binding",
            "DELETE FROM fbs_bot_binding",
            "DELETE FROM fbs_enterprise_member",
            "DELETE FROM fbs_enterprise",
            "INSERT INTO fbs_enterprise (id, enterprise_code, enterprise_name, status, del_flag) "
                + "VALUES (11, 'ENT_IT', 'SmartBot IT', 1, '0')",
            "INSERT INTO fbs_enterprise_member (id, enterprise_id, user_id, role, status, del_flag) "
                + "VALUES (21, 11, 31, 'MEMBER', 1, '0')"
        );
        seedBot(7L, "AIBOT-01", "callback_key_it_0001", "opaque-user-01");
    }

    @Test
    void firstAndDuplicateDeliveryReuseGeneratedIdAndStableRun() throws Exception {
        SmartBotIngressResult first = accept(binding(7L, "AIBOT-01"), envelope());
        SmartBotIngressResult duplicate = accept(binding(7L, "AIBOT-01"), envelope());

        assertTrue(first.firstDelivery());
        assertFalse(duplicate.firstDelivery());
        assertTrue(first.inboundEventId() > 0);
        assertEquals(first.inboundEventId(), duplicate.inboundEventId());
        assertEquals(first.traceId(), duplicate.traceId());
        assertEquals(first.runId(), duplicate.runId());
        assertEquals(first.streamId(), duplicate.streamId());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
        assertEquals(1, scalarInt("SELECT duplicate_count FROM fbs_inbound_event"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_smartbot_input_artifact"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox"));
        String inputRef = scalarString("SELECT input_ref FROM fbs_smartbot_input_artifact");
        assertTrue(inputRef.matches("vault:v1:[0-9a-f-]{36}"));
        assertEquals(inputRef, scalarString(
            "SELECT input_ref FROM fbs_orchestration_step WHERE step_key = 'ingress.accepted'"));
        assertTrue(scalarString("SELECT payload_json FROM fbs_delivery_outbox").contains(inputRef));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_smartbot_input_artifact "
            + "WHERE LOCATE('mysql-contract-test', ciphertext) > 0"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox "
            + "WHERE payload_json LIKE '%mysql-contract-test%'"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_receipt"));
    }

    @RepeatedTest(5)
    void thirtyTwoConcurrentDeliveriesCreateOneDurableRun() throws Exception {
        int concurrency = 32;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<SmartBotIngressResult>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    return accept(binding(7L, "AIBOT-01"), envelope());
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int winners = 0;
            Set<Long> eventIds = new HashSet<>();
            Set<String> runIds = new HashSet<>();
            Set<String> streamIds = new HashSet<>();
            for (Future<SmartBotIngressResult> future : futures) {
                SmartBotIngressResult result = future.get(30, TimeUnit.SECONDS);
                winners += result.firstDelivery() ? 1 : 0;
                eventIds.add(result.inboundEventId());
                runIds.add(result.runId());
                streamIds.add(result.streamId());
            }

            assertEquals(1, winners);
            assertEquals(1, eventIds.size());
            assertEquals(1, runIds.size());
            assertEquals(1, streamIds.size());
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
            assertEquals(31, scalarInt("SELECT duplicate_count FROM fbs_inbound_event"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_smartbot_input_artifact"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step"));
            assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void forcedStepFailureRollsBackClaimAndCanBeRetried() throws Exception {
        execute("CREATE TRIGGER smartbot_it_fail_step BEFORE INSERT ON fbs_orchestration_step "
            + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced smartbot IT failure'");

        assertThrows(RuntimeException.class,
            () -> accept(binding(7L, "AIBOT-01"), envelope()));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_smartbot_input_artifact"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox"));

        execute("DROP TRIGGER smartbot_it_fail_step");
        SmartBotIngressResult retry = accept(binding(7L, "AIBOT-01"), envelope());
        assertTrue(retry.firstDelivery());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
    }

    @Test
    void mismatchedDuplicatePayloadRollsBackDuplicateCounter() throws Exception {
        accept(binding(7L, "AIBOT-01"), envelope());
        SmartBotInboundEnvelope changed = SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("AIBOT-01")
            .opaqueSenderId("opaque-user-01")
            .chatType("single")
            .msgType("text")
            .payloadHash(sha256("changed-source".getBytes(StandardCharsets.UTF_8)))
            .build();

        assertThrows(SecurityException.class,
            () -> ingressService.accept(binding(7L, "AIBOT-01"), changed,
                "changed-source".getBytes(StandardCharsets.UTF_8), content()));
        assertEquals(0, scalarInt("SELECT duplicate_count FROM fbs_inbound_event"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
    }

    @Test
    void sameProviderMessageIdIsIsolatedByBot() throws Exception {
        seedBot(8L, "AIBOT-02", "callback_key_it_0002", "opaque-user-01");

        SmartBotIngressResult first = accept(binding(7L, "AIBOT-01"), envelope());
        SmartBotInboundEnvelope secondEnvelope = SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("AIBOT-02")
            .opaqueSenderId("opaque-user-01")
            .chatType("single")
            .msgType("text")
            .payloadHash(PAYLOAD_HASH)
            .build();
        SmartBotIngressResult second = accept(binding(8L, "AIBOT-02"), secondEnvelope);

        assertTrue(first.firstDelivery());
        assertTrue(second.firstDelivery());
        assertFalse(first.inboundEventId().equals(second.inboundEventId()));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
    }

    @Test
    void concurrentOutboxClaimHasOneWinnerAndFencingControlsCompletion() throws Exception {
        accept(binding(7L, "AIBOT-01"), envelope());
        int concurrency = 16;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<DeliveryOutbox>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                String owner = "worker-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    for (int attempt = 0; attempt < 100; attempt++) {
                        Optional<DeliveryOutbox> claimed =
                            outboxLeaseService.claimNext(owner, Duration.ofSeconds(30));
                        if (claimed.isPresent()) {
                            return claimed;
                        }
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    }
                    return Optional.empty();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            List<DeliveryOutbox> claimed = new ArrayList<>();
            for (Future<Optional<DeliveryOutbox>> future : futures) {
                future.get(30, TimeUnit.SECONDS).ifPresent(claimed::add);
            }
            assertEquals(1, claimed.size());
            DeliveryOutbox winner = claimed.get(0);
            assertEquals("LEASED", winner.getStatus());
            assertEquals(1, winner.getAttemptCount());
            assertEquals(1, scalarInt("SELECT attempt_count FROM fbs_delivery_outbox"));

            assertThrows(IllegalStateException.class, () -> outboxLeaseService.markConsumed(
                winner.getId(), "00000000-0000-0000-0000-000000000001"));
            assertEquals("LEASED", scalarString("SELECT status FROM fbs_delivery_outbox"));
            outboxLeaseService.markConsumed(winner.getId(), winner.getLeaseToken());
            assertEquals("CONSUMED", scalarString("SELECT status FROM fbs_delivery_outbox"));
            assertEquals(0, scalarInt(
                "SELECT COUNT(*) FROM fbs_delivery_outbox WHERE lease_token IS NOT NULL"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void expiredLeaseCanBeReclaimedAndStaleWorkerCannotMutateIt() throws Exception {
        accept(binding(7L, "AIBOT-01"), envelope());
        DeliveryOutbox first = outboxLeaseService
            .claimNext("worker-first", Duration.ofSeconds(30)).orElseThrow();
        execute("UPDATE fbs_delivery_outbox SET lease_until = DATE_SUB(NOW(), INTERVAL 1 SECOND)");

        assertThrows(IllegalStateException.class,
            () -> outboxLeaseService.extendLease(first.getId(), first.getLeaseToken(),
                Duration.ofSeconds(30)));
        assertThrows(IllegalStateException.class,
            () -> outboxLeaseService.markConsumed(first.getId(), first.getLeaseToken()));
        assertThrows(IllegalStateException.class,
            () -> outboxLeaseService.scheduleRetry(first.getId(), first.getLeaseToken(),
                new Date(), "stale retry"));
        assertThrows(IllegalStateException.class,
            () -> outboxLeaseService.markDead(first.getId(), first.getLeaseToken(), "stale dead"));

        DeliveryOutbox second = outboxLeaseService
            .claimNext("worker-second", Duration.ofSeconds(30)).orElseThrow();
        assertFalse(first.getLeaseToken().equals(second.getLeaseToken()));
        assertEquals(2, second.getAttemptCount());
        assertThrows(IllegalStateException.class,
            () -> outboxLeaseService.markDead(first.getId(), first.getLeaseToken(), "stale"));

        outboxLeaseService.scheduleRetry(second.getId(), second.getLeaseToken(),
            new Date(System.currentTimeMillis() - 1000), "temporary\nerror");
        DeliveryOutbox third = outboxLeaseService
            .claimNext("worker-third", Duration.ofSeconds(30)).orElseThrow();
        assertEquals(3, third.getAttemptCount());
        assertEquals("temporary error", scalarString("SELECT last_error FROM fbs_delivery_outbox"));
        outboxLeaseService.markDead(third.getId(), third.getLeaseToken(), "permanent error");
        assertEquals("DEAD", scalarString("SELECT status FROM fbs_delivery_outbox"));
        assertEquals("permanent error", scalarString("SELECT last_error FROM fbs_delivery_outbox"));
    }

    @RepeatedTest(5)
    void concurrentWorkersDrainIndependentRowsWithoutDuplicates() throws Exception {
        List<String> inserts = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            inserts.add("INSERT INTO fbs_delivery_outbox "
                + "(event_key, run_id, event_type, destination_type, payload_json, status, "
                + "attempt_count, next_attempt_at, create_time, update_time) VALUES "
                + "('drain-" + i + "', '00000000-0000-0000-0000-0000000000"
                + String.format("%02d", i) + "', 'RUN_CREATED', 'INTERNAL_DISPATCHER', '{}', "
                + "'PENDING', 0, DATE_SUB(NOW(), INTERVAL 1 SECOND), NOW(), NOW())");
        }
        execute(inserts.toArray(String[]::new));

        int concurrency = 16;
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<DeliveryOutbox>>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < concurrency; i++) {
                String owner = "drain-worker-" + i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start barrier timed out");
                    }
                    for (int attempt = 0; attempt < 100; attempt++) {
                        Optional<DeliveryOutbox> claimed =
                            outboxLeaseService.claimNext(owner, Duration.ofSeconds(30));
                        if (claimed.isPresent()) {
                            return claimed;
                        }
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                    }
                    return Optional.empty();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            Set<Long> ids = new HashSet<>();
            for (Future<Optional<DeliveryOutbox>> future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS).orElseThrow().getId());
            }
            assertEquals(16, ids.size());
            assertEquals(16, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox WHERE status = 'LEASED'"));
            assertEquals(16, scalarInt("SELECT SUM(attempt_count) FROM fbs_delivery_outbox"));
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void claimUsesAnIndependentShortTransaction() throws Exception {
        accept(binding(7L, "AIBOT-01"), envelope());

        assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status -> {
            assertTrue(outboxLeaseService.claimNext("rollback-worker", Duration.ofSeconds(30)).isPresent());
            throw new IllegalStateException("force rollback");
        }));

        assertEquals("LEASED", scalarString("SELECT status FROM fbs_delivery_outbox"));
        assertEquals(1, scalarInt("SELECT attempt_count FROM fbs_delivery_outbox"));
        assertEquals(1, scalarInt(
            "SELECT COUNT(*) FROM fbs_delivery_outbox WHERE lease_token IS NOT NULL AND lease_owner IS NOT NULL"));
    }

    @Test
    void internalDispatcherAtomicallyMakesRunReadyAndConsumesOutbox() throws Exception {
        accept(binding(7L, "AIBOT-01"), envelope());

        InternalOutboxDispatcherService.DispatchOutcome result = internalDispatcher
            .dispatchOne("internal-worker", Duration.ofSeconds(30)).orElseThrow();

        assertEquals("READY", result.status());
        assertEquals("READY", scalarString("SELECT status FROM fbs_orchestration_run"));
        assertEquals(1, scalarInt("SELECT version FROM fbs_orchestration_run"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step "
            + "WHERE step_key = 'internal.dispatch.accepted' AND status = 'SUCCEEDED'"));
        assertEquals("CONSUMED", scalarString("SELECT status FROM fbs_delivery_outbox"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox "
            + "WHERE lease_owner IS NOT NULL OR lease_token IS NOT NULL OR lease_until IS NOT NULL"));
        assertTrue(internalDispatcher.dispatchOne("internal-worker", Duration.ofSeconds(30)).isEmpty());
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_receipt"));
    }

    @Test
    void internalWorkerNeverClaimsResponseUrlDestination() throws Exception {
        execute("INSERT INTO fbs_delivery_outbox "
            + "(event_key, run_id, event_type, destination_type, payload_json, status, "
            + "attempt_count, next_attempt_at, create_time, update_time) VALUES "
            + "('external-only', '22222222-2222-2222-2222-222222222222', 'REPLY', "
            + "'RESPONSE_URL', '{}', 'PENDING', 0, DATE_SUB(NOW(), INTERVAL 1 SECOND), NOW(), NOW())");

        assertTrue(internalDispatcher.dispatchOne(
            "internal-worker", Duration.ofSeconds(30)).isEmpty());
        assertEquals("PENDING", scalarString("SELECT status FROM fbs_delivery_outbox"));
        assertEquals(0, scalarInt("SELECT attempt_count FROM fbs_delivery_outbox"));
    }

    @Test
    void activationStepFailureRollsBackTransactionBAndCanBeReclaimed() throws Exception {
        assertActivationRollbackAndRecovery(
            "smartbot_it_fail_activation_step",
            "CREATE TRIGGER smartbot_it_fail_activation_step BEFORE INSERT ON fbs_orchestration_step "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced activation step failure'");
    }

    @Test
    void activationRunFailureRollsBackTransactionBAndCanBeReclaimed() throws Exception {
        assertActivationRollbackAndRecovery(
            "smartbot_it_fail_activation_run",
            "CREATE TRIGGER smartbot_it_fail_activation_run BEFORE UPDATE ON fbs_orchestration_run "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced activation run failure'");
    }

    @Test
    void outboxConsumeFailureRollsBackRunAndStepAndCanBeReclaimed() throws Exception {
        assertActivationRollbackAndRecovery(
            "smartbot_it_fail_activation_outbox",
            "CREATE TRIGGER smartbot_it_fail_activation_outbox BEFORE UPDATE ON fbs_delivery_outbox "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'forced outbox consume failure'");
    }

    private void assertActivationRollbackAndRecovery(String triggerName,
                                                     String createTriggerSql) throws Exception {
        accept(binding(7L, "AIBOT-01"), envelope());
        DeliveryOutbox claimed = outboxLeaseService
            .claimNext("failure-worker", Duration.ofSeconds(30)).orElseThrow();
        execute(createTriggerSql);

        assertThrows(RuntimeException.class, () -> activationService.apply(claimed));
        assertEquals("PENDING", scalarString("SELECT status FROM fbs_orchestration_run"));
        assertEquals(0, scalarInt("SELECT version FROM fbs_orchestration_run"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step "
            + "WHERE step_key = 'internal.dispatch.accepted'"));
        assertEquals("LEASED", scalarString("SELECT status FROM fbs_delivery_outbox"));
        assertEquals(claimed.getLeaseToken(), scalarString("SELECT lease_token FROM fbs_delivery_outbox"));

        execute("DROP TRIGGER " + triggerName,
            "UPDATE fbs_delivery_outbox SET lease_until = DATE_SUB(NOW(), INTERVAL 1 SECOND)");
        assertEquals("READY", internalDispatcher.dispatchOne(
            "recovery-worker", Duration.ofSeconds(30)).orElseThrow().status());
        assertEquals("READY", scalarString("SELECT status FROM fbs_orchestration_run"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step "
            + "WHERE step_key = 'internal.dispatch.accepted'"));
        assertEquals("CONSUMED", scalarString("SELECT status FROM fbs_delivery_outbox"));
    }

    private static void assertDedicatedDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT DATABASE()")) {
            assertTrue(result.next());
            assertEquals(DATABASE, result.getString(1));
        }
    }

    private static void assertMySqlContract() throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("SELECT VERSION(), @@version_comment")) {
                assertTrue(version.next());
                assertTrue(version.getString(1).startsWith("8."));
                assertTrue(version.getString(2).contains("MySQL"));
            }
            try (ResultSet engine = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() "
                        + "AND table_name LIKE 'fbs_%' AND engine <> 'InnoDB'")) {
                assertTrue(engine.next());
                assertEquals(0, engine.getInt(1));
            }
        }
    }

    private static void dropTestTables() throws Exception {
        execute(
            "DROP TRIGGER IF EXISTS smartbot_it_fail_step",
            "DROP TABLE IF EXISTS fbs_delivery_outbox",
            "DROP TABLE IF EXISTS fbs_orchestration_receipt",
            "DROP TABLE IF EXISTS fbs_orchestration_step",
            "DROP TABLE IF EXISTS fbs_smartbot_input_artifact",
            "DROP TABLE IF EXISTS fbs_orchestration_run",
            "DROP TABLE IF EXISTS fbs_inbound_event",
            "DROP TABLE IF EXISTS fbs_bot_member_binding",
            "DROP TABLE IF EXISTS fbs_bot_binding",
            "DROP TABLE IF EXISTS fbs_enterprise_member",
            "DROP TABLE IF EXISTS fbs_enterprise"
        );
    }

    private static void createFbsIdentityTables() throws Exception {
        execute(
            "CREATE TABLE IF NOT EXISTS fbs_enterprise ("
                + "id BIGINT NOT NULL PRIMARY KEY, enterprise_code VARCHAR(64), enterprise_name VARCHAR(128), "
                + "contact_name VARCHAR(64), contact_phone VARCHAR(32), contact_email VARCHAR(128), "
                + "remark VARCHAR(500), status TINYINT NOT NULL, created_by VARCHAR(64), "
                + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP, updated_by VARCHAR(64), "
                + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP, del_flag CHAR(1) NOT NULL) ENGINE=InnoDB",
            "CREATE TABLE IF NOT EXISTS fbs_enterprise_member ("
                + "id BIGINT NOT NULL PRIMARY KEY, enterprise_id BIGINT NOT NULL, user_id BIGINT NOT NULL, "
                + "role VARCHAR(32), join_time DATETIME, status TINYINT NOT NULL, created_by VARCHAR(64), "
                + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP, updated_by VARCHAR(64), "
                + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP, del_flag CHAR(1) NOT NULL) ENGINE=InnoDB"
        );
    }

    private static void applyCurrentMigrations() throws Exception {
        applyMigration("update_20260711_企微智能机器人编排控制面建表.sql");
        applyMigration("update_20260711_企微智能机器人编排Outbox租约增强.sql");
        applyMigration("update_20260711_企微智能机器人编排加密内容工件.sql");
    }

    private static void applyMigration(String name) throws Exception {
        String sql = Files.readString(locateMigration(name), StandardCharsets.UTF_8);
        List<String> statements = Arrays.stream(sql.split(";\\s*(?:\\r?\\n|$)"))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
        execute(statements.toArray(String[]::new));
    }

    private static Path locateMigration(String name) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve("sql").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("current SmartBot migration not found: " + name);
    }

    private void seedBot(Long botId, String aibotId, String callbackKey,
                         String opaqueSenderId) throws Exception {
        String userHash = identityHasher.hashUser(botId, opaqueSenderId);
        execute(
            "INSERT INTO fbs_bot_binding (id, callback_key, aibot_id, enterprise_id, mode, "
                + "token_secret_ref, aes_key_secret_ref, credential_version, status, del_flag) VALUES ("
                + botId + ", '" + callbackKey + "', '" + aibotId + "', 11, 'CALLBACK', "
                + "'env:TEST_TOKEN', 'env:TEST_AES', 1, 1, '0')",
            "INSERT INTO fbs_bot_member_binding (bot_binding_id, enterprise_id, enterprise_member_id, "
                + "user_id, external_user_hash, status, del_flag) VALUES ("
                + botId + ", 11, 21, 31, '" + userHash + "', 1, '0')"
        );
    }

    private ResolvedBotBinding binding(Long botId, String aibotId) {
        return new ResolvedBotBinding(botId, "masked", aibotId, 11L,
            "CALLBACK", 1, "test-token", "test-aes");
    }

    private SmartBotIngressResult accept(ResolvedBotBinding binding, SmartBotInboundEnvelope envelope) {
        return ingressService.accept(binding, envelope, SOURCE_PAYLOAD, content());
    }

    private SmartBotContentArtifactPayload content() {
        return SmartBotContentArtifactPayload.ofUtf8Json(SmartBotInputArtifactService.PURPOSE,
            "{\"schemaVersion\":1,\"sourceType\":\"WECOM_SMARTBOT_CALLBACK\","
                + "\"kind\":\"smartbot.input.message.v1\",\"msgType\":\"text\","
                + "\"classification\":\"UNTRUSTED_USER_CONTENT\","
                + "\"content\":{\"text\":\"mysql-contract-test\"}}");
    }

    private SmartBotInboundEnvelope envelope() {
        return SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("AIBOT-01")
            .opaqueSenderId("opaque-user-01")
            .chatType("single")
            .msgType("text")
            .payloadHash(PAYLOAD_HASH)
            .build();
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String scalarString(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void execute(String... statements) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
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

    @Configuration
    @EnableTransactionManagement
    static class TestConfiguration {

        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            dataSource.setUrl(System.getProperty("smartbot.mysql.it.url"));
            dataSource.setUsername(System.getProperty("smartbot.mysql.it.username"));
            dataSource.setPassword(System.getProperty("smartbot.mysql.it.password"));
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<Resource> resources = new ArrayList<>();
            resources.addAll(Arrays.asList(resolver.getResources("classpath*:mapper/smartbot/*.xml")));
            resources.add(resolver.getResource("classpath:mapper/fbs/FbsEnterpriseMapper.xml"));
            resources.add(resolver.getResource("classpath:mapper/fbs/FbsEnterpriseMemberMapper.xml"));
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(resources.toArray(Resource[]::new));
            return factory.getObject();
        }

        @Bean
        MapperFactoryBean<WecomBotBindingMapper> botBindingMapper(SqlSessionFactory factory) {
            return mapper(WecomBotBindingMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<WecomBotMemberBindingMapper> botMemberBindingMapper(SqlSessionFactory factory) {
            return mapper(WecomBotMemberBindingMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<WecomInboundEventMapper> inboundEventMapper(SqlSessionFactory factory) {
            return mapper(WecomInboundEventMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<OrchestrationRunMapper> runMapper(SqlSessionFactory factory) {
            return mapper(OrchestrationRunMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<OrchestrationStepMapper> stepMapper(SqlSessionFactory factory) {
            return mapper(OrchestrationStepMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<SmartBotInputArtifactMapper> inputArtifactMapper(SqlSessionFactory factory) {
            return mapper(SmartBotInputArtifactMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<DeliveryOutboxMapper> outboxMapper(SqlSessionFactory factory) {
            return mapper(DeliveryOutboxMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<FbsEnterpriseMapper> enterpriseMapper(SqlSessionFactory factory) {
            return mapper(FbsEnterpriseMapper.class, factory);
        }

        @Bean
        MapperFactoryBean<FbsEnterpriseMemberMapper> enterpriseMemberMapper(SqlSessionFactory factory) {
            return mapper(FbsEnterpriseMemberMapper.class, factory);
        }

        @Bean
        ExternalIdentityHasher identityHasher() {
            return new ExternalIdentityHasher(HMAC_KEY_BASE64);
        }

        @Bean
        SecretReferenceResolver secretReferenceResolver() {
            return reference -> {
                if (!SmartBotInputCryptoService.KEY_REF.equals(reference)) {
                    throw new IllegalArgumentException("unexpected test secret reference");
                }
                return HMAC_KEY_BASE64;
            };
        }

        @Bean
        SmartBotInputCryptoService smartBotInputCryptoService(SecretReferenceResolver secretReferenceResolver) {
            return new SmartBotInputCryptoService(secretReferenceResolver);
        }

        @Bean
        SmartBotInputArtifactService smartBotInputArtifactService(
                SmartBotInputArtifactMapper inputArtifactMapper,
                SmartBotInputCryptoService smartBotInputCryptoService) {
            return new SmartBotInputArtifactService(inputArtifactMapper, smartBotInputCryptoService);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SmartBotIngressService smartBotIngressService(WecomBotBindingMapper botBindingMapper,
                                                       WecomBotMemberBindingMapper memberBindingMapper,
                                                       FbsEnterpriseMapper enterpriseMapper,
                                                       FbsEnterpriseMemberMapper enterpriseMemberMapper,
                                                       WecomInboundEventMapper inboundEventMapper,
                                                       OrchestrationRunMapper runMapper,
                                                       OrchestrationStepMapper stepMapper,
                                                       DeliveryOutboxMapper outboxMapper,
                                                       ExternalIdentityHasher identityHasher,
                                                       SmartBotInputArtifactService inputArtifactService,
                                                       ObjectMapper objectMapper) {
            return new SmartBotIngressService(botBindingMapper, memberBindingMapper,
                enterpriseMapper, enterpriseMemberMapper, inboundEventMapper, runMapper,
                stepMapper, outboxMapper, identityHasher, inputArtifactService, objectMapper);
        }

        @Bean
        OutboxClaimTransactionService outboxClaimTransactionService(
                DeliveryOutboxMapper outboxMapper) {
            return new OutboxClaimTransactionService(outboxMapper);
        }

        @Bean
        OutboxLeaseService outboxLeaseService(OutboxClaimTransactionService claimTransactionService,
                                              DeliveryOutboxMapper outboxMapper) {
            return new OutboxLeaseService(claimTransactionService, outboxMapper);
        }

        @Bean
        InternalRunActivationService internalRunActivationService(DeliveryOutboxMapper outboxMapper,
                                                                   OrchestrationRunMapper runMapper,
                                                                   OrchestrationStepMapper stepMapper,
                                                                   SmartBotInputArtifactService inputArtifactService,
                                                                   ObjectMapper objectMapper) {
            return new InternalRunActivationService(outboxMapper, runMapper, stepMapper,
                inputArtifactService, objectMapper);
        }

        @Bean
        InternalOutboxDispatcherService internalOutboxDispatcherService(
                OutboxLeaseService outboxLeaseService,
                InternalRunActivationService activationService) {
            return new InternalOutboxDispatcherService(outboxLeaseService, activationService);
        }

        private static <T> MapperFactoryBean<T> mapper(Class<T> type, SqlSessionFactory factory) {
            MapperFactoryBean<T> bean = new MapperFactoryBean<>(type);
            bean.setSqlSessionFactory(factory);
            return bean;
        }
    }
}
