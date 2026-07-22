package com.wx.fbsir.business.board.credit;

import com.wx.fbsir.business.board.credit.controller.IndependentBoardCreditAdminController;
import com.wx.fbsir.business.board.credit.controller.IndependentBoardCreditExceptionHandler;
import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditEnvelope;
import com.wx.fbsir.business.board.credit.dto.BoardCreditAuditRecord;
import com.wx.fbsir.business.board.credit.dto.BoardCreditCommandResult;
import com.wx.fbsir.business.board.credit.dto.BoardCreditGrantRequest;
import com.wx.fbsir.business.board.credit.dto.BoardCreditReversalRequest;
import com.wx.fbsir.business.board.credit.service.IndependentBoardCreditService;
import com.wx.fbsir.common.constant.CacheConstants;
import com.wx.fbsir.common.constant.Constants;
import com.wx.fbsir.common.core.domain.entity.SysRole;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.common.exception.ServiceException;
import com.wx.fbsir.framework.security.filter.JwtAuthenticationTokenFilter;
import com.wx.fbsir.framework.security.handle.AuthenticationEntryPointImpl;
import com.wx.fbsir.framework.web.exception.GlobalExceptionHandler;
import com.wx.fbsir.framework.web.service.PermissionService;
import com.wx.fbsir.framework.web.service.TokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWT, method-security and MVC coverage for the isolated credit administrator surface. */
class IndependentBoardCreditHttpSecurityIntegrationTest {
    private static final String TOKEN_SECRET =
            "independent-board-credit-http-security-test-secret-20260722";
    private static final long ACTOR_USER_ID = 42L;
    private static final long TARGET_USER_ID = 99L;
    private static final String ORIGINAL_OPERATION_ID =
            "11111111-1111-1111-1111-111111111111";
    private static final String GRANT_BODY = """
            {"userId":99,"amount":10,"reasonCode":"CUSTOMER_SUPPORT",
             "note":"controlled support adjustment","idempotencyKey":"grant-test-key-0001"}
            """;
    private static final String REVERSAL_BODY = """
            {"originalOperationId":"11111111-1111-1111-1111-111111111111",
             "reasonCode":"OPERATOR_ERROR","note":"controlled reversal note",
             "idempotencyKey":"reverse-test-key-001"}
            """;

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mockMvc;
    private static IndependentBoardCreditService creditService;
    private static InMemoryRedisCache redisCache;

    @BeforeAll
    static void startSpringMvcSecurityChain() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "token.header=Authorization",
                "token.secret=" + TOKEN_SECRET,
                "token.expireTime=30",
                "fbsir.independent-board.credit-candidate.enabled=true");
        context.register(HttpSecurityTestConfiguration.class);
        context.refresh();

        creditService = context.getBean(IndependentBoardCreditService.class);
        redisCache = context.getBean(InMemoryRedisCache.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(
                        "springSecurityFilterChain", jakarta.servlet.Filter.class))
                .build();
    }

    @AfterAll
    static void stopSpringContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetBoundaries() {
        reset(creditService);
        redisCache.clear();
    }

    @Test
    void unauthenticatedGrantIsRejectedBeforeTheCreditService() throws Exception {
        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(creditService);
    }

    @Test
    void grantRequiresBothGlobalAdminAndItsFinePermission() throws Exception {
        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "member", Set.of("board:credit:grant"), "user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("CREDIT_ACCESS_DENIED"));

        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of(), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("CREDIT_ACCESS_DENIED"));

        verifyNoInteractions(creditService);
    }

    @Test
    void authenticatedActorComesOnlyFromJwtForGrant() throws Exception {
        BoardCreditCommandResult result = commandResult("GRANT", 10L, 110L, null);
        when(creditService.grant(any(), eq(ACTOR_USER_ID))).thenReturn(result);

        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:credit:grant"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.data.userId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.data.delta").value(10L))
                .andExpect(jsonPath("$.data.note").doesNotExist())
                .andExpect(jsonPath("$.data.requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.entryHash").doesNotExist());

        verify(creditService).grant(eq(new BoardCreditGrantRequest(
                TARGET_USER_ID,
                10,
                "CUSTOMER_SUPPORT",
                "controlled support adjustment",
                "grant-test-key-0001")), eq(ACTOR_USER_ID));
    }

    @Test
    void frameworkSuperAdminWildcardRemainsAnExplicitPermissionOverride() throws Exception {
        when(creditService.grant(any(), eq(ACTOR_USER_ID)))
                .thenReturn(commandResult("GRANT", 10L, 110L, null));

        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "super-admin", Set.of("*:*:*"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(TARGET_USER_ID));

        verify(creditService).grant(any(BoardCreditGrantRequest.class), eq(ACTOR_USER_ID));
    }

    @Test
    void reversalPermissionIsIndependentFromGrantPermission() throws Exception {
        mockMvc.perform(post("/business/independent-board/credit-operations/reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:credit:grant"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVERSAL_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("CREDIT_ACCESS_DENIED"));

        verifyNoInteractions(creditService);
    }

    @Test
    void authenticatedActorComesOnlyFromJwtForReversal() throws Exception {
        BoardCreditCommandResult result = commandResult(
                "REVERSAL", -10L, 100L, ORIGINAL_OPERATION_ID);
        when(creditService.reverse(any(), eq(ACTOR_USER_ID))).thenReturn(result);

        mockMvc.perform(post("/business/independent-board/credit-operations/reversals")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:credit:reverse"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REVERSAL_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationType").value("REVERSAL"))
                .andExpect(jsonPath("$.data.reversalOfOperationId")
                        .value(ORIGINAL_OPERATION_ID));

        verify(creditService).reverse(eq(new BoardCreditReversalRequest(
                ORIGINAL_OPERATION_ID,
                "OPERATOR_ERROR",
                "controlled reversal note",
                "reverse-test-key-001")), eq(ACTOR_USER_ID));
    }

    @Test
    void queryPermissionIsIndependentAndTheResponseRemovesLedgerSecrets() throws Exception {
        when(creditService.audit(TARGET_USER_ID)).thenReturn(auditEnvelope());

        mockMvc.perform(get("/business/independent-board/credit-accounts/{userId}",
                        TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:credit:query"), "admin"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(jsonPath("$.data.userId").value(TARGET_USER_ID))
                .andExpect(jsonPath("$.data.accountScope").value("USER_GLOBAL"))
                .andExpect(jsonPath("$.data.currencyCode").value("FBS_POINTS"))
                .andExpect(jsonPath("$.data.records[0].operationId").value("operation-001"))
                .andExpect(jsonPath("$.data.records[0].reasonCode").value("CUSTOMER_SUPPORT"))
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.lastEntryHash").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].previousEntryHash").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].entryHash").doesNotExist())
                .andExpect(content().string(not(containsString("digest-secret"))))
                .andExpect(content().string(not(containsString("hash-secret"))));

        verify(creditService).audit(TARGET_USER_ID);
    }

    @Test
    void missingCreditAccountUsesTheStableHttp404Contract() throws Exception {
        when(creditService.audit(TARGET_USER_ID)).thenThrow(
                new ServiceException("CREDIT_ACCOUNT_NOT_FOUND", 404));

        mockMvc.perform(get("/business/independent-board/credit-accounts/{userId}",
                        TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID,
                                "admin",
                                Set.of("board:credit:query"),
                                "admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("CREDIT_ACCOUNT_NOT_FOUND"));

        verify(creditService).audit(TARGET_USER_ID);
    }

    @Test
    void grantPermissionDoesNotAuthorizeCreditQuery() throws Exception {
        mockMvc.perform(get("/business/independent-board/credit-accounts/{userId}",
                        TARGET_USER_ID)
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:credit:grant"), "admin"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.msg").value("CREDIT_ACCESS_DENIED"));

        verifyNoInteractions(creditService);
    }

    @Test
    void malformedAndInvalidGrantBodiesReturnStableHttp400WithoutCallingService()
            throws Exception {
        String authorization = bearer(loginUser(
                ACTOR_USER_ID, "admin", Set.of("board:credit:grant"), "admin"));

        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("CREDIT_REQUEST_INVALID"));

        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY.replace("\"amount\":10", "\"amount\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("CREDIT_REQUEST_INVALID"));

        verifyNoInteractions(creditService);
    }

    @Test
    void serviceConflictUsesRealHttpStatusAndDoesNotLeakDetailMessage() throws Exception {
        when(creditService.grant(any(), eq(ACTOR_USER_ID))).thenThrow(
                new ServiceException("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT", 409)
                        .setDetailMessage("secret note digest hash sql table"));

        mockMvc.perform(post("/business/independent-board/credit-operations/grants")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:credit:grant"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(GRANT_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("CREDIT_IDEMPOTENCY_DIGEST_CONFLICT"))
                .andExpect(content().string(not(containsString("secret note"))))
                .andExpect(content().string(not(containsString("sql table"))));
    }

    @Test
    void creditHttpSurfaceIsAbsentWhenTheCandidateFlagIsMissing() throws Exception {
        try (AnnotationConfigWebApplicationContext disabledContext =
                     new AnnotationConfigWebApplicationContext()) {
            disabledContext.setServletContext(new MockServletContext());
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    disabledContext,
                    "token.header=Authorization",
                    "token.secret=" + TOKEN_SECRET,
                    "token.expireTime=30");
            disabledContext.register(HttpSecurityTestConfiguration.class);
            disabledContext.refresh();

            InMemoryRedisCache disabledCache = disabledContext.getBean(InMemoryRedisCache.class);
            IndependentBoardCreditService disabledService =
                    disabledContext.getBean(IndependentBoardCreditService.class);
            MockMvc disabledMvc = MockMvcBuilders.webAppContextSetup(disabledContext)
                    .addFilters(disabledContext.getBean(
                            "springSecurityFilterChain", jakarta.servlet.Filter.class))
                    .build();

            disabledMvc.perform(post("/business/independent-board/credit-operations/grants")
                            .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                    ACTOR_USER_ID,
                                    "admin",
                                    Set.of("board:credit:grant"),
                                    "admin"), disabledCache))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(GRANT_BODY))
                    .andExpect(status().isNotFound());

            assertTrue(disabledContext.getBeansOfType(
                    IndependentBoardCreditAdminController.class).isEmpty());
            assertTrue(disabledContext.getBeansOfType(
                    IndependentBoardCreditExceptionHandler.class).isEmpty());
            verifyNoInteractions(disabledService);
        }
    }

    private static BoardCreditCommandResult commandResult(
            String operationType, Long delta, Long balanceAfter, String reversalOf) {
        return new BoardCreditCommandResult(
                "22222222-2222-2222-2222-222222222222",
                operationType,
                TARGET_USER_ID,
                delta,
                balanceAfter,
                reversalOf,
                new Date(1_790_000_000_000L));
    }

    private static BoardCreditAuditEnvelope auditEnvelope() {
        BoardCreditAuditRecord record = new BoardCreditAuditRecord(
                "operation-001",
                "idempotency-secret",
                "digest-secret",
                "GRANT",
                10L,
                "CUSTOMER_SUPPORT",
                ACTOR_USER_ID,
                null,
                100L,
                110L,
                1L,
                "previous-hash-secret",
                "entry-hash-secret",
                new Date(1_790_000_000_000L));
        return new BoardCreditAuditEnvelope(
                "account-secret",
                TARGET_USER_ID,
                "USER_GLOBAL",
                "FBS_POINTS",
                100L,
                110L,
                1L,
                "last-hash-secret",
                new Date(1_790_000_001_000L),
                List.of(record),
                500,
                false);
    }

    private static String bearer(LoginUser loginUser) {
        return bearer(loginUser, redisCache);
    }

    private static String bearer(LoginUser loginUser, InMemoryRedisCache targetCache) {
        String tokenId = UUID.randomUUID().toString();
        loginUser.setToken(tokenId);
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(System.currentTimeMillis() + 60 * 60 * 1000L);
        targetCache.put(CacheConstants.LOGIN_TOKEN_KEY + tokenId, loginUser);
        String jwt = Jwts.builder()
                .claim(Constants.LOGIN_USER_KEY, tokenId)
                .claim(Constants.JWT_USERNAME, loginUser.getUsername())
                .signWith(SignatureAlgorithm.HS512, TOKEN_SECRET)
                .compact();
        return Constants.TOKEN_PREFIX + jwt;
    }

    private static LoginUser loginUser(
            long userId, String username, Set<String> permissions, String... roleKeys) {
        SysUser user = new SysUser(userId);
        user.setUserName(username);
        user.setPassword("unused-test-password");
        user.setRoles(List.of(roleKeys).stream()
                .map(IndependentBoardCreditHttpSecurityIntegrationTest::role)
                .toList());
        return new LoginUser(userId, 1L, user, permissions);
    }

    private static SysRole role(String roleKey) {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        return role;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
    @ComponentScan(basePackageClasses = IndependentBoardCreditAdminController.class)
    static class HttpSecurityTestConfiguration {
        @Bean
        InMemoryRedisCache redisCache() {
            return new InMemoryRedisCache();
        }

        @Bean
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTemplate<Object, Object> redisTemplate() {
            return Mockito.mock(RedisTemplate.class);
        }

        @Bean
        TokenService tokenService() {
            return new TokenService();
        }

        @Bean
        JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter() {
            return new JwtAuthenticationTokenFilter();
        }

        @Bean
        AuthenticationEntryPointImpl authenticationEntryPoint() {
            return new AuthenticationEntryPointImpl();
        }

        @Bean(name = "ss")
        PermissionService permissionService() {
            return new PermissionService();
        }

        @Bean
        IndependentBoardCreditService creditService() {
            return Mockito.mock(IndependentBoardCreditService.class);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http,
                JwtAuthenticationTokenFilter jwtFilter,
                AuthenticationEntryPointImpl entryPoint) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(entryPoint))
                    .sessionManagement(session -> session.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    static final class InMemoryRedisCache extends RedisCache {
        private final Map<String, Object> values = new ConcurrentHashMap<>();

        void put(String key, Object value) {
            values.put(key, value);
        }

        void clear() {
            values.clear();
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getCacheObject(String key) {
            return (T) values.get(key);
        }
    }
}
