package com.wx.fbsir.business.board.attribution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionNoStoreFilter;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionReadbackController;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionReadbackProtocolFilter;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestVerifier;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackResponseV1;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackService;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackAdmission;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.framework.config.SecurityConfig;
import com.wx.fbsir.framework.config.properties.PermitAllUrlProperties;
import com.wx.fbsir.framework.security.filter.FbsApiKeyAuthFilter;
import com.wx.fbsir.framework.security.filter.JwtAuthenticationTokenFilter;
import com.wx.fbsir.framework.security.handle.AuthenticationEntryPointImpl;
import com.wx.fbsir.framework.security.handle.LogoutSuccessHandlerImpl;
import com.wx.fbsir.framework.web.service.TokenService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves the exact readback route through the production security chain. */
class IndependentBoardAttributionReadbackHttpSecurityIntegrationTest {
    private static final String EVENT_ID = "1".repeat(64);
    private static final String RECEIPT_ID = "2".repeat(64);
    private static final String EVENT_DIGEST = "3".repeat(64);

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mockMvc;
    private static IndependentBoardAttributionReadbackService service;
    private static BoardAttributionReadbackRequestVerifier verifier;
    private static IndependentBoardAttributionProperties properties;

    @BeforeAll
    static void startProductionSecurityChain() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "token.header=Authorization",
                "token.secret=w05e-readback-security-chain-secret-20260823",
                "token.expireTime=30",
                "fbsir.independent-board.attribution.authoritative-readback-enabled=true");
        context.register(
                TestConfiguration.class,
                SecurityConfig.class,
                IndependentBoardAttributionReadbackController.class);
        context.refresh();
        service = context.getBean(
                IndependentBoardAttributionReadbackService.class);
        verifier = context.getBean(
                BoardAttributionReadbackRequestVerifier.class);
        properties = context.getBean(
                IndependentBoardAttributionProperties.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(
                        new IndependentBoardAttributionNoStoreFilter(),
                        new IndependentBoardAttributionReadbackProtocolFilter(),
                        context.getBean(
                                "springSecurityFilterChain",
                                jakarta.servlet.Filter.class))
                .build();
    }

    @AfterAll
    static void stopContext() {
        if (context != null) {
            context.close();
        }
    }

    @BeforeEach
    void resetBoundaries() {
        reset(service, verifier);
    }

    @Test
    void exactAnonymousRouteReachesHmacBoundaryWithoutJwt() throws Exception {
        VerifiedBoardAttributionReadbackRequest verified = verified();
        BoardAttributionReadbackResponseV1 unsigned = response(
                "COMMITTED_EXACT", 0, "");
        BoardAttributionReadbackResponseV1 signed = response(
                "COMMITTED_EXACT", 200, "f".repeat(64));
        when(verifier.verify(any(), eq(properties))).thenReturn(verified);
        when(service.read(verified)).thenReturn(unsigned);
        when(verifier.signResponse(
                unsigned, verified, 200, properties)).thenReturn(signed);

        mockMvc.perform(post(
                        IndependentBoardAttributionReadbackController.PATH)
                        .contentType("application/json")
                        .accept("application/json")
                        .content(validBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMITTED_EXACT"))
                .andExpect(jsonPath("$.signature").value("f".repeat(64)));
    }

    @Test
    void adjacentInternalPathStillRequiresJwt() throws Exception {
        mockMvc.perform(post(
                        IndependentBoardAttributionReadbackController.PATH
                                + "-adjacent")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private static VerifiedBoardAttributionReadbackRequest verified() {
        return new VerifiedBoardAttributionReadbackRequest(
                EVENT_ID,
                RECEIPT_ID,
                EVENT_DIGEST,
                Instant.parse("2026-08-23T08:39:30Z"),
                Instant.parse("2026-08-23T08:40:30Z"),
                "4".repeat(64),
                "5".repeat(64),
                "readback-k1");
    }

    private static BoardAttributionReadbackResponseV1 response(
            String status,
            int httpStatus,
            String signature) {
        return new BoardAttributionReadbackResponseV1(
                BoardAttributionReadbackResponseV1.SCHEMA_VERSION,
                status,
                EVENT_ID,
                RECEIPT_ID,
                EVENT_DIGEST,
                true,
                httpStatus,
                "4".repeat(64),
                "5".repeat(64),
                "2026-08-23T08:40:00Z",
                "2026-08-23T08:41:00Z",
                "w05e-readback-test",
                "a".repeat(64),
                false,
                "readback-k1",
                signature);
    }

    private static String validBody() {
        return "{"
                + "\"schemaVersion\":\"v\","
                + "\"eventId\":\"\","
                + "\"receiptId\":\"\","
                + "\"eventDigest\":\"\","
                + "\"issuedAt\":\"\","
                + "\"expiresAt\":\"\","
                + "\"nonce\":\"\","
                + "\"keyId\":\"\","
                + "\"signature\":\"\"}";
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    static class TestConfiguration {
        @Bean
        @SuppressWarnings({"rawtypes", "unchecked"})
        RedisTemplate<Object, Object> redisTemplate() {
            return Mockito.mock(RedisTemplate.class);
        }

        @Bean
        RedisCache redisCache() {
            return Mockito.mock(RedisCache.class);
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
        FbsApiKeyAuthService fbsApiKeyAuthService() {
            return new FbsApiKeyAuthService();
        }

        @Bean
        FbsApiKeyMapper fbsApiKeyMapper() {
            return Mockito.mock(FbsApiKeyMapper.class);
        }

        @Bean
        FbsApiKeyAuthFilter fbsApiKeyAuthFilter() {
            return new FbsApiKeyAuthFilter();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CorsFilter corsFilter() {
            return new CorsFilter(new UrlBasedCorsConfigurationSource());
        }

        @Bean
        PermitAllUrlProperties permitAllUrlProperties() {
            PermitAllUrlProperties value =
                    Mockito.mock(PermitAllUrlProperties.class);
            when(value.getUrls()).thenReturn(List.of());
            return value;
        }

        @Bean
        AuthenticationEntryPointImpl authenticationEntryPoint() {
            return new AuthenticationEntryPointImpl();
        }

        @Bean
        LogoutSuccessHandlerImpl logoutSuccessHandler() {
            return new LogoutSuccessHandlerImpl();
        }

        @Bean
        IndependentBoardAttributionReadbackService readbackService() {
            return Mockito.mock(
                    IndependentBoardAttributionReadbackService.class);
        }

        @Bean
        BoardAttributionReadbackRequestVerifier readbackVerifier() {
            return Mockito.mock(
                    BoardAttributionReadbackRequestVerifier.class);
        }

        @Bean
        IndependentBoardAttributionReadbackAdmission readbackAdmission(
                IndependentBoardAttributionProperties properties) {
            return new IndependentBoardAttributionReadbackAdmission(properties);
        }

        @Bean
        IndependentBoardAttributionProperties attributionProperties() {
            return new IndependentBoardAttributionProperties();
        }
    }
}
