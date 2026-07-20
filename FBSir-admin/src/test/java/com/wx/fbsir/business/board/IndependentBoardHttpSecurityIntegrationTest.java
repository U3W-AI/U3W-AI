package com.wx.fbsir.business.board;

import com.wx.fbsir.business.board.controller.IndependentBoardAdminController;
import com.wx.fbsir.business.board.controller.IndependentBoardMeController;
import com.wx.fbsir.business.board.domain.BoardEnterpriseMemberScope;
import com.wx.fbsir.business.board.domain.BoardEntitlementReceipt;
import com.wx.fbsir.business.board.domain.BoardProductEntitlement;
import com.wx.fbsir.business.board.domain.BoardProductPlan;
import com.wx.fbsir.business.board.domain.BoardUsageOperation;
import com.wx.fbsir.business.board.dto.BoardConnectorBindingKey;
import com.wx.fbsir.business.board.mapper.IndependentBoardMapper;
import com.wx.fbsir.business.board.service.BoardConnectorBindingPort;
import com.wx.fbsir.business.board.service.IndependentBoardDashboardService;
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
import java.time.LocalDate;
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
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private static BoardConnectorBindingPort connectorBindingPort;
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
        connectorBindingPort = context.getBean(BoardConnectorBindingPort.class);
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
        reset(mapper, connectorBindingPort);
        redisCache.clear();
        when(connectorBindingPort.selectAuthoritativeCurrentBindingKeys(any(), any()))
                .thenReturn(Set.of());
        when(connectorBindingPort.hasAuthoritativeCurrentBinding(
                any(), any(), any(), any(), Mockito.anyBoolean())).thenReturn(false);
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

        verify(mapper, never()).selectActiveContext(any(), any());
    }

    @Test
    void contextsAndDashboardAreBothUnavailableWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/my/independent-board/contexts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/my/independent-board/dashboard")
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
        mockMvc.perform(get("/my/independent-board/meeting-reservations/meeting-001")
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        verify(mapper, never()).selectActiveContextsByUser(any());
        verify(mapper, never()).selectActiveContext(any(), any());
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

        verify(mapper, never()).selectActiveContext(any(), any());
    }

    @Test
    void ordinaryUserCanReadOnlyTheEnterpriseBoundToTheJwtPrincipal() throws Exception {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID))
                .thenReturn(member(TENANT_ID, 11L, USER_ID));

        mockMvc.perform(get("/my/independent-board/entitlement")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.effectivePlanCode").value("BOARD_FREE"));

        verify(mapper).selectActiveContext(TENANT_ID, USER_ID);
    }

    @Test
    void ordinaryUserCrossTenantRequestFailsClosedAtTheMembershipBoundary() throws Exception {
        long foreignTenantId = 99L;
        when(mapper.selectActiveContext(foreignTenantId, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/my/independent-board/entitlement")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(foreignTenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));

        verify(mapper).selectActiveContext(foreignTenantId, USER_ID);
        verify(mapper, never()).selectEntitlement(any(), any(), any(), any());
    }

    @Test
    void inactiveEnterpriseCurrentReadClosesAllMeSurfacesBeforeAnyWrite() throws Exception {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(null);
        String authorization = bearer(loginUser(USER_ID, "member", Set.of(), "user"));

        mockMvc.perform(get("/my/independent-board/entitlement")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));
        mockMvc.perform(post("/my/independent-board/meeting-reservations")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"operationId\":\"inactive-001\","
                                + "\"agendaCount\":1,\"seatCount\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));
        mockMvc.perform(get("/my/independent-board/dashboard")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));
        mockMvc.perform(get("/my/independent-board/meeting-reservations/inactive-001")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));

        verify(mapper, times(3)).selectActiveContext(TENANT_ID, USER_ID);
        verify(mapper).selectActiveContextForUpdate(TENANT_ID, USER_ID);
        verify(mapper, never()).selectEntitlement(any(), any(), any(), any());
        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).selectOperation(any(), any());
        verify(mapper, never()).insertOperation(any());
        verify(mapper, never()).prepareUsageBudget(
                any(), any(), any(), any(), any(), any());
        verify(mapper, never()).reserveOneMeeting(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void contextsIgnoreClientIdentityParametersAndUseOnlyTheJwtUser() throws Exception {
        when(mapper.selectActiveContextsByUser(USER_ID)).thenReturn(List.of(
                context(TENANT_ID, 11L, USER_ID, "福帮手", "MEMBER")));

        mockMvc.perform(get("/my/independent-board/contexts")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("userId", "999")
                        .param("memberId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data[0].memberId").value(11L))
                .andExpect(jsonPath("$.data[0].tenantName").value("福帮手"))
                .andExpect(jsonPath("$.data[0].memberRole").value("MEMBER"))
                .andExpect(jsonPath("$.data[0].userId").doesNotExist());

        verify(mapper).selectActiveContextsByUser(USER_ID);
        verify(mapper, never()).selectActiveContextsByUser(999L);
    }

    @Test
    void dashboardReturnsOnlyTheJwtUsersSelectedContextAndRecentMeetings() throws Exception {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, 11L, USER_ID, "福帮手", "MEMBER"));
        when(mapper.selectRecentOperationsByTenantAndUser(
                TENANT_ID, USER_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of(operation(TENANT_ID, USER_ID, "meeting-001")));

        mockMvc.perform(get("/my/independent-board/dashboard")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.context.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.entitlement.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.entitlement.effectivePlanCode").value("BOARD_FREE"))
                .andExpect(jsonPath("$.data.recentMeetings.length()").value(1))
                .andExpect(jsonPath("$.data.recentMeetings[0].operationId").value("meeting-001"))
                .andExpect(jsonPath("$.data.recentMeetings[0].requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.connectorState").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.data.webhookState").value("COMING_SOON"))
                .andExpect(jsonPath("$.data.watchState").value("COMING_SOON"));

        verify(mapper).selectRecentOperationsByTenantAndUser(
                TENANT_ID, USER_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC);
    }

    @Test
    void dashboardRejectsCrossTenantBeforeEntitlementAndHistoryReads() throws Exception {
        long foreignTenantId = 99L;
        when(mapper.selectActiveContext(foreignTenantId, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/my/independent-board/dashboard")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(foreignTenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));

        verify(mapper, never()).selectActivePlan(any(), any());
        verify(mapper, never()).selectRecentOperationsByTenantAndUser(any(), any(), any(), any());
    }

    @Test
    void exactMeetingReservationReadUsesJwtScopeAndReturnsTheSafeProjection() throws Exception {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, 11L, USER_ID, "福帮手", "MEMBER"));
        when(mapper.selectOperation(TENANT_ID, "meeting-001"))
                .thenReturn(operation(TENANT_ID, USER_ID, "meeting-001"));

        mockMvc.perform(get("/my/independent-board/meeting-reservations/meeting-001")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID))
                        .param("userId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(true))
                .andExpect(jsonPath("$.data.meeting.operationId").value("meeting-001"))
                .andExpect(jsonPath("$.data.meeting.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.meeting.effectivePlanCode").value("BOARD_FREE"))
                .andExpect(jsonPath("$.data.meeting.bucketDate").value("2026-07-20"))
                .andExpect(jsonPath("$.data.meeting.requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.meeting.userId").doesNotExist());

        verify(mapper).selectOperation(TENANT_ID, "meeting-001");
    }

    @Test
    void exactMeetingReservationReadDoesNotRevealMissingOrAnotherUsersOperation() throws Exception {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, 11L, USER_ID, "福帮手", "MEMBER"));
        when(mapper.selectOperation(TENANT_ID, "missing-001")).thenReturn(null);
        when(mapper.selectOperation(TENANT_ID, "private-001"))
                .thenReturn(operation(TENANT_ID, 999L, "private-001"));

        String authorization = bearer(loginUser(USER_ID, "member", Set.of(), "user"));
        mockMvc.perform(get("/my/independent-board/meeting-reservations/missing-001")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(false))
                .andExpect(jsonPath("$.data.meeting").value(nullValue()));
        mockMvc.perform(get("/my/independent-board/meeting-reservations/private-001")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(false))
                .andExpect(jsonPath("$.data.meeting").value(nullValue()));
    }

    @Test
    void exactMeetingReservationReadDoesNotExposeAnotherProductOrMetric() throws Exception {
        when(mapper.selectActiveContext(TENANT_ID, USER_ID)).thenReturn(
                context(TENANT_ID, 11L, USER_ID, "Tenant", "MEMBER"));
        BoardUsageOperation otherProduct = operation(TENANT_ID, USER_ID, "product-001");
        otherProduct.setProductCode("ANOTHER_PRODUCT");
        BoardUsageOperation otherMetric = operation(TENANT_ID, USER_ID, "metric--001");
        otherMetric.setMetricCode("ANOTHER_METRIC");
        when(mapper.selectOperation(TENANT_ID, "product-001")).thenReturn(otherProduct);
        when(mapper.selectOperation(TENANT_ID, "metric--001")).thenReturn(otherMetric);

        String authorization = bearer(loginUser(USER_ID, "member", Set.of(), "user"));
        mockMvc.perform(get("/my/independent-board/meeting-reservations/product-001")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(false))
                .andExpect(jsonPath("$.data.meeting").value(nullValue()));
        mockMvc.perform(get("/my/independent-board/meeting-reservations/metric--001")
                        .header("Authorization", authorization)
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.found").value(false))
                .andExpect(jsonPath("$.data.meeting").value(nullValue()));
    }

    @Test
    void exactMeetingReservationReadRejectsCrossTenantBeforeOperationLookup() throws Exception {
        long foreignTenantId = 99L;
        when(mapper.selectActiveContext(foreignTenantId, USER_ID)).thenReturn(null);

        mockMvc.perform(get("/my/independent-board/meeting-reservations/meeting-001")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(foreignTenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));

        verify(mapper, never()).selectOperation(any(), any());
    }

    @Test
    void exactMeetingReservationReadRejectsInvalidOperationId() throws Exception {
        mockMvc.perform(get("/my/independent-board/meeting-reservations/short")
                        .header("Authorization", bearer(loginUser(USER_ID, "member", Set.of(), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        verify(mapper, never()).selectActiveContext(any(), any());
        verify(mapper, never()).selectOperation(any(), any());
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

    @Test
    void administratorEntitlementQueryReturnsOnlyTheSafeManagementFields() throws Exception {
        BoardProductEntitlement entitlement = entitlement();
        when(mapper.selectEntitlementsByTenant(
                TENANT_ID, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(entitlement));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());

        mockMvc.perform(get("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:query"), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data[0].memberId").value(11L))
                .andExpect(jsonPath("$.data[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.data[0].planCode").value("BOARD_VIP"))
                .andExpect(jsonPath("$.data[0].entitlementStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].activationState").value("PENDING_CONNECTOR"))
                .andExpect(jsonPath("$.data[0].validFrom").exists())
                .andExpect(jsonPath("$.data[0].validUntil").exists())
                .andExpect(jsonPath("$.data[0].version").value(1L))
                .andExpect(jsonPath("$.data[0].updatedAt").exists())
                .andExpect(jsonPath("$.data[0].status").doesNotExist())
                .andExpect(jsonPath("$.data[0].createdAt").doesNotExist())
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].productCode").doesNotExist())
                .andExpect(jsonPath("$.data[0].connectorBindingId").doesNotExist())
                .andExpect(jsonPath("$.data[0].connectorVerifiedAt").doesNotExist());
    }

    @Test
    void administratorReadbackShowsActiveOnlyForAuthoritativeConnectorBinding() throws Exception {
        BoardProductEntitlement entitlement = entitlement();
        when(mapper.selectEntitlementsByTenant(
                TENANT_ID, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(List.of(entitlement));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(connectorBindingPort.selectAuthoritativeCurrentBindingKeys(
                TENANT_ID, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(Set.of(new BoardConnectorBindingKey(
                        TENANT_ID, 11L, USER_ID, IndependentBoardEntitlementService.PRODUCT_CODE)));

        mockMvc.perform(get("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:query"), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].activationState").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].connectorBindingId").doesNotExist())
                .andExpect(jsonPath("$.data[0].connectorVerifiedAt").doesNotExist());

        verify(connectorBindingPort).selectAuthoritativeCurrentBindingKeys(
                TENANT_ID, IndependentBoardEntitlementService.PRODUCT_CODE);
        verify(connectorBindingPort, never()).hasAuthoritativeCurrentBinding(
                any(), any(), any(), any(), Mockito.anyBoolean());
    }

    @Test
    void entitlementGrantRequiresBothGlobalAdminRoleAndFinePermission() throws Exception {
        String body = "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                + "\"planCode\":\"BOARD_VIP\",\"validUntil\":null,\"expectedVersion\":0}";

        mockMvc.perform(post("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "member", Set.of("board:entitlement:grant"), "user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of(), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(mapper, never()).selectExactActiveMemberForUpdate(any(), any(), any());
        verify(mapper, never()).insertEntitlement(any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void globalAdminCanGrantEntitlementAndReceivesTheSafeProjection() throws Exception {
        when(mapper.selectExactActiveMemberForUpdate(TENANT_ID, 11L, USER_ID))
                .thenReturn(member(TENANT_ID, 11L, USER_ID));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        when(mapper.selectEntitlementForUpdate(
                TENANT_ID, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(null);
        when(mapper.insertEntitlement(any())).thenReturn(1);
        when(mapper.insertEntitlementReceipt(any())).thenReturn(1);

        mockMvc.perform(post("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:grant"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                                + "\"planCode\":\"BOARD_VIP\","
                                + "\"validUntil\":\"2030-01-01T00:00:00\","
                                + "\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.memberId").value(11L))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.planCode").value("BOARD_VIP"))
                .andExpect(jsonPath("$.data.entitlementStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.activationState").value("PENDING_CONNECTOR"))
                .andExpect(jsonPath("$.data.validFrom").exists())
                .andExpect(jsonPath("$.data.validUntil").value("2030-01-01T00:00:00"))
                .andExpect(jsonPath("$.data.version").value(1L))
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.data.createdAt").doesNotExist())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productCode").doesNotExist())
                .andExpect(jsonPath("$.data.connectorBindingId").doesNotExist())
                .andExpect(jsonPath("$.data.connectorVerifiedAt").doesNotExist());

        verify(mapper).insertEntitlement(any());
        verify(mapper).insertEntitlementReceipt(any());
    }

    @Test
    void inactiveEnterpriseRejectsAdminGrantBeforeAnyWrite() throws Exception {
        when(mapper.selectExactActiveMemberForUpdate(TENANT_ID, 11L, USER_ID)).thenReturn(null);

        mockMvc.perform(post("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:grant"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                                + "\"planCode\":\"BOARD_VIP\","
                                + "\"validUntil\":null,\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("TENANT_MEMBER_USER_SCOPE_INVALID"));

        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).insertEntitlement(any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void entitlementRevokeRequiresBothGlobalAdminRoleAndFinePermission() throws Exception {
        String body = "{\"tenantId\":7,\"memberId\":11,\"userId\":42,\"expectedVersion\":1}";

        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", bearer(loginUser(
                                900L, "member", Set.of("board:entitlement:revoke"), "user")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of(), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void entitlementRevokeRejectsUnknownReasonOrPayloadFieldsBeforeAnySideEffect()
            throws Exception {
        String prefix = "{\"tenantId\":7,\"memberId\":11,\"userId\":42,\"expectedVersion\":1,";
        String token = bearer(loginUser(
                900L, "operator", Set.of("board:entitlement:revoke"), "admin"));

        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prefix + "\"reason\":\"MEMBER_LEFT\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prefix + "\"payload\":{\"secret\":\"x\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"memberId\":11,\"userId\":42}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                                + "\"expectedVersion\":null}"))
                .andExpect(status().isBadRequest());

        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void entitlementRevokeRejectsEveryDuplicateContractFieldBeforeAnySideEffect()
            throws Exception {
        String token = bearer(loginUser(
                900L, "operator", Set.of("board:entitlement:revoke"), "admin"));
        List<String> duplicateBodies = List.of(
                "{\"tenantId\":7,\"tenantId\":8,\"memberId\":11,"
                        + "\"userId\":42,\"expectedVersion\":1}",
                "{\"tenantId\":7,\"memberId\":11,\"memberId\":12,"
                        + "\"userId\":42,\"expectedVersion\":1}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"userId\":43,\"expectedVersion\":1}",
                "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                        + "\"expectedVersion\":1,\"expectedVersion\":2}");

        for (String body : duplicateBodies) {
            mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void entitlementRevokeRejectsTrailingRootTokensBeforeAnySideEffect()
            throws Exception {
        String token = bearer(loginUser(
                900L, "operator", Set.of("board:entitlement:revoke"), "admin"));
        String valid = "{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                + "\"expectedVersion\":1}";

        for (String body : List.of(valid + "{}", valid + "null", valid + "123")) {
            mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        verify(mapper, never()).selectEntitlementForUpdate(any(), any(), any());
        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void globalAdminCanRevokeWithoutAnActiveMemberAndReceivesRevokedProjection()
            throws Exception {
        BoardProductEntitlement current = entitlement();
        when(mapper.selectEntitlementForUpdate(
                TENANT_ID, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(current);
        when(mapper.updateEntitlementIfVersion(any(), any())).thenReturn(1);
        when(mapper.insertEntitlementReceipt(any())).thenReturn(1);

        mockMvc.perform(post("/business/independent-board/entitlements/revoke")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:revoke"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                                + "\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.memberId").value(11L))
                .andExpect(jsonPath("$.data.userId").value(USER_ID))
                .andExpect(jsonPath("$.data.planCode").value("BOARD_VIP"))
                .andExpect(jsonPath("$.data.entitlementStatus").value("REVOKED"))
                .andExpect(jsonPath("$.data.activationState").value("REVOKED"))
                .andExpect(jsonPath("$.data.version").value(2L))
                .andExpect(jsonPath("$.data.validUntil").exists())
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.productCode").doesNotExist())
                .andExpect(jsonPath("$.data.connectorBindingId").doesNotExist())
                .andExpect(jsonPath("$.data.connectorVerifiedAt").doesNotExist());

        verify(mapper).updateEntitlementIfVersion(any(), org.mockito.ArgumentMatchers.eq(1L));
        verify(mapper).insertEntitlementReceipt(any());
        verify(mapper, never()).selectExactActiveMemberForUpdate(any(), any(), any());
    }

    @Test
    void entitlementGrantCannotRestoreARevokedRow() throws Exception {
        when(mapper.selectExactActiveMemberForUpdate(TENANT_ID, 11L, USER_ID))
                .thenReturn(member(TENANT_ID, 11L, USER_ID));
        when(mapper.selectActivePlan(
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.VIP_PLAN)).thenReturn(vipPlan());
        BoardProductEntitlement revoked = entitlement();
        revoked.setStatus("REVOKED");
        when(mapper.selectEntitlementForUpdate(
                TENANT_ID, 11L, IndependentBoardEntitlementService.PRODUCT_CODE))
                .thenReturn(revoked);

        mockMvc.perform(post("/business/independent-board/entitlements")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:grant"), "admin")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":7,\"memberId\":11,\"userId\":42,"
                                + "\"planCode\":\"BOARD_VIP\",\"validUntil\":null,"
                                + "\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("ENTITLEMENT_SCOPE_OR_VERSION_CONFLICT"));

        verify(mapper, never()).updateEntitlementIfVersion(any(), any());
        verify(mapper, never()).insertEntitlementReceipt(any());
    }

    @Test
    void entitlementReceiptAuditRequiresBothGlobalAdminRoleAndFinePermission()
            throws Exception {
        mockMvc.perform(get("/business/independent-board/entitlement-receipts")
                        .header("Authorization", bearer(loginUser(
                                900L, "member", Set.of("board:entitlement:audit"), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/business/independent-board/entitlement-receipts")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of(), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(mapper, never()).selectEntitlementReceiptsByTenant(any());
    }

    @Test
    void globalAdminEntitlementReceiptAuditReturnsTheBoundedSafeEnvelope()
            throws Exception {
        BoardEntitlementReceipt receipt = entitlementReceipt();
        when(mapper.selectEntitlementReceiptsByTenant(TENANT_ID)).thenReturn(List.of(receipt));

        mockMvc.perform(get("/business/independent-board/entitlement-receipts")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:entitlement:audit"), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.limit").value(500))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].receiptId").value("receipt-001"))
                .andExpect(jsonPath("$.data.records[0].tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.records[0].actorUserId").value(900L))
                .andExpect(jsonPath("$.data.records[0].targetMemberId").value(11L))
                .andExpect(jsonPath("$.data.records[0].action").value("ENTITLEMENT_REVOKED"))
                .andExpect(jsonPath("$.data.records[0].evidenceLevel").value("ACTION_COMPLETED"))
                .andExpect(jsonPath("$.data.records[0].createdAt").exists())
                .andExpect(jsonPath("$.data.records[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].payloadDigest").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].productCode").doesNotExist());

        verify(mapper).selectEntitlementReceiptsByTenant(TENANT_ID);
    }

    @Test
    void operationAuditRequiresBothGlobalAdminRoleAndFinePermission() throws Exception {
        mockMvc.perform(get("/business/independent-board/operations")
                        .header("Authorization", bearer(loginUser(
                                900L, "member", Set.of("board:operation:audit"), "user")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(get("/business/independent-board/operations")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of(), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));

        verify(mapper, never()).selectOperationsByTenant(any(), any(), any());
    }

    @Test
    void globalAdminOperationAuditReturnsTheBoundedSafeEnvelope() throws Exception {
        BoardUsageOperation operation = operation(TENANT_ID, USER_ID, "audit-001");
        operation.setUpdateTime(new Date(1_790_000_001_000L));
        operation.setCompletedAt(new Date(1_790_000_002_000L));
        when(mapper.selectOperationsByTenant(
                TENANT_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC))
                .thenReturn(List.of(operation));

        mockMvc.perform(get("/business/independent-board/operations")
                        .header("Authorization", bearer(loginUser(
                                900L, "operator", Set.of("board:operation:audit"), "admin")))
                        .param("tenantId", String.valueOf(TENANT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.limit").value(500))
                .andExpect(jsonPath("$.data.truncated").value(false))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].operationId").value("audit-001"))
                .andExpect(jsonPath("$.data.records[0].tenantId").value(TENANT_ID))
                .andExpect(jsonPath("$.data.records[0].memberId").value(11L))
                .andExpect(jsonPath("$.data.records[0].userId").value(USER_ID))
                .andExpect(jsonPath("$.data.records[0].status").value("RESERVED"))
                .andExpect(jsonPath("$.data.records[0].effectivePlanCode").value("BOARD_FREE"))
                .andExpect(jsonPath("$.data.records[0].bucketDate").value("2026-07-20"))
                .andExpect(jsonPath("$.data.records[0].agendaCount").value(3))
                .andExpect(jsonPath("$.data.records[0].seatCount").value(2))
                .andExpect(jsonPath("$.data.records[0].remainingCount").value(0))
                .andExpect(jsonPath("$.data.records[0].createdAt").exists())
                .andExpect(jsonPath("$.data.records[0].updatedAt").exists())
                .andExpect(jsonPath("$.data.records[0].completedAt").exists())
                .andExpect(jsonPath("$.data.records[0].id").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].requestDigest").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].productCode").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].metricCode").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].units").doesNotExist());

        verify(mapper).selectOperationsByTenant(
                TENANT_ID,
                IndependentBoardEntitlementService.PRODUCT_CODE,
                IndependentBoardEntitlementService.MEETING_METRIC);
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

    private static BoardEnterpriseMemberScope context(
            long tenantId, long memberId, long userId, String tenantName, String memberRole) {
        BoardEnterpriseMemberScope context = member(tenantId, memberId, userId);
        context.setTenantName(tenantName);
        context.setMemberRole(memberRole);
        return context;
    }

    private static BoardUsageOperation operation(long tenantId, long userId, String operationId) {
        BoardUsageOperation operation = new BoardUsageOperation();
        operation.setOperationId(operationId);
        operation.setTenantId(tenantId);
        operation.setMemberId(11L);
        operation.setUserId(userId);
        operation.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        operation.setMetricCode(IndependentBoardEntitlementService.MEETING_METRIC);
        operation.setStatus("RESERVED");
        operation.setEffectivePlanCode("BOARD_FREE");
        operation.setAgendaCount(3);
        operation.setSeatCount(2);
        operation.setRemainingCount(0);
        operation.setBucketDate(LocalDate.of(2026, 7, 20));
        operation.setCreateTime(new Date(1_790_000_000_000L));
        operation.setRequestDigest("a".repeat(64));
        return operation;
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

    private static BoardProductPlan vipPlan() {
        BoardProductPlan plan = new BoardProductPlan();
        plan.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        plan.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        plan.setVip(true);
        plan.setConnectorRequired(true);
        plan.setDailyMeetingLimit(5);
        plan.setAgendaLimit(30);
        plan.setSeatLimit(null);
        plan.setSecretaryEnabled(true);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private static BoardProductEntitlement entitlement() {
        long now = System.currentTimeMillis();
        BoardProductEntitlement entitlement = new BoardProductEntitlement();
        entitlement.setId(88L);
        entitlement.setTenantId(TENANT_ID);
        entitlement.setMemberId(11L);
        entitlement.setUserId(USER_ID);
        entitlement.setProductCode(IndependentBoardEntitlementService.PRODUCT_CODE);
        entitlement.setPlanCode(IndependentBoardEntitlementService.VIP_PLAN);
        entitlement.setStatus("ACTIVE");
        entitlement.setConnectorBindingId("must-not-leak");
        entitlement.setConnectorVerifiedAt(new Date(now - 60_000L));
        entitlement.setValidFrom(new Date(now - 3_600_000L));
        entitlement.setValidUntil(new Date(now + 3_600_000L));
        entitlement.setVersion(1L);
        entitlement.setCreatedAt(new Date(now - 7_200_000L));
        entitlement.setUpdatedAt(new Date(now - 30_000L));
        return entitlement;
    }

    private static BoardEntitlementReceipt entitlementReceipt() {
        BoardEntitlementReceipt receipt = new BoardEntitlementReceipt();
        receipt.setReceiptId("receipt-001");
        receipt.setTenantId(TENANT_ID);
        receipt.setActorUserId(900L);
        receipt.setTargetMemberId(11L);
        receipt.setAction("ENTITLEMENT_REVOKED");
        receipt.setPayloadDigest("a".repeat(64));
        receipt.setEvidenceLevel("ACTION_COMPLETED");
        receipt.setCreatedAt(new Date(1_790_000_003_000L));
        return receipt;
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
        BoardConnectorBindingPort connectorBindingPort() {
            return Mockito.mock(BoardConnectorBindingPort.class);
        }

        @Bean
        IndependentBoardEntitlementService entitlementService(
                IndependentBoardMapper mapper,
                BoardConnectorBindingPort connectorBindingPort) {
            return new IndependentBoardEntitlementService(mapper, connectorBindingPort);
        }

        @Bean
        IndependentBoardDashboardService dashboardService(
                IndependentBoardMapper mapper,
                IndependentBoardEntitlementService entitlementService) {
            return new IndependentBoardDashboardService(mapper, entitlementService);
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
                IndependentBoardDashboardService dashboardService,
                IndependentBoardEntitlementService entitlementService,
                IndependentBoardMeetingService meetingService) {
            return new IndependentBoardMeController(
                    dashboardService, entitlementService, meetingService);
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
