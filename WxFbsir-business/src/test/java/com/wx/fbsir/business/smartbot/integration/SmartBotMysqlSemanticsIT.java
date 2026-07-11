package com.wx.fbsir.business.smartbot.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotInboundEnvelope;
import com.wx.fbsir.business.smartbot.dto.SmartBotIngressResult;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotMemberBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomInboundEventMapper;
import com.wx.fbsir.business.smartbot.service.ExternalIdentityHasher;
import com.wx.fbsir.business.smartbot.service.SmartBotIngressService;
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

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private static final String PAYLOAD_HASH = "a".repeat(64);
    private static final String HMAC_KEY_BASE64 =
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static AnnotationConfigApplicationContext context;
    private static DataSource dataSource;
    private static SmartBotIngressService ingressService;
    private static ExternalIdentityHasher identityHasher;

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
        identityHasher = context.getBean(ExternalIdentityHasher.class);

        assertDedicatedDatabase();
        createFbsIdentityTables();
        applyCurrentMigration();
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
            "DELETE FROM fbs_delivery_outbox",
            "DELETE FROM fbs_orchestration_receipt",
            "DELETE FROM fbs_orchestration_step",
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
        SmartBotIngressResult first = ingressService.accept(binding(7L, "AIBOT-01"), envelope());
        SmartBotIngressResult duplicate = ingressService.accept(binding(7L, "AIBOT-01"), envelope());

        assertTrue(first.firstDelivery());
        assertFalse(duplicate.firstDelivery());
        assertTrue(first.inboundEventId() > 0);
        assertEquals(first.inboundEventId(), duplicate.inboundEventId());
        assertEquals(first.traceId(), duplicate.traceId());
        assertEquals(first.runId(), duplicate.runId());
        assertEquals(first.streamId(), duplicate.streamId());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
        assertEquals(1, scalarInt("SELECT duplicate_count FROM fbs_inbound_event"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox"));
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
                    return ingressService.accept(binding(7L, "AIBOT-01"), envelope());
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
            () -> ingressService.accept(binding(7L, "AIBOT-01"), envelope()));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_step"));
        assertEquals(0, scalarInt("SELECT COUNT(*) FROM fbs_delivery_outbox"));

        execute("DROP TRIGGER smartbot_it_fail_step");
        SmartBotIngressResult retry = ingressService.accept(binding(7L, "AIBOT-01"), envelope());
        assertTrue(retry.firstDelivery());
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
    }

    @Test
    void mismatchedDuplicatePayloadRollsBackDuplicateCounter() throws Exception {
        ingressService.accept(binding(7L, "AIBOT-01"), envelope());
        SmartBotInboundEnvelope changed = SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("AIBOT-01")
            .opaqueSenderId("opaque-user-01")
            .chatType("single")
            .msgType("text")
            .payloadHash("b".repeat(64))
            .build();

        assertThrows(SecurityException.class,
            () -> ingressService.accept(binding(7L, "AIBOT-01"), changed));
        assertEquals(0, scalarInt("SELECT duplicate_count FROM fbs_inbound_event"));
        assertEquals(1, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
    }

    @Test
    void sameProviderMessageIdIsIsolatedByBot() throws Exception {
        seedBot(8L, "AIBOT-02", "callback_key_it_0002", "opaque-user-01");

        SmartBotIngressResult first = ingressService.accept(binding(7L, "AIBOT-01"), envelope());
        SmartBotInboundEnvelope secondEnvelope = SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("AIBOT-02")
            .opaqueSenderId("opaque-user-01")
            .chatType("single")
            .msgType("text")
            .payloadHash(PAYLOAD_HASH)
            .build();
        SmartBotIngressResult second = ingressService.accept(binding(8L, "AIBOT-02"), secondEnvelope);

        assertTrue(first.firstDelivery());
        assertTrue(second.firstDelivery());
        assertFalse(first.inboundEventId().equals(second.inboundEventId()));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_inbound_event"));
        assertEquals(2, scalarInt("SELECT COUNT(*) FROM fbs_orchestration_run"));
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

    private static void applyCurrentMigration() throws Exception {
        Path migration = locateMigration();
        String sql = Files.readString(migration, StandardCharsets.UTF_8);
        List<String> statements = Arrays.stream(sql.split(";\\s*(?:\\r?\\n|$)"))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
        execute(statements.toArray(String[]::new));
    }

    private static Path locateMigration() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String name = "update_20260711_企微智能机器人编排控制面建表.sql";
        for (int i = 0; i < 4 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve("sql").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("current SmartBot migration not found");
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

    private static int scalarInt(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
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
                                                       ObjectMapper objectMapper) {
            return new SmartBotIngressService(botBindingMapper, memberBindingMapper,
                enterpriseMapper, enterpriseMemberMapper, inboundEventMapper, runMapper,
                stepMapper, outboxMapper, identityHasher, objectMapper);
        }

        private static <T> MapperFactoryBean<T> mapper(Class<T> type, SqlSessionFactory factory) {
            MapperFactoryBean<T> bean = new MapperFactoryBean<>(type);
            bean.setSqlSessionFactory(factory);
            return bean;
        }
    }
}
