package com.wx.fbsir.business.board.credit.integration;

import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditEnvelope;
import com.wx.fbsir.business.board.credit.dto.BoardCreditCommandResult;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
import com.wx.fbsir.business.board.credit.service.IndependentBoardCreditService;
import com.wx.fbsir.business.board.credit.service.IndependentBoardCreditTransactionService;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit destructive integration coverage for the W3f shadow ledger.
 *
 * <p>The dedicated runner owns the disposable MySQL process and applies the exact migration
 * bytes before this suite starts. This suite never points at a conventional port or database.</p>
 */
@SpringJUnitConfig(IndependentBoardCreditLedgerMysqlIT.TestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IndependentBoardCreditLedgerMysqlIT {
    private static final String ALLOW_PROPERTY =
            "independent.board.credit.mysql.it.allowDestructive";
    private static final String URL_PROPERTY = "independent.board.credit.mysql.it.url";
    private static final String USERNAME_PROPERTY =
            "independent.board.credit.mysql.it.username";
    private static final String PASSWORD_PROPERTY =
            "independent.board.credit.mysql.it.password";
    private static final long ACTOR_USER_ID = 9_001L;
    private static final AtomicLong USER_IDS = new AtomicLong(70_000L);

    @org.springframework.beans.factory.annotation.Autowired
    private IndependentBoardCreditService creditService;

    @org.springframework.beans.factory.annotation.Autowired
    private DataSource dataSource;

    @BeforeAll
    void verifyDisposableRuntimeContract() throws SQLException {
        assertEquals("true", required(ALLOW_PROPERTY),
                "The explicit destructive-test consent property is required");
        String configuredUrl = required(URL_PROPERTY);
        assertTrue(configuredUrl.matches(
                        "jdbc:mysql://127\\.0\\.0\\.1:(?!3306(?:/|$))[0-9]{4,5}/"
                                + "u3w_independent_board_credit_it_[0-9a-f]{8}\\?.+"),
                "The credit IT must use a uniquely named loopback database away from port 3306");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT VERSION(), DATABASE(), @@default_storage_engine, "
                             + "@@transaction_isolation")) {
            assertTrue(result.next());
            String version = result.getString(1);
            assertTrue(version.equals("8.0.30") || version.equals("8.4.8"),
                    "Only the locked dual MySQL runtime matrix is accepted");
            assertTrue(result.getString(2).matches(
                    "u3w_independent_board_credit_it_[0-9a-f]{8}"));
            assertEquals("InnoDB", result.getString(3));
            assertEquals("REPEATABLE-READ", result.getString(4));

            DatabaseMetaData metadata = connection.getMetaData();
            System.out.printf(
                    "credit-ledger-mysql-it runtime=%s connector=%s %s database=%s%n",
                    version,
                    metadata.getDriverName(),
                    metadata.getDriverVersion(),
                    result.getString(2));
        }

        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM u3w_schema_migration "
                        + "WHERE version = '20260722_independent_board_credit_ledger_v1' "
                        + "AND description = 'Independent Board USER_GLOBAL FBS_POINTS "
                        + "immutable shadow ledger'"));
        assertEquals(3L, scalarLong(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "AND table_name IN ('fbs_credit_account','fbs_credit_operation',"
                        + "'fbs_credit_entry')"));
        assertEquals(6L, scalarLong(
                "SELECT COUNT(*) FROM information_schema.triggers "
                        + "WHERE trigger_schema = DATABASE() "
                        + "AND event_object_table IN ('fbs_credit_account',"
                        + "'fbs_credit_operation','fbs_credit_entry')"));
    }

    @Test
    void grantReversalAndAuditCommitOneContiguousChain() throws SQLException {
        long userId = nextUser(100);

        BoardCreditCommandResult grant = creditService.grant(
                grant(userId, 25, "grant.mysql.normal.0001"), ACTOR_USER_ID);
        BoardCreditCommandResult reversal = creditService.reverse(
                reversal(grant.operationId(), "reverse.mysql.normal.01"), ACTOR_USER_ID);
        BoardCreditAuditEnvelope audit = creditService.audit(userId);

        assertEquals("GRANT", grant.operationType());
        assertEquals(125L, grant.balanceAfter());
        assertEquals("REVERSAL", reversal.operationType());
        assertEquals(-25L, reversal.delta());
        assertEquals(grant.operationId(), reversal.reversalOfOperationId());
        assertEquals(100L, reversal.balanceAfter());
        assertEquals(100L, audit.openingBalance());
        assertEquals(100L, audit.balance());
        assertEquals(2L, audit.version());
        assertEquals(2, audit.records().size());
        assertEquals(reversal.operationId(), audit.records().get(0).operationId());
        assertEquals(grant.operationId(), audit.records().get(1).operationId());
        assertEquals(100L, userPoints(userId));
        assertEquals(2L, rowsForUser("fbs_credit_operation", userId));
        assertEquals(2L, rowsForUser("fbs_credit_entry", userId));
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM fbs_credit_entry newer "
                        + "INNER JOIN fbs_credit_entry older "
                        + "ON older.account_id = newer.account_id "
                        + "AND older.sequence_no = newer.sequence_no - 1 "
                        + "WHERE newer.account_id = ? AND newer.sequence_no = 2 "
                        + "AND BINARY newer.previous_entry_hash = BINARY older.entry_hash "
                        + "AND newer.balance_before = older.balance_after",
                audit.accountId()));
    }

    @Test
    void concurrentSameKeyGrantCommitsOnceAndBothCallersReplayTheWinner() throws Exception {
        long userId = nextUser(50);
        BoardCreditGrantRequest command = grant(userId, 30, "grant.mysql.same-key.01");

        List<Attempt> attempts = race(
                () -> creditService.grant(command, ACTOR_USER_ID),
                () -> creditService.grant(command, ACTOR_USER_ID));

        assertTrue(attempts.stream().allMatch(Attempt::succeeded));
        assertEquals(attempts.get(0).result().operationId(),
                attempts.get(1).result().operationId());
        assertEquals(80L, attempts.get(0).result().balanceAfter());
        assertEquals(80L, userPoints(userId));
        assertEquals(1L, rowsForUser("fbs_credit_account", userId));
        assertEquals(1L, rowsForUser("fbs_credit_operation", userId));
        assertEquals(1L, rowsForUser("fbs_credit_entry", userId));
        assertEquals(1L, scalarLong(
                "SELECT version FROM fbs_credit_account WHERE user_id = ?", userId));
    }

    @Test
    void distinctIdempotencyKeysCanReverseTheSameGrantOnlyOnce() throws Exception {
        long userId = nextUser(100);
        BoardCreditCommandResult grant = creditService.grant(
                grant(userId, 40, "grant.mysql.reverse-race"), ACTOR_USER_ID);
        BoardCreditReversalRequest first = reversal(
                grant.operationId(), "reverse.mysql.race.0001");
        BoardCreditReversalRequest second = reversal(
                grant.operationId(), "reverse.mysql.race.0002");

        List<Attempt> attempts = race(
                () -> creditService.reverse(first, ACTOR_USER_ID),
                () -> creditService.reverse(second, ACTOR_USER_ID));

        assertEquals(1L, attempts.stream().filter(Attempt::succeeded).count());
        assertEquals(1L, attempts.stream().filter(attempt -> !attempt.succeeded()).count());
        Attempt rejected = attempts.stream().filter(attempt -> !attempt.succeeded())
                .findFirst().orElseThrow();
        assertEquals("CREDIT_ACCOUNT_VERSION_CONFLICT", rejected.errorCode());
        assertEquals(409, rejected.statusCode());
        assertEquals(100L, userPoints(userId));
        assertEquals(2L, rowsForUser("fbs_credit_operation", userId));
        assertEquals(2L, rowsForUser("fbs_credit_entry", userId));
        assertEquals(1L, scalarLong(
                "SELECT COUNT(*) FROM fbs_credit_operation "
                        + "WHERE reversal_of_operation_id = ?", grant.operationId()));
    }

    @Test
    void failureAfterAccountCasRollsBackAccountOperationEntryAndProjection() throws SQLException {
        long userId = nextUser(100);
        String triggerName = "credit_it_fail_projection";
        execute("DROP TRIGGER IF EXISTS " + triggerName);
        execute("CREATE TRIGGER " + triggerName + " BEFORE UPDATE ON sys_user "
                + "FOR EACH ROW BEGIN "
                + "IF OLD.user_id = " + userId + " AND NOT (NEW.points <=> OLD.points) THEN "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'credit it projection failure'; "
                + "END IF; END");
        try {
            ServiceException failure = assertThrows(ServiceException.class,
                    () -> creditService.grant(
                            grant(userId, 15, "grant.mysql.rollback.01"), ACTOR_USER_ID));
            assertEquals("CREDIT_LEDGER_PERSISTENCE_FAILED", failure.getMessage());
        } finally {
            execute("DROP TRIGGER IF EXISTS " + triggerName);
        }

        assertEquals(100L, userPoints(userId));
        assertEquals(0L, rowsForUser("fbs_credit_account", userId));
        assertEquals(0L, rowsForUser("fbs_credit_operation", userId));
        assertEquals(0L, rowsForUser("fbs_credit_entry", userId));
    }

    @Test
    void immutableTriggersAndSameAccountLineageConstraintFailClosed() throws SQLException {
        long firstUserId = nextUser(100);
        long secondUserId = nextUser(100);
        BoardCreditCommandResult firstGrant = creditService.grant(
                grant(firstUserId, 10, "grant.mysql.immutable.1"), ACTOR_USER_ID);
        BoardCreditCommandResult secondGrant = creditService.grant(
                grant(secondUserId, 10, "grant.mysql.immutable.2"), ACTOR_USER_ID);
        String firstAccountId = scalarString(
                "SELECT account_id FROM fbs_credit_account WHERE user_id = ?", firstUserId);

        assertSqlRejected("Credit operations are immutable",
                "UPDATE fbs_credit_operation SET reason_note = 'mutated audit note' "
                        + "WHERE operation_id = ?", firstGrant.operationId());
        assertSqlRejected("Credit operations are immutable",
                "DELETE FROM fbs_credit_operation WHERE operation_id = ?",
                firstGrant.operationId());
        assertSqlRejected("Credit entries are immutable",
                "UPDATE fbs_credit_entry SET entry_hash = REPEAT('f', 64) "
                        + "WHERE operation_id = ?", firstGrant.operationId());
        assertSqlRejected("Credit entries are immutable",
                "DELETE FROM fbs_credit_entry WHERE operation_id = ?", firstGrant.operationId());
        assertSqlRejected("Credit accounts cannot be deleted",
                "DELETE FROM fbs_credit_account WHERE account_id = ?", firstAccountId);
        assertSqlRejected("Credit account transition contract violated",
                "UPDATE fbs_credit_account SET balance = balance + 1, "
                        + "version = version + 1, last_entry_sequence = last_entry_sequence + 1, "
                        + "last_entry_hash = REPEAT('f', 64) WHERE account_id = ?",
                firstAccountId);

        SQLException crossAccount = assertThrows(SQLException.class, () -> execute(
                "INSERT INTO fbs_credit_operation "
                        + "(operation_id,idempotency_key,request_digest,account_id,user_id,"
                        + "account_scope,currency_code,operation_type,delta_amount,reason_code,"
                        + "reason_note,actor_user_id,reversal_of_operation_id,balance_before,"
                        + "balance_after,created_at) "
                        + "SELECT ?, ?, REPEAT('a',64), account_id,user_id,account_scope,"
                        + "currency_code,'REVERSAL',-1,'OPERATOR_ERROR','cross account blocked',"
                        + "?, ?, balance_after,balance_after - 1,NOW(3) "
                        + "FROM fbs_credit_operation WHERE operation_id = ?",
                UUID.randomUUID().toString(),
                "cross-account-key-0001",
                ACTOR_USER_ID,
                firstGrant.operationId(),
                secondGrant.operationId()));
        assertTrue(crossAccount.getMessage().contains("fk_credit_operation_reversal"),
                () -> "Expected same-account reversal FK rejection, got: "
                        + crossAccount.getMessage());
        assertEquals(2L, scalarLong("SELECT COUNT(*) FROM fbs_credit_operation "
                + "WHERE user_id IN (?, ?)", firstUserId, secondUserId));
    }

    private long nextUser(int openingPoints) throws SQLException {
        long userId = USER_IDS.incrementAndGet();
        execute("INSERT INTO sys_user (user_id,points,status,del_flag,update_time) "
                        + "VALUES (?,?,'0','0',NOW(3))",
                userId, openingPoints);
        return userId;
    }

    private static BoardCreditGrantRequest grant(
            long userId, int amount, String idempotencyKey) {
        return new BoardCreditGrantRequest(
                userId,
                0L,
                amount,
                "CUSTOMER_SUPPORT",
                "controlled mysql integration grant",
                idempotencyKey);
    }

    private static BoardCreditReversalRequest reversal(
            String originalOperationId, String idempotencyKey) {
        return new BoardCreditReversalRequest(
                originalOperationId,
                1L,
                "OPERATOR_ERROR",
                "controlled mysql integration reversal",
                idempotencyKey);
    }

    private List<Attempt> race(
            Supplier<BoardCreditCommandResult> first,
            Supplier<BoardCreditCommandResult> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Attempt> firstFuture = executor.submit(
                    () -> coordinatedAttempt(ready, start, first));
            Future<Attempt> secondFuture = executor.submit(
                    () -> coordinatedAttempt(ready, start, second));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            return List.of(
                    firstFuture.get(30, TimeUnit.SECONDS),
                    secondFuture.get(30, TimeUnit.SECONDS));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static Attempt coordinatedAttempt(
            CountDownLatch ready,
            CountDownLatch start,
            Supplier<BoardCreditCommandResult> action) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            return Attempt.succeeded(action.get());
        } catch (ServiceException rejected) {
            return Attempt.rejected(rejected.getMessage(), rejected.getCode());
        }
    }

    private long rowsForUser(String table, long userId) throws SQLException {
        assertTrue(table.equals("fbs_credit_account")
                || table.equals("fbs_credit_operation")
                || table.equals("fbs_credit_entry"));
        if (table.equals("fbs_credit_entry")) {
            return scalarLong(
                    "SELECT COUNT(*) FROM fbs_credit_entry e "
                            + "INNER JOIN fbs_credit_operation o "
                            + "ON o.operation_id = e.operation_id WHERE o.user_id = ?",
                    userId);
        }
        return scalarLong("SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", userId);
    }

    private long userPoints(long userId) throws SQLException {
        return scalarLong("SELECT COALESCE(points,0) FROM sys_user WHERE user_id = ?", userId);
    }

    private void assertSqlRejected(String expectedMessage, String sql, Object... arguments) {
        SQLException failure = assertThrows(SQLException.class, () -> execute(sql, arguments));
        assertTrue(failure.getMessage().contains(expectedMessage),
                () -> "Expected rejection '" + expectedMessage + "', got: "
                        + failure.getMessage());
    }

    private void execute(String sql, Object... arguments) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            statement.execute();
        }
    }

    private long scalarLong(String sql, Object... arguments) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getLong(1);
            }
        }
    }

    private String scalarString(String sql, Object... arguments) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, arguments);
            try (ResultSet result = statement.executeQuery()) {
                assertTrue(result.next());
                String value = result.getString(1);
                assertNotNull(value);
                return value;
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... arguments)
            throws SQLException {
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the explicit credit MySQL IT");
        }
        return value;
    }

    private record Attempt(
            BoardCreditCommandResult result, String errorCode, Integer statusCode) {
        static Attempt succeeded(BoardCreditCommandResult result) {
            return new Attempt(result, null, null);
        }

        static Attempt rejected(String errorCode, Integer statusCode) {
            return new Attempt(null, errorCode, statusCode);
        }

        boolean succeeded() {
            return result != null && errorCode == null && statusCode == null;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource source = new DriverManagerDataSource();
            source.setDriverClassName("com.mysql.cj.jdbc.Driver");
            source.setUrl(required(URL_PROPERTY));
            source.setUsername(required(USERNAME_PROPERTY));
            source.setPassword(System.getProperty(PASSWORD_PROPERTY, ""));
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
                    .getResources("classpath*:mapper/board/IndependentBoardCreditMapper.xml"));
            return factory.getObject();
        }

        @Bean
        IndependentBoardCreditMapper creditMapper(SqlSessionFactory sqlSessionFactory) {
            return new SqlSessionTemplate(sqlSessionFactory)
                    .getMapper(IndependentBoardCreditMapper.class);
        }

        @Bean
        IndependentBoardCreditTransactionService creditTransactionService(
                IndependentBoardCreditMapper mapper) {
            return new IndependentBoardCreditTransactionService(mapper);
        }

        @Bean
        IndependentBoardCreditService creditService(
                IndependentBoardCreditTransactionService transactionService) {
            return new IndependentBoardCreditService(transactionService);
        }
    }
}
