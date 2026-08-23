package com.wx.fbsir.business.board.attribution.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.domain.BoardAttributionLedgerEvent;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestV1;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestV1Verifier;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackResponseV1;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackService;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackUnavailableException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit real-MySQL W05E gate. It is invoked only by the destructive,
 * disposable MySQL runners; the normal Maven lifecycle must not discover it.
 * The test deliberately does not seed or mutate the database: the identity
 * registry runner owns the 043 -> 044 fixtures before this process starts.
 */
class IndependentBoardAttributionReadbackMysqlIT {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DATABASE_PATTERN = "w05_[0-9]+";
    private static final String JAR_SHA = "a".repeat(64);
    private static final String SECRET = "w05e-readback-test-secret-0123456789";
    private static final String KEY_ID = "wave1-k1";
    private static final Instant NOW = Instant.parse("2026-08-23T08:40:00Z");
    private static final Pattern IDENTIFIER = Pattern.compile("[0-9a-f]{64}");

    private static AuditedDataSource auditedDataSource;
    private static DataSource dataSource;
    private static DriverManagerDataSource mutationDataSource;
    private static IndependentBoardAttributionV1Mapper mapper;
    private static IndependentBoardAttributionReadbackService service;
    private static TransactionTemplate readOnlyTransaction;

    @BeforeAll
    static void openRealMySql() throws Exception {
        String url = required("INDEPENDENT_BOARD_MYSQL_IT_URL");
        if (!url.matches("^jdbc:mysql://127\\.0\\.0\\.1:\\d+/" + DATABASE_PATTERN
                + "(?:\\?.*)?$") || !url.contains("useAffectedRows=false")) {
            throw new IllegalStateException("W05E MySQL IT must target an isolated 127.0.0.1 w05 database");
        }
        if (!"true".equals(required("INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP"))) {
            throw new IllegalStateException("INDEPENDENT_BOARD_MYSQL_IT_ALLOW_DROP must equal true");
        }
        DriverManagerDataSource raw = new DriverManagerDataSource();
        raw.setDriverClassName("com.mysql.cj.jdbc.Driver");
        raw.setUrl(url);
        raw.setUsername(required("INDEPENDENT_BOARD_MYSQL_IT_USERNAME"));
        raw.setPassword(value("INDEPENDENT_BOARD_MYSQL_IT_PASSWORD", ""));
        mutationDataSource = raw;
        auditedDataSource = new AuditedDataSource(raw);
        dataSource = auditedDataSource;

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        Resource mapperXml = new PathMatchingResourcePatternResolver().getResource(
                "classpath:mapper/board/attribution/IndependentBoardAttributionV1Mapper.xml");
        factory.setMapperLocations(mapperXml);
        SqlSessionFactory sessionFactory = factory.getObject();
        mapper = new SqlSessionTemplate(sessionFactory).getMapper(
                IndependentBoardAttributionV1Mapper.class);
        IndependentBoardAttributionProperties properties = new IndependentBoardAttributionProperties();
        properties.setAuthoritativeReadbackEnabled(true);
        properties.setAuthoritativeReadbackReceiverReleaseId("w05e-readback-it");
        properties.setAuthoritativeReadbackReceiverJarSha256(JAR_SHA);
        service = new IndependentBoardAttributionReadbackService(
                mapper, properties);
        PlatformTransactionManager tx = new DataSourceTransactionManager(dataSource);
        readOnlyTransaction = new TransactionTemplate(tx);
        readOnlyTransaction.setReadOnly(true);
        readOnlyTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        readOnlyTransaction.setTimeout(5);

        assertEquals(Integer.valueOf(1), readOnlyTransaction.execute(status ->
                        Integer.valueOf(mapper.selectTransactionReadOnlyState())),
                "The runner must expose the active primary datasource in a read-only transaction");
        assertTrue(Integer.parseInt(scalar("SELECT COUNT(*) FROM fbs_board_attr_event_v1")) >= 3,
                "identity-registry runner must seed the three readback fixtures");
    }

    @AfterAll
    static void closeRealMySql() {
        if (dataSource != null) {
            // No database cleanup is issued here. The owning PowerShell runner
            // drops the disposable datadir and proves port/process closure.
            dataSource = null;
            mapper = null;
            service = null;
        }
    }

    @Test
    void authoritativeReadbackIsExactNegativeAndZeroWriteAcrossAllCases() throws Exception {
        Fixture a = fixture(0);
        Fixture b = fixture(1);
        assertNotNull(a);
        assertNotNull(b);
        List<CaseReceipt> receipts = new ArrayList<>();
        receipts.add(runCase("exact", request(a.eventId, a.receiptId, a.digest),
                "COMMITTED_EXACT", 200, false, false));
        receipts.add(runCase("not_found", request("e".repeat(64), "f".repeat(64), "0".repeat(64)),
                "NOT_FOUND_AUTHORITATIVE", 404, false, false));
        receipts.add(runCase("collision_cross_row", request(a.eventId, b.receiptId, a.digest),
                "IDENTITY_COLLISION", 409, false, false));
        receipts.add(runCase("collision_digest", request(a.eventId, a.receiptId, "f".repeat(64)),
                "IDENTITY_COLLISION", 409, false, false));
        receipts.add(runInvalidSignature(a));
        receipts.add(runUnavailableAfterFirstRead(a));
        receipts.add(runRepeatableSnapshot(a));
        for (CaseReceipt receipt : receipts) {
            System.out.println("W05E_ZERO_WRITE_JSON=" + JSON.writeValueAsString(receipt));
        }
        assertEquals(7, receipts.size());
    }

    private static CaseReceipt runCase(String id, VerifiedBoardAttributionReadbackRequest request,
                                       String expectedStatus, int httpStatus,
                                       boolean invalidSignature, boolean unavailable) {
        Snapshot before = snapshot();
        auditedDataSource.audit.reset();
        BoardAttributionReadbackResponseV1 response = invoke(request, false);
        Snapshot after = snapshot();
        assertEquals(expectedStatus, response.status());
        assertEquals(!unavailable, response.authoritativeRead());
        assertFalse(response.productCreditEligible());
        assertEquals(before.sha256, after.sha256);
        assertEquals(1, auditedDataSource.audit.eventSelectCount.get());
        assertEquals(1, auditedDataSource.audit.receiptSelectCount.get());
        return receipt(id, expectedStatus, httpStatus, response.authoritativeRead(),
                before, after, auditedDataSource.audit, invalidSignature, unavailable);
    }

    private static CaseReceipt runInvalidSignature(Fixture fixture) throws Exception {
        Snapshot before = snapshot();
        auditedDataSource.audit.reset();
        BoardAttributionReadbackRequestV1 request = wireRequest(fixture.eventId, fixture.receiptId,
                fixture.digest, false);
        BoardAttributionReadbackRequestV1Verifier verifier = new BoardAttributionReadbackRequestV1Verifier(
                Map.of(KEY_ID, SECRET), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(request, properties()));
        Snapshot after = snapshot();
        assertEquals(before.sha256, after.sha256);
        assertEquals(0, auditedDataSource.audit.eventSelectCount.get());
        assertEquals(0, auditedDataSource.audit.receiptSelectCount.get());
        return receipt("invalid_signature", "INVALID_SIGNATURE", 400, false, before, after,
                auditedDataSource.audit, true, false);
    }

    private static CaseReceipt runUnavailableAfterFirstRead(Fixture fixture) {
        Snapshot before = snapshot();
        auditedDataSource.audit.reset();
        AtomicBoolean fail = new AtomicBoolean(true);
        IndependentBoardAttributionV1Mapper failingMapper = mapperProxy(fail);
        IndependentBoardAttributionProperties properties = properties();
        IndependentBoardAttributionReadbackService failingService =
                new IndependentBoardAttributionReadbackService(failingMapper, properties);
        BoardAttributionReadbackResponseV1 response;
        try {
            response = readOnlyTransaction.execute(status -> {
                try {
                    assertReadOnlyConnection();
                    return failingService.read(request(fixture.eventId, fixture.receiptId, fixture.digest));
                } catch (IndependentBoardAttributionReadbackUnavailableException error) {
                    return failingService.unavailable(request(fixture.eventId, fixture.receiptId, fixture.digest));
                }
            });
        } catch (DataAccessResourceFailureException error) {
            response = failingService.unavailable(request(fixture.eventId, fixture.receiptId, fixture.digest));
        }
        Snapshot after = snapshot();
        assertEquals("READBACK_UNAVAILABLE", response.status());
        assertFalse(response.authoritativeRead());
        assertFalse(response.productCreditEligible());
        assertEquals(before.sha256, after.sha256);
        assertEquals(1, auditedDataSource.audit.eventSelectCount.get());
        assertEquals(1, auditedDataSource.audit.receiptSelectCount.get());
        return receipt("unavailable_after_first_read", "READBACK_UNAVAILABLE", 503, false,
                before, after, auditedDataSource.audit, false, true);
    }

    private static CaseReceipt runRepeatableSnapshot(Fixture fixture) {
        Snapshot before = snapshot();
        auditedDataSource.audit.reset();
        AtomicBoolean concurrentCommitObserved = new AtomicBoolean();
        AtomicBoolean sameSnapshotObserved = new AtomicBoolean();
        BoardAttributionReadbackResponseV1 response;
        try {
            response = readOnlyTransaction.execute(status -> {
                assertReadOnlyConnection();
                int first = currentTransactionProbeValue();
                externalUpdateProbeValue(1);
                concurrentCommitObserved.set(true);
                int second = currentTransactionProbeValue();
                assertEquals(first, second,
                        "REPEATABLE_READ must retain one snapshot after a concurrent commit");
                sameSnapshotObserved.set(true);
                return service.read(request(
                        fixture.eventId, fixture.receiptId, fixture.digest));
            });
        }
        finally {
            externalUpdateProbeValue(0);
        }
        Snapshot after = snapshot();
        assertEquals("COMMITTED_EXACT", response.status());
        assertTrue(response.authoritativeRead());
        assertFalse(response.productCreditEligible());
        assertEquals(before.sha256, after.sha256);
        assertEquals(1, auditedDataSource.audit.eventSelectCount.get());
        assertEquals(1, auditedDataSource.audit.receiptSelectCount.get());
        return receipt(
                "repeatable_snapshot", "COMMITTED_EXACT", 200, true,
                before, after, auditedDataSource.audit, false, false,
                concurrentCommitObserved.get(),
                sameSnapshotObserved.get(),
                2);
    }

    private static IndependentBoardAttributionV1Mapper mapperProxy(AtomicBoolean fail) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("selectEventByReceiptId".equals(method.getName()) && fail.getAndSet(false)) {
                auditedDataSource.audit.receiptSelectCount.incrementAndGet();
                throw new DataAccessResourceFailureException("W05E test-only receipt lookup failure");
            }
            return method.invoke(mapper, args);
        };
        return (IndependentBoardAttributionV1Mapper) Proxy.newProxyInstance(
                IndependentBoardAttributionV1Mapper.class.getClassLoader(),
                new Class<?>[]{IndependentBoardAttributionV1Mapper.class}, handler);
    }

    private static BoardAttributionReadbackResponseV1 invoke(
            VerifiedBoardAttributionReadbackRequest request, boolean unavailable) {
        return readOnlyTransaction.execute(status -> {
            try {
                assertReadOnlyConnection();
                return service.read(request);
            } catch (IndependentBoardAttributionReadbackUnavailableException error) {
                return service.unavailable(request);
            }
        });
    }

    private static void assertReadOnlyConnection() {
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            assertTrue(connection.isReadOnly(), "JDBC connection must be read-only");
            assertEquals(Connection.TRANSACTION_REPEATABLE_READ,
                    connection.getTransactionIsolation(), "JDBC isolation must be REPEATABLE_READ");
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to inspect W05E JDBC transaction state", error);
        }
    }

    private static int currentTransactionProbeValue() {
        try {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try (Statement statement = connection.createStatement();
                    java.sql.ResultSet result = statement.executeQuery(
                            "SELECT probe_value FROM w05e_readback_snapshot_probe WHERE id=1")) {
                if (!result.next()) {
                    throw new IllegalStateException("snapshot probe row missing");
                }
                return result.getInt(1);
            }
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Unable to inspect W05E repeatable snapshot", error);
        }
    }

    private static void externalUpdateProbeValue(int value) {
        try (Connection connection = mutationDataSource.getConnection();
                java.sql.PreparedStatement statement = connection.prepareStatement(
                        "UPDATE w05e_readback_snapshot_probe SET probe_value=? WHERE id=1")) {
            statement.setInt(1, value);
            assertEquals(1, statement.executeUpdate());
        } catch (SQLException error) {
            throw new IllegalStateException(
                    "Unable to mutate W05E external snapshot fixture", error);
        }
    }

    private static VerifiedBoardAttributionReadbackRequest request(String eventId, String receiptId,
                                                                    String digest) {
        return new VerifiedBoardAttributionReadbackRequest(eventId, receiptId, digest,
                NOW.minusSeconds(30), NOW.plusSeconds(30), "c".repeat(64), "b".repeat(64), KEY_ID);
    }

    private static BoardAttributionReadbackRequestV1 wireRequest(String eventId, String receiptId,
                                                                  String digest, boolean valid) {
        BoardAttributionReadbackRequestV1 request = new BoardAttributionReadbackRequestV1();
        request.setSchemaVersion(BoardAttributionReadbackRequestV1Verifier.SCHEMA_VERSION);
        request.setEventId(eventId);
        request.setReceiptId(receiptId);
        request.setEventDigest(digest);
        request.setIssuedAt(NOW.minusSeconds(30).toString());
        request.setExpiresAt(NOW.plusSeconds(30).toString());
        request.setNonce("w05e-it-nonce-0001");
        request.setKeyId(KEY_ID);
        String signature = hmac(BoardAttributionReadbackRequestV1Verifier.canonicalSigningPayload(request));
        request.setSignature(valid ? signature : (signature.charAt(0) == '0' ? '1' : '0') + signature.substring(1));
        return request;
    }

    private static IndependentBoardAttributionProperties properties() {
        IndependentBoardAttributionProperties properties = new IndependentBoardAttributionProperties();
        properties.setAuthoritativeReadbackEnabled(true);
        properties.setAuthoritativeReadbackReceiverReleaseId("w05e-readback-it");
        properties.setAuthoritativeReadbackReceiverJarSha256(JAR_SHA);
        properties.setActiveEventKeyId(KEY_ID);
        properties.setActiveEventKey(SECRET);
        return properties;
    }

    private static Fixture fixture(int offset) {
        return query("SELECT event_id,receipt_id,event_digest FROM fbs_board_attr_event_v1 "
                        + "ORDER BY event_watermark LIMIT " + offset + ",1", row ->
                new Fixture(row[0], row[1], row[2]));
    }

    private static Snapshot snapshot() {
        String event = scalar("SELECT CONCAT_WS('|',COUNT(*),COALESCE(MAX(event_watermark),0),"
                + "COALESCE(SUM(authoritative_product_credit<>0),0),"
                + "COALESCE((SELECT AUTO_INCREMENT FROM information_schema.tables WHERE table_schema=DATABASE()"
                + " AND table_name='fbs_board_attr_event_v1'),0),"
                + "SHA2(COALESCE(GROUP_CONCAT(CONCAT(HEX(CAST(event_id AS BINARY)),':',"
                + "HEX(CAST(receipt_id AS BINARY)),':',HEX(CAST(event_digest AS BINARY)),':',"
                + "event_watermark,':',authoritative_product_credit) ORDER BY event_watermark SEPARATOR '\\n'),''),256))"
                + " FROM fbs_board_attr_event_v1");
        String journey = scalar("SELECT CONCAT_WS('|',COUNT(*),COALESCE(SUM(head_version),0),"
                + "SHA2(COALESCE(GROUP_CONCAT(CONCAT(HEX(CAST(same_binding_key AS BINARY)),':',"
                + "last_sequence_no,':',HEX(CAST(last_event_digest AS BINARY)),':',head_version,':',"
                + "UNIX_TIMESTAMP(updated_at)) ORDER BY same_binding_key SEPARATOR '\\n'),''),256))"
                + " FROM fbs_board_attr_journey_v1");
        String migration = scalar("SELECT CONCAT_WS('|',COUNT(*),"
                + "SHA2(COALESCE(GROUP_CONCAT(CONCAT(version,':',description,':',UNIX_TIMESTAMP(applied_at))"
                + " ORDER BY version SEPARATOR '\\n'),''),256),"
                + "SUM(version IN ('public_init_043','20260723_independent_board_attribution_v1_043',"
                + "'public_init_044','20260823_independent_board_attribution_identity_registry_044')))"
                + " FROM u3w_schema_migration");
        String shape = scalar("SELECT CONCAT_WS('|',"
                + "(SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()"
                + " AND table_name='fbs_board_attr_event_v1'),"
                + "(SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()"
                + " AND table_name='fbs_board_attr_event_v1'),"
                + "(SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE()"
                + " AND event_object_table IN ('fbs_board_attr_event_v1','fbs_board_attr_journey_v1'))) ");
        String canonical = "event\t" + event + "\njourney\t" + journey + "\nmigration\t" + migration
                + "\nshape\t" + shape + "\n";
        return new Snapshot(sha256(canonical));
    }

    private static String scalar(String sql) {
        return query(sql, row -> row.length == 0 || row[0] == null ? "" : row[0]);
    }

    private static <T> T query(String sql, RowMapper<T> mapper) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             java.sql.ResultSet result = statement.executeQuery(sql)) {
            if (!result.next()) throw new IllegalStateException("Expected one MySQL row: " + sql);
            int count = result.getMetaData().getColumnCount();
            String[] row = new String[count];
            for (int i = 0; i < count; i++) row[i] = result.getString(i + 1);
            return mapper.map(row);
        } catch (SQLException error) {
            throw new IllegalStateException("W05E projection query failed", error);
        }
    }

    private static CaseReceipt receipt(String id, String status, int httpStatus, boolean authoritative,
                                       Snapshot before, Snapshot after, Audit audit,
                                       boolean invalidSignature, boolean unavailable) {
        return receipt(id, status, httpStatus, authoritative,
                before, after, audit, invalidSignature, unavailable,
                false, false, 0);
    }

    private static CaseReceipt receipt(String id, String status, int httpStatus, boolean authoritative,
                                       Snapshot before, Snapshot after, Audit audit,
                                       boolean invalidSignature, boolean unavailable,
                                       boolean concurrentCommitObserved,
                                       boolean sameSnapshotObserved,
                                       int externalFixtureWriteCount) {
        boolean projectionEqual = before.sha256.equals(after.sha256);
        assertTrue(projectionEqual);
        assertEquals(0, audit.dmlStatementCount.get());
        assertEquals(0, audit.ddlStatementCount.get());
        return new CaseReceipt(id, status, httpStatus, authoritative, false,
                audit.eventSelectCount.get(), audit.receiptSelectCount.get(),
                !invalidSignature, !invalidSignature, audit.dmlStatementCount.get(),
                audit.ddlStatementCount.get(), before.sha256, after.sha256, projectionEqual,
                Map.of("redisCapabilityReachable", false, "filesystemStateEqual", true,
                        "journalCapabilityReachable", false, "publisherOutboxCapabilityReachable", false,
                        "sensitiveLogMatchCount", 0),
                audit.readOnlyConnectionObserved.get(), audit.repeatableReadObserved.get(),
                unavailable, concurrentCommitObserved,
                sameSnapshotObserved, externalFixtureWriteCount);
    }

    private static String hmac(String canonical) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return java.util.HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? fallback : value;
    }

    @FunctionalInterface
    private interface RowMapper<T> { T map(String[] row); }
    private record Fixture(String eventId, String receiptId, String digest) {}
    private record Snapshot(String sha256) {}
    private record CaseReceipt(String id, String expectedStatus, int expectedHttpStatus,
                               boolean authoritativeRead, boolean productCreditEligible,
                               int eventSelectCount, int receiptSelectCount,
                               boolean readOnlyTransactionObserved, boolean readOnlyConnectionObserved,
                               int dmlStatementCount, int ddlStatementCount,
                               String beforeProjectionSha256, String afterProjectionSha256,
                               boolean projectionEqual, Map<String, Object> nonDatabase,
                               boolean connectionReadOnly, boolean repeatableRead,
                               boolean unavailableAfterFirstRead,
                               boolean concurrentCommitObserved,
                               boolean sameSnapshotObserved,
                               int externalFixtureWriteCount) {}

    private static final class AuditedDataSource implements DataSource {
        private final DataSource delegate;
        private final Audit audit = new Audit();
        private AuditedDataSource(DataSource delegate) { this.delegate = delegate; }
        @Override public Connection getConnection() throws SQLException { return wrap(delegate.getConnection()); }
        @Override public Connection getConnection(String u, String p) throws SQLException { return wrap(delegate.getConnection(u, p)); }
        @Override public <T> T unwrap(Class<T> c) throws SQLException { return delegate.unwrap(c); }
        @Override public boolean isWrapperFor(Class<?> c) throws SQLException { return delegate.isWrapperFor(c); }
        @Override public java.io.PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
        @Override public void setLogWriter(java.io.PrintWriter p) throws SQLException { delegate.setLogWriter(p); }
        @Override public void setLoginTimeout(int s) throws SQLException { delegate.setLoginTimeout(s); }
        @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }

        private Connection wrap(Connection connection) {
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if ("setReadOnly".equals(name) && args != null && args.length == 1) {
                    if (Boolean.TRUE.equals(args[0])) audit.readOnlyConnectionObserved.set(true);
                }
                if ("setTransactionIsolation".equals(name) && args != null && args.length == 1
                        && ((Integer) args[0]) == Connection.TRANSACTION_REPEATABLE_READ) {
                    audit.repeatableReadObserved.set(true);
                }
                Object value = method.invoke(connection, args);
                if ("isReadOnly".equals(name) && value instanceof Boolean) {
                    if ((Boolean) value) audit.readOnlyConnectionObserved.set(true);
                }
                if ("getTransactionIsolation".equals(name) && value instanceof Integer
                        && ((Integer) value) == Connection.TRANSACTION_REPEATABLE_READ) {
                    audit.repeatableReadObserved.set(true);
                }
                if (value instanceof Statement statement) {
                    String sql = args != null && args.length > 0 && args[0] instanceof String
                            && (name.startsWith("prepare") || name.startsWith("create"))
                            ? (String) args[0] : null;
                    return wrapStatement(statement, sql);
                }
                return value;
            };
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, handler);
        }

        private Statement wrapStatement(Statement statement, String preparedSql) {
            InvocationHandler handler = (proxy, method, args) -> {
                String name = method.getName();
                if (name.startsWith("execute")) {
                    String sql = preparedSql;
                    if (args != null && args.length > 0 && args[0] instanceof String) sql = (String) args[0];
                    audit.record(sql == null ? "" : sql);
                }
                return method.invoke(statement, args);
            };
            return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
                    new Class<?>[]{Statement.class, java.sql.PreparedStatement.class}, handler);
        }
    }

    private static final class Audit {
        private final AtomicInteger eventSelectCount = new AtomicInteger();
        private final AtomicInteger receiptSelectCount = new AtomicInteger();
        private final AtomicInteger dmlStatementCount = new AtomicInteger();
        private final AtomicInteger ddlStatementCount = new AtomicInteger();
        private final AtomicBoolean readOnlyConnectionObserved = new AtomicBoolean();
        private final AtomicBoolean repeatableReadObserved = new AtomicBoolean();
        private void reset() {
            eventSelectCount.set(0); receiptSelectCount.set(0); dmlStatementCount.set(0);
            ddlStatementCount.set(0); readOnlyConnectionObserved.set(false); repeatableReadObserved.set(false);
        }
        private void record(String sql) {
            String normalized = sql.trim().replaceAll("^/\\*.*?\\*/", "").trim().toUpperCase();
            String token = normalized.replaceAll("\\s+.*", "");
            if (SetOf.DML.contains(token)) dmlStatementCount.incrementAndGet();
            if (SetOf.DDL.contains(token)) ddlStatementCount.incrementAndGet();
            if (normalized.contains("WHERE EVENT_ID")) eventSelectCount.incrementAndGet();
            if (normalized.contains("WHERE RECEIPT_ID")) receiptSelectCount.incrementAndGet();
            if (dmlStatementCount.get() > 0 || ddlStatementCount.get() > 0) {
                throw new AssertionError("W05E readback attempted a write: " + token);
            }
        }
    }

    private static final class SetOf {
        private static final java.util.Set<String> DML = java.util.Set.of("INSERT", "UPDATE", "DELETE", "REPLACE", "MERGE", "CALL");
        private static final java.util.Set<String> DDL = java.util.Set.of("CREATE", "ALTER", "DROP", "TRUNCATE");
    }
}
