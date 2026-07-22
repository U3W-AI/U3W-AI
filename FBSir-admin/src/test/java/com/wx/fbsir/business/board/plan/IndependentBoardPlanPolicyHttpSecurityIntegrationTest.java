package com.wx.fbsir.business.board.plan;

import com.wx.fbsir.business.board.plan.controller.IndependentBoardPlanPolicyAdminController;
import com.wx.fbsir.business.board.plan.controller.IndependentBoardPlanPolicyExceptionHandler;
import com.wx.fbsir.business.board.plan.controller.IndependentBoardPlanPolicyNoStoreFilter;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyAuditEnvelope;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionView;
import com.wx.fbsir.business.board.plan.service.IndependentBoardPlanPolicyService;
import com.wx.fbsir.common.annotation.Log;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

/** Real JWT, method-security and MVC contract for the default-off plan-policy surface. */
class IndependentBoardPlanPolicyHttpSecurityIntegrationTest {
    private static final String TOKEN_SECRET =
            "independent-board-plan-policy-http-security-test-secret-20260722";
    private static final long ACTOR_USER_ID = 42L;
    private static final String BODY = """
            {"planCode":"BOARD_VIP","expectedVersion":1,
             "planName":"Independent Board VIP Plus","dailyMeetingLimit":8,
             "agendaLimit":30,"seatLimit":null,"secretaryEnabled":true,
             "rollbackOfReceiptId":null,
             "idempotencyKey":"plan-policy-http-0001"}
            """;

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mockMvc;
    private static IndependentBoardPlanPolicyService service;
    private static InMemoryRedisCache redisCache;

    @BeforeAll
    static void startSpringMvcSecurityChain() {
        context = context(true);
        service = context.getBean(IndependentBoardPlanPolicyService.class);
        redisCache = context.getBean(InMemoryRedisCache.class);
        mockMvc = mvc(context);
    }

    @AfterAll
    static void stopSpringContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetBoundaries() {
        reset(service);
        redisCache.clear();
    }

    @Test
    void unauthenticatedRevisionIsRejectedBeforeService() throws Exception {
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.code").value(401));
        verifyNoInteractions(service);
    }

    @Test
    void trailingSlashCandidatePathRemainsNoStoreWhenSecurityRejectsIt() throws Exception {
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions/"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")));
        verifyNoInteractions(service);
    }

    @Test
    void revisionRequiresBothGlobalAdminAndFinePermission() throws Exception {
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "member", Set.of("board:plan:revise"), "user")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_ACCESS_DENIED"));

        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of(), "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")));
        verifyNoInteractions(service);
    }

    @Test
    void revisionUsesJwtActorAndReturnsOnlySafeNoStoreProjection() throws Exception {
        BoardPlanPolicyRevisionView result = view();
        when(service.revise(any(), eq(ACTOR_USER_ID))).thenReturn(result);

        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:plan:revise"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.data.receiptId").value(result.receiptId()))
                .andExpect(jsonPath("$.data.actorUserId").value(ACTOR_USER_ID))
                .andExpect(jsonPath("$.data.idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.data.commandDigest").doesNotExist());

        verify(service).revise(eq(new BoardPlanPolicyRevisionRequest(
                "BOARD_VIP", 1L, "Independent Board VIP Plus", 8, 30,
                null, true, null, "plan-policy-http-0001")), eq(ACTOR_USER_ID));
    }

    @Test
    void auditPermissionIsIndependentAndBoundedProjectionRemainsNoStore() throws Exception {
        when(service.audit()).thenReturn(new BoardPlanPolicyAuditEnvelope(
                List.of(view()), 100, false));

        mockMvc.perform(get("/business/independent-board/plan-policy-receipts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:plan:audit"), "admin"))))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.data.limit").value(100))
                .andExpect(jsonPath("$.data.records[0].policyDigest").value("d".repeat(64)))
                .andExpect(content().string(not(containsString("idempotency"))));
        verify(service).audit();

        reset(service);
        mockMvc.perform(get("/business/independent-board/plan-policy-receipts")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:plan:revise"), "admin"))))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")));
        verifyNoInteractions(service);
    }

    @Test
    void malformedOrExpandedBodyReturnsStable400WithoutServiceCall() throws Exception {
        String authorization = bearer(loginUser(
                ACTOR_USER_ID, "admin", Set.of("board:plan:revise"), "admin"));
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_REQUEST_INVALID"));
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("}", ",\"actorUserId\":1}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_REQUEST_INVALID"));
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("Independent Board VIP Plus", "\\uD800")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void planNameTransportLimitCountsUnicodeCodePoints() throws Exception {
        String authorization = bearer(loginUser(
                ACTOR_USER_ID, "admin", Set.of("board:plan:revise"), "admin"));
        String allowed = "😀".repeat(128);
        when(service.revise(any(), eq(ACTOR_USER_ID))).thenReturn(view());
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("Independent Board VIP Plus", allowed)))
                .andExpect(status().isOk());
        verify(service).revise(any(), eq(ACTOR_USER_ID));

        reset(service);
        String rejected = "😀".repeat(129);
        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("Independent Board VIP Plus", rejected)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_REQUEST_INVALID"));
        verifyNoInteractions(service);
    }

    @Test
    void serviceConflictUsesReal409WithoutLeakingDetail() throws Exception {
        when(service.revise(any(), eq(ACTOR_USER_ID))).thenThrow(
                new ServiceException("BOARD_PLAN_POLICY_VERSION_CONFLICT", 409)
                        .setDetailMessage("secret idempotency sql detail"));

        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:plan:revise"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_VERSION_CONFLICT"))
                .andExpect(content().string(not(containsString("secret idempotency"))));
    }

    @Test
    void unexpectedRuntimeFailureReturnsStableNoStore500() throws Exception {
        when(service.revise(any(), eq(ACTOR_USER_ID))).thenThrow(
                new IllegalStateException("secret SQL and connection detail"));

        mockMvc.perform(post("/business/independent-board/plan-policy-revisions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                ACTOR_USER_ID, "admin", Set.of("board:plan:revise"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        containsString("no-store")))
                .andExpect(jsonPath("$.msg").value("BOARD_PLAN_POLICY_FAILED"))
                .andExpect(content().string(not(containsString("secret SQL"))));
    }

    @Test
    void operationAuditPersistsNeitherFreeTextRequestNorReceiptResponse() throws Exception {
        Log audit = IndependentBoardPlanPolicyAdminController.class
                .getDeclaredMethod("revise", BoardPlanPolicyRevisionRequest.class)
                .getAnnotation(Log.class);

        assertTrue(audit != null && !audit.isSaveRequestData());
        assertTrue(!audit.isSaveResponseData());
        assertArrayEquals(new String[0], audit.excludeParamNames());
    }

    @Test
    void surfaceAndLocalAdviceAreAbsentWhenCandidateFlagIsMissing() throws Exception {
        try (AnnotationConfigWebApplicationContext disabled = context(false)) {
            InMemoryRedisCache cache = disabled.getBean(InMemoryRedisCache.class);
            IndependentBoardPlanPolicyService disabledService =
                    disabled.getBean(IndependentBoardPlanPolicyService.class);
            mvc(disabled).perform(post("/business/independent-board/plan-policy-revisions")
                            .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                    ACTOR_USER_ID, "admin", Set.of("board:plan:revise"),
                                    "admin"), cache))
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                            containsString("no-store")));
            mvc(disabled).perform(get("/business/independent-board/plan-policy-receipts")
                            .header(HttpHeaders.AUTHORIZATION, bearer(loginUser(
                                    ACTOR_USER_ID, "admin", Set.of("board:plan:audit"),
                                    "admin"), cache)))
                    .andExpect(status().isNotFound())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                            containsString("no-store")));
            assertTrue(disabled.getBeansOfType(
                    IndependentBoardPlanPolicyAdminController.class).isEmpty());
            assertTrue(disabled.getBeansOfType(
                    IndependentBoardPlanPolicyExceptionHandler.class).isEmpty());
            verifyNoInteractions(disabledService);
        }
    }

    private static BoardPlanPolicyRevisionView view() {
        return new BoardPlanPolicyRevisionView(
                "123e4567-e89b-12d3-a456-426614174000", "BOARD_VIP", 2L,
                "plan-policy-baseline-board-vip-v1", null, "PLAN_POLICY_REVISED",
                "ADMIN_USER", ACTOR_USER_ID, "Independent Board VIP Plus",
                8, 30, null, true, "c".repeat(64), "d".repeat(64),
                "ACTION_COMPLETED", new Date(1_790_000_000_000L));
    }

    private static AnnotationConfigWebApplicationContext context(boolean enabled) {
        AnnotationConfigWebApplicationContext value = new AnnotationConfigWebApplicationContext();
        value.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                value,
                "token.header=Authorization",
                "token.secret=" + TOKEN_SECRET,
                "token.expireTime=30",
                "fbsir.independent-board.plan-policy-candidate.enabled=" + enabled);
        value.register(HttpSecurityTestConfiguration.class);
        value.refresh();
        return value;
    }

    private static MockMvc mvc(AnnotationConfigWebApplicationContext target) {
        return MockMvcBuilders.webAppContextSetup(target)
                .addFilters(
                        target.getBean(IndependentBoardPlanPolicyNoStoreFilter.class),
                        target.getBean(
                                "springSecurityFilterChain", jakarta.servlet.Filter.class))
                .build();
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
                .signWith(SignatureAlgorithm.HS512, TOKEN_SECRET).compact();
        return Constants.TOKEN_PREFIX + jwt;
    }

    private static LoginUser loginUser(
            long userId, String username, Set<String> permissions, String... roleKeys) {
        SysUser user = new SysUser(userId);
        user.setUserName(username);
        user.setPassword("unused-test-password");
        user.setRoles(List.of(roleKeys).stream()
                .map(IndependentBoardPlanPolicyHttpSecurityIntegrationTest::role).toList());
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
    @ComponentScan(basePackageClasses = IndependentBoardPlanPolicyAdminController.class)
    static class HttpSecurityTestConfiguration {
        @Bean InMemoryRedisCache redisCache() { return new InMemoryRedisCache(); }

        @Bean
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTemplate<Object, Object> redisTemplate() {
            return Mockito.mock(RedisTemplate.class);
        }

        @Bean TokenService tokenService() { return new TokenService(); }
        @Bean JwtAuthenticationTokenFilter jwtAuthenticationTokenFilter() {
            return new JwtAuthenticationTokenFilter();
        }
        @Bean AuthenticationEntryPointImpl authenticationEntryPoint() {
            return new AuthenticationEntryPointImpl();
        }
        @Bean(name = "ss") PermissionService permissionService() {
            return new PermissionService();
        }
        @Bean IndependentBoardPlanPolicyService planPolicyService() {
            return Mockito.mock(IndependentBoardPlanPolicyService.class);
        }
        @Bean GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http,
                JwtAuthenticationTokenFilter jwtFilter,
                AuthenticationEntryPointImpl entryPoint) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(value -> value.authenticationEntryPoint(entryPoint))
                    .sessionManagement(value -> value.sessionCreationPolicy(
                            SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(value -> value.anyRequest().authenticated())
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                    .build();
        }
    }

    static final class InMemoryRedisCache extends RedisCache {
        private final Map<String, Object> values = new ConcurrentHashMap<>();
        void put(String key, Object value) { values.put(key, value); }
        void clear() { values.clear(); }
        @SuppressWarnings("unchecked")
        @Override public <T> T getCacheObject(String key) { return (T) values.get(key); }
    }
}
