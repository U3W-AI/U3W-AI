package com.wx.fbsir.business.board;

import com.wx.fbsir.business.board.portal.BoardPortalBadRequestException;
import com.wx.fbsir.business.board.portal.BoardPortalDataDriftException;
import com.wx.fbsir.business.board.portal.BoardPortalForbiddenException;
import com.wx.fbsir.business.board.portal.IndependentBoardPortalReadService;
import com.wx.fbsir.business.board.portal.controller.IndependentBoardPortalAdminReadController;
import com.wx.fbsir.business.board.portal.controller.BoardPortalCandidateBoundaryFilter;
import com.wx.fbsir.business.board.portal.controller.IndependentBoardPortalMeReadController;
import com.wx.fbsir.business.board.portal.controller.IndependentBoardPortalReadExceptionHandler;
import com.wx.fbsir.business.board.portal.dto.BoardPortalConnectorView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalOAuthFamilyView;
import com.wx.fbsir.business.board.portal.dto.BoardPortalReadEnvelope;
import com.wx.fbsir.common.constant.CacheConstants;
import com.wx.fbsir.common.constant.Constants;
import com.wx.fbsir.common.core.domain.AjaxResult;
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
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.RedisTemplate;
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
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the candidate reads through the real JWT and method-security spine. */
class IndependentBoardPortalReadHttpSecurityIntegrationTest {

    private static final String TOKEN_SECRET =
            "independent-board-portal-read-http-security-test-secret-20260721";
    private static final long USER_ID = 42L;
    private static final long TENANT_ID = 7L;

    private static Harness enabled;
    private static Harness disabled;

    @BeforeAll
    static void startSpringMvcSecurityChains() {
        enabled = startHarness(true);
        disabled = startHarness(false);
        org.junit.jupiter.api.Assertions.assertEquals(1,
                enabled.context().getBeansOfType(
                        IndependentBoardPortalMeReadController.class).size());
        org.junit.jupiter.api.Assertions.assertTrue(
                disabled.context().getBeansOfType(
                        IndependentBoardPortalMeReadController.class).isEmpty());
    }

    @AfterAll
    static void stopSpringContexts() {
        if (enabled != null) {
            enabled.context().close();
        }
        if (disabled != null) {
            disabled.context().close();
        }
    }

    @BeforeEach
    void resetBoundaries() {
        reset(enabled.readService(), disabled.readService());
        enabled.redisCache().clear();
        disabled.redisCache().clear();
    }

    @Test
    void defaultOffReturns404ToAValidJwtWithoutCallingTheReadService() throws Exception {
        disabled.mockMvc().perform(get("/my/independent-board/connector")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", bearer(disabled,
                                loginUser(USER_ID, "user", Set.of(
                                        "my:independent-board:connector:view"), "user"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));

        verifyNoInteractions(disabled.readService());
    }

    @Test
    void anonymousAndUnknownBearerAreRejectedBeforeTheReadService() throws Exception {
        enabled.mockMvc().perform(get("/my/independent-board/connector")
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        enabled.mockMvc().perform(get("/my/independent-board/connector")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", Constants.TOKEN_PREFIX
                                + jwtWithUnknownLogin("missing-login")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verifyNoInteractions(enabled.readService());
    }

    @Test
    void roleAndPermissionMustBothMatchTheAdminEndpoint() throws Exception {
        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .header("Authorization", bearer(enabled,
                                loginUser(USER_ID, "ordinary", Set.of(
                                        "board:oauth:client:query"), "user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .header("Authorization", bearer(enabled,
                                loginUser(USER_ID, "admin", Set.of(
                                        "board:oauth:family:query"), "admin"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(enabled.readService());
    }

    @Test
    void meEndpointRequiresItsDedicatedPermission() throws Exception {
        enabled.mockMvc().perform(get("/my/independent-board/connector")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", bearer(enabled,
                                loginUser(USER_ID, "user", Set.of(), "user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        verifyNoInteractions(enabled.readService());
    }

    @Test
    void malformedOrAmbiguousQueriesReturn400BeforeAServiceCall() throws Exception {
        String token = bearer(enabled, admin(
                "board:oauth:family:query", "board:oauth:client:query"));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/families")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/families")
                        .param("tenantId", "not-a-number")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/families")
                        .param("tenantId", "0")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .param("unexpected", "value")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("UNEXPECTED_QUERY_PARAMETER"));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .param("status", "ACTIVE", "REVOKED")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("DUPLICATE_QUERY_PARAMETER"));

        verifyNoInteractions(enabled.readService());
    }

    @Test
    void invalidStatusAndCursorAreMappedTo400WithoutLeakingDetails() throws Exception {
        when(enabled.readService().listOAuthClients(USER_ID, "INVALID", null))
                .thenThrow(new BoardPortalBadRequestException("INVALID_STATUS"));
        when(enabled.readService().listOAuthClients(USER_ID, null, "invalid-cursor"))
                .thenThrow(new BoardPortalBadRequestException("INVALID_CURSOR"));
        String token = bearer(enabled, admin("board:oauth:client:query"));

        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .param("status", "INVALID")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("INVALID_STATUS"));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .param("cursor", "invalid-cursor")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("INVALID_CURSOR"));
    }

    @Test
    void approvedAdminAndMeReadsReturn200WithNoStoreHeaders() throws Exception {
        BoardPortalReadEnvelope<BoardPortalOAuthFamilyView> emptyPage =
                new BoardPortalReadEnvelope<>(List.of(), 100, false, null);
        when(enabled.readService().listOAuthFamilies(USER_ID, TENANT_ID, "ACTIVE", null))
                .thenReturn(emptyPage);
        when(enabled.readService().getConnector(USER_ID, TENANT_ID))
                .thenReturn(new BoardPortalConnectorView(
                        TENANT_ID, 11L, "NOT_CONNECTED", "BOARD_FREE",
                        null, null, null, List.of(), null, null, null,
                        0L, "CURRENT_READ_COMPLETE"));

        enabled.mockMvc().perform(get("/business/independent-board/oauth/families")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(enabled,
                                admin("board:oauth:family:query"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.limit").value(100))
                .andExpect(jsonPath("$.data.records").isArray());
        enabled.mockMvc().perform(get("/my/independent-board/connector")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", bearer(enabled,
                                loginUser(USER_ID, "user", Set.of(
                                        "my:independent-board:connector:view"), "user"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.uiState").value("NOT_CONNECTED"));

        verify(enabled.readService()).listOAuthFamilies(
                USER_ID, TENANT_ID, "ACTIVE", null);
        verify(enabled.readService()).getConnector(USER_ID, TENANT_ID);
    }

    @Test
    void scopedBusinessFailuresKeepTransportAndBodyStatusesAligned() throws Exception {
        when(enabled.readService().getConnector(USER_ID, TENANT_ID))
                .thenThrow(new BoardPortalForbiddenException("TENANT_MEMBERSHIP_REQUIRED"));
        when(enabled.readService().listOAuthClients(USER_ID, "ACTIVE", null))
                .thenThrow(new BoardPortalDataDriftException("CLIENT_SCOPE_DRIFT"));
        when(enabled.readService().listConnectorBindings(USER_ID, TENANT_ID, null, null))
                .thenThrow(new DataAccessResourceFailureException("database detail must not leak"));
        when(enabled.readService().listOAuthFamilies(USER_ID, TENANT_ID, null, null))
                .thenThrow(new IllegalStateException("runtime secret must not leak"));

        enabled.mockMvc().perform(get("/my/independent-board/connector")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", bearer(enabled,
                                loginUser(USER_ID, "user", Set.of(
                                        "my:independent-board:connector:view"), "user"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBERSHIP_REQUIRED"));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/clients")
                        .param("status", "ACTIVE")
                        .header("Authorization", bearer(enabled,
                                admin("board:oauth:client:query"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("PORTAL_READ_DATA_DRIFT"));
        enabled.mockMvc().perform(get("/business/independent-board/connector-bindings")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", bearer(enabled,
                                admin("board:connector:query"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg").value("PORTAL_READ_UNAVAILABLE"))
                .andExpect(jsonPath("$.msg").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("database"))));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/families")
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .header("Authorization", bearer(enabled,
                                admin("board:oauth:family:query"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("PORTAL_READ_FAILED"))
                .andExpect(jsonPath("$.msg").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void heldEndpointsStay404AndWritesStay405ForValidJwt() throws Exception {
        String token = bearer(enabled, admin(
                "board:oauth:client:query", "board:oauth:family:query",
                "board:connector:query"));
        enabled.mockMvc().perform(get("/business/independent-board/oauth/security-events")
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        enabled.mockMvc().perform(get("/my/independent-board/security-receipts")
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
        enabled.mockMvc().perform(post("/business/independent-board/oauth/clients")
                        .header("Authorization", token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));

        verifyNoInteractions(enabled.readService());
    }

    @Test
    void candidateBoundaryLeavesExistingUnsupportedMethodSemanticsUntouched() throws Exception {
        disabled.mockMvc().perform(post("/legacy/probe")
                        .header("Authorization", bearer(disabled,
                                loginUser(USER_ID, "user", Set.of(), "user"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500));

        verifyNoInteractions(disabled.readService());
    }

    private static Harness startHarness(boolean candidateEnabled) {
        AnnotationConfigWebApplicationContext context =
                new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "token.header=Authorization",
                "token.secret=" + TOKEN_SECRET,
                "token.expireTime=30",
                "fbsir.independent-board.portal-candidate.enabled=" + candidateEnabled);
        context.register(
                HttpSecurityTestConfiguration.class,
                IndependentBoardPortalMeReadController.class,
                IndependentBoardPortalAdminReadController.class,
                IndependentBoardPortalReadExceptionHandler.class,
                BoardPortalCandidateBoundaryFilter.class,
                LegacyProbeController.class);
        context.refresh();
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(
                                "springSecurityFilterChain", jakarta.servlet.Filter.class),
                        context.getBean(BoardPortalCandidateBoundaryFilter.class))
                .build();
        return new Harness(
                context,
                mockMvc,
                context.getBean(IndependentBoardPortalReadService.class),
                context.getBean(InMemoryRedisCache.class));
    }

    private static String bearer(Harness harness, LoginUser loginUser) {
        String tokenId = UUID.randomUUID().toString();
        loginUser.setToken(tokenId);
        loginUser.setLoginTime(System.currentTimeMillis());
        loginUser.setExpireTime(System.currentTimeMillis() + 60 * 60 * 1000L);
        harness.redisCache().put(CacheConstants.LOGIN_TOKEN_KEY + tokenId, loginUser);
        return Constants.TOKEN_PREFIX + jwt(tokenId, loginUser.getUsername());
    }

    private static String jwtWithUnknownLogin(String tokenId) {
        return jwt(tokenId, "unknown");
    }

    private static String jwt(String tokenId, String username) {
        return Jwts.builder()
                .claim(Constants.LOGIN_USER_KEY, tokenId)
                .claim(Constants.JWT_USERNAME, username)
                .signWith(SignatureAlgorithm.HS512, TOKEN_SECRET)
                .compact();
    }

    private static LoginUser admin(String... permissions) {
        return loginUser(USER_ID, "admin", Set.of(permissions), "admin");
    }

    private static LoginUser loginUser(
            long userId, String username, Set<String> permissions, String... roleKeys) {
        SysUser user = new SysUser(userId);
        user.setUserName(username);
        user.setPassword("unused-test-password");
        user.setRoles(List.of(roleKeys).stream()
                .map(IndependentBoardPortalReadHttpSecurityIntegrationTest::role)
                .toList());
        return new LoginUser(userId, 1L, user, permissions);
    }

    private static SysRole role(String roleKey) {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        return role;
    }

    @RestController
    static class LegacyProbeController {
        @GetMapping("/legacy/probe")
        AjaxResult get() {
            return AjaxResult.success();
        }
    }

    private record Harness(
            AnnotationConfigWebApplicationContext context,
            MockMvc mockMvc,
            IndependentBoardPortalReadService readService,
            InMemoryRedisCache redisCache) {
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
        IndependentBoardPortalReadService independentBoardPortalReadService() {
            return Mockito.mock(IndependentBoardPortalReadService.class);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }

        @Bean
        static MethodValidationPostProcessor methodValidationPostProcessor() {
            return new MethodValidationPostProcessor();
        }

        @Bean
        SecurityFilterChain testSecurityFilterChain(
                HttpSecurity http,
                JwtAuthenticationTokenFilter jwtFilter,
                AuthenticationEntryPointImpl entryPoint) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .exceptionHandling(exceptions ->
                            exceptions.authenticationEntryPoint(entryPoint))
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
