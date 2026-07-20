package com.wx.fbsir.business.board;

import com.wx.fbsir.business.board.controller.IndependentBoardAdminController;
import com.wx.fbsir.business.board.controller.IndependentBoardMeController;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.service.IndependentBoardEntitlementService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingService;
import com.wx.fbsir.business.board.service.IndependentBoardMeetingTransactionService;
import com.wx.fbsir.common.constant.CacheConstants;
import com.wx.fbsir.common.constant.Constants;
import com.wx.fbsir.common.core.domain.entity.SysRole;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.framework.security.filter.JwtAuthenticationTokenFilter;
import com.wx.fbsir.framework.security.handle.AuthenticationEntryPointImpl;
import com.wx.fbsir.framework.web.exception.GlobalExceptionHandler;
import com.wx.fbsir.framework.web.service.PermissionService;
import com.wx.fbsir.framework.web.service.TokenService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Collections;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP security integration coverage for the independent-board vertical slice.
 *
 * <p>This intentionally exercises the real RuoYi/FBSir JWT filter, token parser,
 * {@code LoginUser} principal, {@code @PreAuthorize} method interceptor and
 * {@code @ss} role/permission service through Spring MVC. Redis and the database
 * mapper are the only boundary fakes.</p>
 */
class IndependentBoardHttpSecurityIntegrationTest {
    private static final String TOKEN_SECRET =
            "independent-board-http-security-test-secret-20260720";
    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 7L;

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mockMvc;
    private static IndependentBoardMapper mapper;
    private static InMemoryRedisCache redisCache;

    @BeforeAll
    static void startSpringMvcSecurityChain() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "token.header=Authorization",
                "token.secret=" + TOKEN_SECRET,
                "token.expireTime=30");
        context.register(HttpSecurityTestConfiguration.class);
        context.refresh();

        mapper = context.getBean(IndependentBoardMapper.class);
        redisCache = context.getBean(InMemoryRedisCache.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", jakarta.servlet.Filter.class))
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
        reset(mapper);
        redisCache.clear();
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.FREE_PLAN)).thenReturn(freePlan());
    }

    @Test
    void unauthenticatedRequestIsRejectedByTheHttpSecurityChain() throws Exception {
        mockMvc.perform(get("/my/independent-board/entitlement")
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(mapper, never()).selectActiveMember(any(), any());
    }

    @Test
    void tamperedJwtIsRejectedBeforeTheController() throws Exception {
        String token = jwtFor(loginUser(USER_ID, "member", Set.of(), "user"),
                TOKEN_SECRET + "-wrong");

        mockMvc.perform(get("/my/independent-board/entitlement")
                        .header("Authorization", Constants.TOKEN_PREFIX + token)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(mapper, never()).selectActiveMember(any(), any());
    }

    @Test
    void ordinaryUserCanReadOnlyTheEnterpriseBoundToTheJwtPrincipal() throws Exception {
        when(mapper.selectActiveMember(TENANT_ID, USER_ID))
                .thenReturn(member(TENANT_ID, 11L, USER_ID));

        mockMvc.perform(get("/my/independent-board/entitlement")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.effectivePlanCode").value("BOARD_FREE"));

        verify(mapper).selectActiveMember(TENANT_ID, USER_ID);
    }

    @Test
    void ordinaryUserCrossTenantRequestFailsClosedAtTheMembershipBoundary() throws Exception {
        long foreignTenantId = 99L;
        when(mapper.selectActiveMember(foreignTenantId, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/my/independent-board/entitlement")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(foreignTenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));

        verify(mapper).selectActiveMember(foreignTenantId, USER_ID);
        verify(mapper, never()).selectEntitlement(any(), any(), any(), any());
    }

    @Test
    void nonAdministratorCannotUseAdminApiEvenWithTheFinePermission() throws Exception {
        mockMvc.perform(get("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                USER_ID, "member", Set.of("board:entitlement:query"), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(mapper, never()).selectEntitlementsByTenant(any(), any());
    }

    @Test
    void administratorWithoutTheFinePermissionCannotUseAdminApi() throws Exception {
        mockMvc.perform(get("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(USER_ID, "operator", Set.of(), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(mapper, never()).selectEntitlementsByTenant(any(), any());
    }

    @Test
    void administratorWithRoleAndFinePermissionCanUseAdminApi() throws Exception {
        when(mapper.selectEntitlementsByTenant(
                TENANT_ID, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:query"), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(mapper).selectEntitlementsByTenant(
                TENANT_ID, IndependentBoardEntitlementService.PRODUCT_CODE);
    }

    private static String bearer(LoginUser loginUser) {
        return Constants.TOKEN_PREFIX + jwtFor(loginUser, TOKEN_SECRET);
    }

    private static String jwtFor(LoginUser loginUser, String secret) {
        String tokenId = UUID.randomUUID().toString();
        loginUser.setToken(tokenId);
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(System.currentTimeMillis() + 60 * 60 * 1000L);
        redisCache.put(CacheConstants.LOGIN_TOKEN_KEY + tokenId, loginUser);
        return Jwts.builder()
                .claim(Constants.LOGIN_USER_KEY, tokenId)
                .claim(Constants.JWT_USERNAME, loginUser.getUsername())
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    private static LoginUser loginUser(
            long userId, String username, Set<String> permissions, String... roleKeys) {
        SysUser user = new SysUser(userId);
        user.setUserName(username);
        user.setPassword("unused-test-password");
        user.setRoles(List.of(roleKeys).stream().map(IndependentBoardHttpSecurityIntegrationTest::role).toList());
        return new LoginUser(userId, 1L, user, permissions);
    }

    private static SysRole role(String roleKey) {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        return role;
    }

    private static BoardEnterpriseMemberScope member(long tenantId, long memberId, long userId) {
        BoardEnterpriseMemberScope member = new BoardEnterpriseMemberScope();
        member.setTenantId(tenantId);
        member.setMemberId(memberId);
        member.setUserId(userId);
        member.setStatus(1);
        member.setDelFlag("0");
        return member;
    }

    private static BoardProductPlan freePlan() {
        BoardProductPlan plan = new BoardProductPlan();
        plan.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        plan.setPlanCode(IndependentBoardEntitlementService.FREE_PLAN);
        plan.setVip(false);
        plan.setConnectorRequired(false);
        plan.setDailyMeetingLimit(1);
        plan.setAgendaLimit(5);
        plan.setSeatLimit(3);
        plan.setSecretaryEnabled(false);
        plan.setStatus("ACTIVE");
        return plan;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
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
        IndependentBoardMapper independentBoardMapper() {
            return Mockito.mock(IndependentBoardMapper.class);
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

        @Bean
        IndependentBoardMeController meController(
                IndependentBoardEntitlementService entitlementService,
                IndependentBoardMeetingService meetingService) {
            return new IndependentBoardMeController(entitlementService, meetingService);
        }

        @Bean
        IndependentBoardAdminController adminController(
                IndependentBoardEntitlementService entitlementService,
                IndependentBoardMeetingService meetingService) {
            return new IndependentBoardAdminController(entitlementService, meetingService);
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
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
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
