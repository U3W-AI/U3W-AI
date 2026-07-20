package com.wx.fbsir.business.board.integration;

import com.wx.fbsir.business.board.dto.BoardEntitlementAdminView;
import com.wx.fbsir.business.board.dto.BoardEntitlementGrantRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationRequest;
import com.wx.fbsir.business.board.dto.BoardMeetingReservationView;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final long TENANT_ONE = 1001L;
    private static final long MEMBER_ONE = 101L;
    private static final long USER_ONE = 501L;
    private static final long TENANT_TWO = 1002L;
    private static final long MEMBER_TWO = 102L;
    private static final long USER_TWO = 502L;

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static IndependentBoardEntitlementService entitlementService;
    private static IndependentBoardMeetingService meetingService;

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

        assertTrue(AopUtils.isAopProxy(entitlementService),
                "entitlement service must be a Spring transaction proxy");
        assertTrue(AopUtils.isAopProxy(meetingService),
                "meeting service must be a Spring transaction proxy");
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
                "INSERT INTO fbs_enterprise_member "
                        + "(id, enterprise_id, user_id, role, status, del_flag) VALUES "
                        + "(" + MEMBER_ONE + ", " + TENANT_ONE + ", " + USER_ONE
                        + ", 'MEMBER', 1, '0'), "
                        + "(" + MEMBER_TWO + ", " + TENANT_TWO + ", " + USER_TWO
                        + ", 'MEMBER', 1, '0')"
        );
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

    private static BoardMeetingReservationRequest request(
            long tenantId, String operationId, int agendaCount, int seatCount) {
        return new BoardMeetingReservationRequest(tenantId, operationId, agendaCount, seatCount);
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
                "DROP TABLE IF EXISTS fbs_enterprise_member",
                "CREATE TABLE IF NOT EXISTS u3w_schema_migration ("
                        + "version VARCHAR(96) NOT NULL, "
                        + "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                        + "description VARCHAR(255) NOT NULL, PRIMARY KEY (version)) ENGINE=InnoDB",
                "DELETE FROM u3w_schema_migration WHERE version = '" + MIGRATION_VERSION + "'",
                "CREATE TABLE fbs_enterprise_member ("
                        + "id BIGINT UNSIGNED NOT NULL PRIMARY KEY, "
                        + "enterprise_id BIGINT UNSIGNED NOT NULL, "
                        + "user_id BIGINT UNSIGNED NOT NULL, role VARCHAR(32), "
                        + "status TINYINT NOT NULL, del_flag CHAR(1) NOT NULL, "
                        + "UNIQUE KEY uk_board_it_member (enterprise_id, user_id)) ENGINE=InnoDB"
        );
        executeMigration(locateMigration("update_20260720_independent_board_control_plane.sql"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version = '" + MIGRATION_VERSION + "'"));
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
