package com.wx.fbsir.business.board.credit.service;

import com.wx.fbsir.business.board.credit.mapper.IndependentBoardCreditMapper;
import com.wx.fbsir.business.board.credit.mapper.SkillConsumeCreditLedgerMapper;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Real Connector/J and InnoDB coverage for the default-off 042 writer. */
@SpringJUnitConfig(SkillConsumeCreditLedgerV2MysqlIT.TestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SkillConsumeCreditLedgerV2MysqlIT {
    private static final String ALLOW_PROPERTY =
            "independent.board.skill.consume.credit.mysql.it.allowDestructive";
    private static final String URL_PROPERTY =
            "independent.board.skill.consume.credit.mysql.it.url";
    private static final String USERNAME_PROPERTY =
            "independent.board.skill.consume.credit.mysql.it.username";
    private static final String PASSWORD_PROPERTY =
            "independent.board.skill.consume.credit.mysql.it.password";
    private static final AtomicLong USER_IDS = new AtomicLong(82_000L);

    @org.springframework.beans.factory.annotation.Autowired
    private SkillConsumeCreditWriter writer;

    @org.springframework.beans.factory.annotation.Autowired
    private SkillConsumeCreditTransactionService transactionService;

    @org.springframework.beans.factory.annotation.Autowired
    private DataSource dataSource;

    @org.springframework.beans.factory.annotation.Autowired
    private CasControllableUsageMapper usageMapper;

    @org.springframework.beans.factory.annotation.Autowired
    private IndependentBoardCreditService legacyCreditService;

    @BeforeAll
    void verifyDisposableRuntimeAndSchemaContract() throws SQLException {
        assertEquals("true", required(ALLOW_PROPERTY));
        String configuredUrl = required(URL_PROPERTY);
        assertTrue(configuredUrl.matches(
                        "jdbc:mysql://127\\.0\\.0\\.1:(?!3306(?:/|$))[0-9]{4,5}/"
                                + "w3l_(8030|848)_[0-9a-f]{8}\\?.+"),
                "042 Java IT must use a unique loopback database away from port 3306");
        assertTrue(AopUtils.isAopProxy(transactionService),
                "fresh and replay methods must cross the Spring transaction proxy");

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT VERSION(),DATABASE(),@@default_storage_engine,@@transaction_isolation")) {
            assertTrue(result.next());
            assertTrue(result.getString(1).equals("8.0.30")
                    || result.getString(1).equals("8.4.8"));
            assertTrue(result.getString(2).matches("w3l_(8030|848)_[0-9a-f]{8}"));
            assertEquals("InnoDB", result.getString(3));
            assertEquals("REPEATABLE-READ", result.getString(4));
            DatabaseMetaData metadata = connection.getMetaData();
            System.out.printf("skill-credit-v2-mysql-it runtime=%s connector=%s %s database=%s%n",
                    result.getString(1), metadata.getDriverName(), metadata.getDriverVersion(),
                    result.getString(2));
        }

        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version='20260722_independent_board_credit_ledger_v1'"));
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM u3w_schema_migration "
                + "WHERE version='20260723_skill_consume_credit_ledger_v2_042'"));
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name='fbs_skill_usage_record'"));
        assertEquals(4L, scalarLong("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema=DATABASE() AND table_name IN "
                + "('fbs_skill_credit_account_v2','fbs_skill_credit_operation_v2',"
                + "'fbs_skill_credit_entry_v2','fbs_skill_credit_projection_bridge_v2')"));
    }

    @Test
    void firstConsumeAndExactReplayCommitOneImmutableResult() throws SQLException {
        long userId = insertUser(100);
        String usageId = "usage.mysql.first." + userId;

        ConsumeResult first = consume(userId, usageId, 25, "session-first");
        ConsumeResult replay = consume(userId, usageId, 25, "session-first");

        assertTrue(first.isSuccess());
        assertTrue(replay.isSuccess());
        assertEquals(75, first.getRemainPoints());
        assertEquals(first.getRemainPoints(), replay.getRemainPoints());
        assertEquals(75L, userPoints(userId));
        assertEquals(1L, rows("fbs_skill_credit_account_v2", userId));
        assertEquals(1L, rows("fbs_skill_credit_operation_v2", userId));
        assertEquals(1L, entryRows(userId));
        assertEquals(1L, rows("fbs_skill_credit_projection_bridge_v2", userId));
        assertEquals(1L, scalarLong("SELECT COUNT(*) FROM fbs_skill_usage_record "
                + "WHERE usage_record_id=? AND user_id=? AND status=1", usageId, userId));
        assertEquals("75|1|1|75|1", scalarString(
                "SELECT CONCAT_WS('|',a.balance,a.version,a.last_entry_sequence,"
                        + "b.projected_balance,b.projection_version) "
                        + "FROM fbs_skill_credit_account_v2 a "
                        + "INNER JOIN fbs_skill_credit_projection_bridge_v2 b "
                        + "ON b.account_id=a.account_id WHERE a.user_id=?", userId));
    }

    @Test
    void concurrencyDigestConflictAndBalanceRaceCommitOnlyValidWinners() throws Exception {
        long replayUser = insertUser(100);
        String replayUsage = "usage.mysql.concurrent." + replayUser;
        List<ConsumeResult> replays = runConcurrent(32,
                () -> consume(replayUser, replayUsage, 30, "session-concurrent"));

        assertTrue(replays.stream().allMatch(ConsumeResult::isSuccess));
        assertTrue(replays.stream().allMatch(result -> result.getRemainPoints() == 70));
        assertEquals(70L, userPoints(replayUser));
        assertEquals(1L, rows("fbs_skill_credit_operation_v2", replayUser));
        assertEquals(1L, entryRows(replayUser));
        ConsumeResult conflict = consume(
                replayUser, replayUsage, 31, "session-concurrent");
        assertFalse(conflict.isSuccess());
        assertEquals("SKILL_CREDIT_LEDGER_IDEMPOTENCY_DIGEST_CONFLICT",
                conflict.getFailReason());
        assertEquals(70L, userPoints(replayUser));

        long balanceUser = insertUser(100);
        List<ConsumeResult> balanceRace = race(
                () -> consume(balanceUser, "usage.mysql.balance.a." + balanceUser,
                        60, "session-balance-a"),
                () -> consume(balanceUser, "usage.mysql.balance.b." + balanceUser,
                        60, "session-balance-b"));
        assertEquals(1L, balanceRace.stream().filter(ConsumeResult::isSuccess).count());
        assertEquals(1L, balanceRace.stream().filter(result -> !result.isSuccess()
                && "SKILL_CREDIT_LEDGER_INSUFFICIENT_BALANCE".equals(result.getFailReason())).count());
        assertEquals(40L, userPoints(balanceUser));
        assertEquals(1L, rows("fbs_skill_credit_operation_v2", balanceUser));
        assertEquals(1L, entryRows(balanceUser));
    }

    @Test
    void terminalUsageCasZeroRollsBackEveryFinancialAndReceiptWrite() throws SQLException {
        long userId = insertUser(100);
        String usageId = "usage.mysql.cas-zero." + userId;
        usageMapper.failTerminalCasFor(usageId);
        ConsumeResult result;
        try {
            result = consume(userId, usageId, 25, "session-cas-zero");
        } finally {
            usageMapper.clearTerminalCasFailure();
        }

        assertFalse(result.isSuccess());
        assertEquals("SKILL_USAGE_RECORD_TERMINAL_CAS_CONFLICT", result.getFailReason());
        assertEquals(100L, userPoints(userId));
        assertEquals(0L, rows("fbs_skill_credit_account_v2", userId));
        assertEquals(0L, rows("fbs_skill_credit_operation_v2", userId));
        assertEquals(0L, entryRows(userId));
        assertEquals(0L, rows("fbs_skill_credit_projection_bridge_v2", userId));
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM fbs_skill_usage_record WHERE usage_record_id=?", usageId));
    }

    @Test
    void legacyAndV2AuthorityAreMutuallyExclusiveInBothDirections() throws SQLException {
        long legacyUser = insertUser(100);
        execute("INSERT INTO fbs_credit_account(account_id,user_id,opening_balance,balance) "
                        + "VALUES (?,?,100,100)",
                UUID.randomUUID().toString(), legacyUser);
        ConsumeResult blockedV2 = consume(
                legacyUser, "usage.mysql.legacy." + legacyUser, 10, "session-legacy");
        assertFalse(blockedV2.isSuccess());
        assertEquals("SKILL_CREDIT_LEDGER_LEGACY_AUTHORITY_PRESENT", blockedV2.getFailReason());
        assertEquals(0L, rows("fbs_skill_credit_account_v2", legacyUser));

        long v2User = insertUser(100);
        assertTrue(consume(v2User, "usage.mysql.v2." + v2User, 10, "session-v2").isSuccess());
        BoardCreditGrantRequest grant = new BoardCreditGrantRequest(
                v2User, 0L, 10, "CUSTOMER_SUPPORT", "blocked by v2 authority",
                "grant.mysql.v2-fence." + v2User);
        BoardCreditReversalRequest reversal = new BoardCreditReversalRequest(
                UUID.randomUUID().toString(), 0L, "OPERATOR_ERROR", "blocked by v2 authority",
                "reverse.mysql.v2-fence." + v2User);

        List<Runnable> fencedLegacyActions = List.of(
                () -> {
                    legacyCreditService.grant(grant, 9_001L);
                },
                () -> {
                    legacyCreditService.reverse(reversal, 9_001L);
                },
                () -> {
                    legacyCreditService.audit(v2User);
                });
        for (Runnable action : fencedLegacyActions) {
            ServiceException failure = assertThrows(ServiceException.class, action::run);
            assertEquals(409, failure.getCode());
            assertEquals("CREDIT_LEDGER_V2_AUTHORITY_CANDIDATE_ACTIVE", failure.getMessage());
        }
        assertEquals(0L, scalarLong(
                "SELECT COUNT(*) FROM fbs_credit_account WHERE user_id=?", v2User));
        assertEquals(90L, userPoints(v2User));
    }

    private ConsumeResult consume(long userId, String usageId, int amount, String sessionId) {
        return writer.consume(userId, usageId, 7_001L, "26.7.20", "board.review",
                "SKILL_USE", amount, "WORKBUDDY", sessionId);
    }

    private long insertUser(int points) throws SQLException {
        long userId = USER_IDS.incrementAndGet();
        execute("INSERT INTO sys_user(user_id,points,status,del_flag,update_time) "
                + "VALUES (?,?,'0','0',NOW(3))", userId, points);
        return userId;
    }

    private List<ConsumeResult> runConcurrent(
            int count, Supplier<ConsumeResult> action) throws Exception {
        List<Supplier<ConsumeResult>> actions = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            actions.add(action);
        }
        return runCoordinated(actions);
    }

    private List<ConsumeResult> race(
            Supplier<ConsumeResult> first, Supplier<ConsumeResult> second) throws Exception {
        return runCoordinated(List.of(first, second));
    }

    private List<ConsumeResult> runCoordinated(
            List<Supplier<ConsumeResult>> actions) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(actions.size());
        CountDownLatch ready = new CountDownLatch(actions.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ConsumeResult>> futures = new ArrayList<>();
        try {
            for (Supplier<ConsumeResult> action : actions) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(20, TimeUnit.SECONDS));
                    return action.get();
                }));
            }
            assertTrue(ready.await(20, TimeUnit.SECONDS));
            start.countDown();
            List<ConsumeResult> results = new ArrayList<>();
            for (Future<ConsumeResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
        }
    }

    private long userPoints(long userId) throws SQLException {
        return scalarLong("SELECT COALESCE(points,0) FROM sys_user WHERE user_id=?", userId);
    }

    private long rows(String table, long userId) throws SQLException {
        assertTrue(table.equals("fbs_skill_credit_account_v2")
                || table.equals("fbs_skill_credit_operation_v2")
                || table.equals("fbs_skill_credit_projection_bridge_v2"));
        return scalarLong("SELECT COUNT(*) FROM " + table + " WHERE user_id=?", userId);
    }

    private long entryRows(long userId) throws SQLException {
        return scalarLong("SELECT COUNT(*) FROM fbs_skill_credit_entry_v2 e "
                + "INNER JOIN fbs_skill_credit_operation_v2 o "
                + "ON o.operation_id=e.operation_id WHERE o.user_id=?", userId);
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
                return result.getString(1);
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
            throw new IllegalStateException(name + " is required for the explicit 042 MySQL IT");
        }
        return value;
    }

    static final class CasControllableUsageMapper implements FbsSkillUsageRecordMapper {
        private final FbsSkillUsageRecordMapper delegate;
        private final AtomicReference<String> terminalCasFailure = new AtomicReference<>();

        CasControllableUsageMapper(FbsSkillUsageRecordMapper delegate) {
            this.delegate = delegate;
        }

        void failTerminalCasFor(String usageRecordId) {
            terminalCasFailure.set(usageRecordId);
        }

        void clearTerminalCasFailure() {
            terminalCasFailure.set(null);
        }

        @Override
        public FbsSkillUsageRecord selectByRecordId(String usageRecordId) {
            return delegate.selectByRecordId(usageRecordId);
        }

        @Override
        public FbsSkillUsageRecord selectByRecordIdForUpdate(String usageRecordId) {
            return delegate.selectByRecordIdForUpdate(usageRecordId);
        }

        @Override
        public int insertUsageRecord(FbsSkillUsageRecord record) {
            return delegate.insertUsageRecord(record);
        }

        @Override
        public int updateStatusByRecordId(
                String usageRecordId, Integer status, String errorMessage) {
            if (usageRecordId.equals(terminalCasFailure.get())) {
                return 0;
            }
            return delegate.updateStatusByRecordId(usageRecordId, status, errorMessage);
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
            factory.setMapperLocations(
                    new ClassPathResource("mapper/board/SkillConsumeCreditLedgerMapper.xml"),
                    new ClassPathResource("mapper/board/IndependentBoardCreditMapper.xml"),
                    new ClassPathResource("mapper/fbs/FbsSkillUsageRecordMapper.xml"));
            return factory.getObject();
        }

        @Bean
        SkillConsumeCreditLedgerMapper skillCreditMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(SkillConsumeCreditLedgerMapper.class);
        }

        @Bean
        CasControllableUsageMapper usageRecordMapper(SqlSessionFactory factory) {
            return new CasControllableUsageMapper(
                    new SqlSessionTemplate(factory).getMapper(FbsSkillUsageRecordMapper.class));
        }

        @Bean
        IndependentBoardCreditMapper legacyCreditMapper(SqlSessionFactory factory) {
            return new SqlSessionTemplate(factory).getMapper(IndependentBoardCreditMapper.class);
        }

        @Bean
        SkillConsumeCreditTransactionService skillCreditTransactionService(
                SkillConsumeCreditLedgerMapper mapper, FbsSkillUsageRecordMapper usageMapper) {
            return new SkillConsumeCreditTransactionService(mapper, usageMapper);
        }

        @Bean
        SkillConsumeCreditWriter skillCreditWriter(
                SkillConsumeCreditTransactionService transactionService) {
            return new SkillConsumeCreditWriter(transactionService);
        }

        @Bean
        IndependentBoardCreditTransactionService legacyCreditTransactionService(
                IndependentBoardCreditMapper mapper) {
            return new IndependentBoardCreditTransactionService(mapper);
        }

        @Bean
        IndependentBoardCreditService legacyCreditService(
                IndependentBoardCreditTransactionService transactionService) {
            return new IndependentBoardCreditService(transactionService, true, true);
        }
    }
}
