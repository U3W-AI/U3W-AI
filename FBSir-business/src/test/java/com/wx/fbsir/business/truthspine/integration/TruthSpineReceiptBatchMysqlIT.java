package com.wx.fbsir.business.truthspine.integration;

import com.wx.fbsir.business.truthspine.mapper.TruthSpineReceiptBatchMapper;
import com.wx.fbsir.business.truthspine.service.TruthSpineReceiptBatchPersistenceService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit real-MySQL contract test. It is intentionally not discovered by the default Maven
 * lifecycle. Supply a disposable loopback database and explicit destructive-test consent.
 */
class TruthSpineReceiptBatchMysqlIT {
    private static final String DATABASE = "u3w_truth_spine_it";
    private static final String MIGRATION_VERSION = "20260712_truth_spine_test_state_v1";
    private static DataSource dataSource;
    private static String jdbcUrl;

    @BeforeAll
    static void applyTruthSpineMigration() throws Exception {
        jdbcUrl = required("TRUTH_SPINE_MYSQL_IT_URL");
        if (!"true".equals(required("TRUTH_SPINE_MYSQL_IT_ALLOW_DROP"))) {
            throw new IllegalStateException("TRUTH_SPINE_MYSQL_IT_ALLOW_DROP must equal true");
        }
        if (!jdbcUrl.matches("^jdbc:mysql://127\\.0\\.0\\.1:\\d+/" + DATABASE + "(?:\\?.*)?$")
                || !hasExactlyOneBooleanParameter(jdbcUrl, "useAffectedRows", "false")) {
            throw new IllegalStateException("MySQL IT URL must target the dedicated 127.0.0.1 database with useAffectedRows=false");
        }
        dataSource = newDataSource(jdbcUrl);
        execute("DROP TABLE IF EXISTS fbs_truth_spine_receipt_batch");
        execute("CREATE TABLE IF NOT EXISTS u3w_schema_migration ("
            + "version VARCHAR(96) NOT NULL, applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "description VARCHAR(255) NOT NULL, PRIMARY KEY (version)) ENGINE=InnoDB");
        execute("DELETE FROM u3w_schema_migration WHERE version = '" + MIGRATION_VERSION + "'");
        executeMigration(locateMigration("update_20260712_truth_spine_test_state_receipt.sql"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM u3w_schema_migration WHERE version = '" + MIGRATION_VERSION + "'"));
    }

    @AfterAll
    static void cleanDedicatedDatabase() throws Exception {
        if (dataSource != null) {
            execute("DROP TABLE IF EXISTS fbs_truth_spine_receipt_batch",
                "DELETE FROM u3w_schema_migration WHERE version = '" + MIGRATION_VERSION + "'");
        }
    }

    @BeforeEach
    void clearLedger() throws Exception {
        execute("DELETE FROM fbs_truth_spine_receipt_batch");
    }

    @Test
    void concurrentSameEvidencePersistsExactlyOnceAndNeverGainsCredit() throws Exception {
        Persistence persistence = persistenceFor(jdbcUrl);
        TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch command = command("batch-1", "idem-1", hash('d'));
        ExecutorService executor = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(32);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<TruthSpineReceiptBatchPersistenceService.PersistResult>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return persistence.persist(command);
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            for (Future<TruthSpineReceiptBatchPersistenceService.PersistResult> future : futures) {
                TruthSpineReceiptBatchPersistenceService.PersistResult result = future.get(20, TimeUnit.SECONDS);
                assertEquals("batch-1", result.batchId());
                assertFalse(result.productCreditEligible());
                assertFalse(result.businessClosureEligible());
            }
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_truth_spine_receipt_batch"));
        assertEquals(0, scalarInt("SELECT product_credit_eligible + business_closure_eligible FROM fbs_truth_spine_receipt_batch"));
    }

    @Test
    void affectedRowsTrueStillRejectsConcurrentEvidenceCollision() throws Exception {
        // The approved base URL is false; this second, local-only data source exercises the
        // opposite JDBC affected-row reporting mode without broadening the destructive-test gate.
        Persistence persistence = persistenceFor(jdbcUrl.replace("useAffectedRows=false", "useAffectedRows=true"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = List.of(
                executor.submit(() -> persistCollisionCandidate(persistence, command("batch-1", "idem-1", hash('d')), ready, start)),
                executor.submit(() -> persistCollisionCandidate(persistence, command("batch-1", "idem-1", hash('e')), ready, start))
            );
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> future : futures) {
                if (future.get(20, TimeUnit.SECONDS)) accepted++;
            }
            assertEquals(1, accepted);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_truth_spine_receipt_batch"));
    }

    @Test
    void databaseCheckConstraintsRejectAnyCreditPromotion() {
        Persistence persistence = persistenceFor(jdbcUrl);
        persistence.persist(command("batch-1", "idem-1", hash('d')));

        assertThrows(SQLException.class, () -> execute(
            "UPDATE fbs_truth_spine_receipt_batch SET product_credit_eligible = 1 WHERE batch_id = 'batch-1'"));
        assertThrows(SQLException.class, () -> execute(
            "UPDATE fbs_truth_spine_receipt_batch SET business_closure_eligible = 1 WHERE batch_id = 'batch-1'"));
    }

    private static boolean persistCollisionCandidate(Persistence persistence,
                                                      TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch command,
                                                      CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            persistence.persist(command);
            return true;
        } catch (IllegalStateException expected) {
            return false;
        }
    }

    private static Persistence persistenceFor(String url) {
        DataSource source = newDataSource(url);
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource mapperXml = resolver.getResource("classpath:mapper/truthspine/TruthSpineReceiptBatchMapper.xml");
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setMapperLocations(mapperXml);
            SqlSessionFactory sqlSessionFactory = factory.getObject();
            SqlSessionTemplate sessionTemplate = new SqlSessionTemplate(sqlSessionFactory);
            TruthSpineReceiptBatchMapper mapper = sessionTemplate.getMapper(TruthSpineReceiptBatchMapper.class);
            return new Persistence(new TransactionTemplate(new DataSourceTransactionManager(source)),
                new TruthSpineReceiptBatchPersistenceService(mapper));
        } catch (Exception e) {
            throw new IllegalStateException("Truth Spine MySQL integration mapper setup failed", e);
        }
    }

    private static TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch command(
            String batchId, String idempotencyKey, String payloadSha256) {
        return new TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch(
            batchId, "api2-test-workload-1", idempotencyKey, "invocation-1", "fbsir-super-partner-group",
            hash('b'), hash('c'), "host-test-key-1", payloadSha256, false, false, new Date());
    }

    private static DataSource newDataSource(String url) {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName("com.mysql.cj.jdbc.Driver");
        source.setUrl(url);
        source.setUsername(required("TRUTH_SPINE_MYSQL_IT_USERNAME"));
        String password = value("TRUTH_SPINE_MYSQL_IT_PASSWORD");
        source.setPassword(password == null ? "" : password);
        return source;
    }

    private static void executeMigration(Path path) throws Exception {
        String delimiter = ";";
        StringBuilder statement = new StringBuilder();
        try (Connection connection = dataSource.getConnection(); Statement jdbc = connection.createStatement()) {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("DELIMITER ")) {
                    delimiter = trimmed.substring("DELIMITER ".length()).trim();
                    continue;
                }
                if (trimmed.startsWith("--") || trimmed.isEmpty()) continue;
                statement.append(line).append('\n');
                if (trimmed.endsWith(delimiter)) {
                    int end = statement.lastIndexOf(delimiter);
                    jdbc.execute(statement.substring(0, end));
                    statement.setLength(0);
                }
            }
        }
        if (!statement.isEmpty()) throw new IllegalStateException("Truth Spine migration parser left an incomplete statement");
    }

    private static Path locateMigration(String name) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve("sql").resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IllegalStateException("Truth Spine migration not found: " + name);
    }

    private static void execute(String... statements) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String sql : statements) statement.execute(sql);
        }
    }

    private static int scalarInt(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static boolean hasExactlyOneBooleanParameter(String url, String key, String expectedValue) {
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) return false;
        int matches = 0;
        for (String part : url.substring(queryStart + 1).split("&", -1)) {
            int equals = part.indexOf('=');
            if (equals <= 0 || !key.equals(part.substring(0, equals))) continue;
            if (!expectedValue.equals(part.substring(equals + 1))) return false;
            matches++;
        }
        return matches == 1;
    }

    private static String required(String name) {
        String value = value(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required for the explicit MySQL IT");
        return value;
    }

    private static String value(String name) {
        String system = System.getProperty(name);
        return system != null ? system : System.getenv(name);
    }

    private record Persistence(TransactionTemplate transactionTemplate,
                               TruthSpineReceiptBatchPersistenceService service) {
        TruthSpineReceiptBatchPersistenceService.PersistResult persist(
                TruthSpineReceiptBatchPersistenceService.VerifiedTestStateBatch command) {
            return transactionTemplate.execute(status -> service.persist(command));
        }
    }
}
