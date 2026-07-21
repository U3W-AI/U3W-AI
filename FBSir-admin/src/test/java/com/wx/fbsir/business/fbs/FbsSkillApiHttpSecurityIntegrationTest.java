package com.wx.fbsir.business.fbs;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.controller.skillapi.FbsSkillApiController;
import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.fbs.service.SkillConsumeService;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.core.redis.RedisCache;
import com.wx.fbsir.framework.config.SecurityConfig;
import com.wx.fbsir.framework.config.properties.PermitAllUrlProperties;
import com.wx.fbsir.framework.security.filter.FbsApiKeyAuthFilter;
import com.wx.fbsir.framework.security.filter.JwtAuthenticationTokenFilter;
import com.wx.fbsir.framework.security.handle.AuthenticationEntryPointImpl;
import com.wx.fbsir.framework.security.handle.LogoutSuccessHandlerImpl;
import com.wx.fbsir.framework.web.exception.GlobalExceptionHandler;
import com.wx.fbsir.framework.web.service.TokenService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proves user binding and retired mutation routes through the production API-key security chain. */
class FbsSkillApiHttpSecurityIntegrationTest {
    private static final String API_KEY = "fbs_test_points_closure_key_20260722";
    private static final String BODY = "{\"userId\":999,\"source\":\"ADMIN_GRANT\","
            + "\"amount\":1000000,\"usageRecordId\":\"unsafe-001\"}";

    private static AnnotationConfigWebApplicationContext context;
    private static MockMvc mockMvc;
    private static FbsApiKeyMapper apiKeyMapper;
    private static IPointsService pointsService;
    private static SkillConsumeService skillConsumeService;
    private static RightsCheckService rightsCheckService;
    private static FbsSkillUsageRecordMapper usageRecordMapper;

    @BeforeAll
    static void startProductionSecurityChain() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                context,
                "token.header=Authorization",
                "token.secret=fbs-skill-points-http-security-test-secret-20260722",
                "token.expireTime=30");
        context.register(
                TestConfiguration.class,
                SecurityConfig.class,
                FbsSkillApiController.class);
        context.refresh();

        apiKeyMapper = context.getBean(FbsApiKeyMapper.class);
        pointsService = context.getBean(IPointsService.class);
        skillConsumeService = context.getBean(SkillConsumeService.class);
        rightsCheckService = context.getBean(RightsCheckService.class);
        usageRecordMapper = context.getBean(FbsSkillUsageRecordMapper.class);
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
        reset(apiKeyMapper, pointsService, skillConsumeService,
                rightsCheckService, usageRecordMapper);
    }

    @Test
    void validKeyTimestampAndHmacReachTheControllerAndReturnGone() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/points/earn")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, BODY))
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.msg").value("SKILL_POINTS_EARN_DISABLED"));

        verifyNoInteractions(pointsService);
    }

    @Test
    void validHmacWithMalformedLegacyBodyStillReturnsTheStableTombstone() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String timestamp = String.valueOf(System.currentTimeMillis());
        String malformedBody = "{not-json";

        mockMvc.perform(post("/fbs/skill-api/points/earn")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, malformedBody))
                        .contentType("application/json")
                        .content(malformedBody))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.msg").value("SKILL_POINTS_EARN_DISABLED"));

        verifyNoInteractions(pointsService);
    }

    @Test
    void boundKeyCanConsumeOnlyForItsBoundUser() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        when(skillConsumeService.consume(
                eq(42L), eq("pack-board"), eq("board-skill"), eq("usage-001"),
                eq("WORKBUDDY"), eq("host-session-001"), isNull()))
                .thenReturn(ConsumeResult.success("usage-001", 90));
        String body = "{\"userId\":42,\"packCode\":\"pack-board\","
                + "\"skillCode\":\"board-skill\",\"usageRecordId\":\"usage-001\","
                + "\"hostSessionId\":\"host-session-001\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/usage/consume")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(skillConsumeService).consume(
                eq(42L), eq("pack-board"), eq("board-skill"), eq("usage-001"),
                eq("WORKBUDDY"), eq("host-session-001"), isNull());
    }

    @Test
    void unboundKeyCannotFallbackToConsumeRequestUserId() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        key.setUserId(null);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String body = "{\"userId\":42,\"packCode\":\"pack-board\","
                + "\"skillCode\":\"board-skill\",\"usageRecordId\":\"usage-002\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/usage/consume")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg")
                        .value("SKILL_API_KEY_USER_BINDING_REQUIRED"));

        verifyNoInteractions(skillConsumeService);
    }

    @Test
    void boundKeyCannotConsumeForAConflictingRequestUserId() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String body = "{\"userId\":999,\"packCode\":\"pack-board\","
                + "\"skillCode\":\"board-skill\",\"usageRecordId\":\"usage-003\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/usage/consume")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("SKILL_API_KEY_USER_MISMATCH"));

        verifyNoInteractions(skillConsumeService);
    }

    @Test
    void unboundKeyCannotReadAnotherUsersPointsThroughUserInfo() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        key.setUserId(null);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String body = "{\"userId\":42}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/user/info")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg")
                        .value("SKILL_API_KEY_USER_BINDING_REQUIRED"));

        verifyNoInteractions(pointsService);
    }

    @Test
    void unboundKeyCannotCheckRightsForARequestBodyUser() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        key.setUserId(null);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String body = "{\"userId\":42,\"packCode\":\"pack-board\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/rights/check")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg")
                        .value("SKILL_API_KEY_USER_BINDING_REQUIRED"));

        verifyNoInteractions(rightsCheckService);
    }

    @Test
    void unboundKeyCannotStartAUsageRecordForARequestBodyUser() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        key.setUserId(null);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String body = "{\"userId\":42,\"packCode\":\"pack-board\"," +
                "\"skillCode\":\"board-skill\",\"usageRecordId\":\"usage-start-001\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/usage/start")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg")
                        .value("SKILL_API_KEY_USER_BINDING_REQUIRED"));

        verifyNoInteractions(usageRecordMapper);
    }

    @Test
    void boundKeyCannotReplayAStartRecordOwnedByAnotherUser() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        FbsSkillUsageRecord otherUserRecord = new FbsSkillUsageRecord();
        otherUserRecord.setUsageRecordId("usage-start-002");
        otherUserRecord.setUserId(999L);
        when(usageRecordMapper.selectByRecordId("usage-start-002"))
                .thenReturn(otherUserRecord);
        String body = "{\"userId\":42,\"packCode\":\"pack-board\"," +
                "\"skillCode\":\"board-skill\",\"usageRecordId\":\"usage-start-002\"}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(post("/fbs/skill-api/usage/start")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("SKILL_API_KEY_USER_MISMATCH"));
    }

    @Test
    void unboundKeyCannotEndAnExistingUsageRecord() throws Exception {
        FbsApiKey key = activeKey(API_KEY);
        key.setUserId(null);
        when(apiKeyMapper.selectActiveByKey(API_KEY)).thenReturn(key);
        String body = "{\"status\":1}";
        String timestamp = String.valueOf(System.currentTimeMillis());

        mockMvc.perform(put("/fbs/skill-api/usage/end/usage-end-001")
                        .header("X-FBS-API-Key", API_KEY)
                        .header("X-FBS-Timestamp", timestamp)
                        .header("X-FBS-Signature", hmac(API_KEY, timestamp, body))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg")
                        .value("SKILL_API_KEY_USER_BINDING_REQUIRED"));

        verifyNoInteractions(usageRecordMapper);
    }

    @Test
    void unknownKeyIsRejectedByTheApiKeyFilterBeforeTheController() throws Exception {
        String unknownKey = "fbs_unknown_points_closure_key";

        mockMvc.perform(post("/fbs/skill-api/points/earn")
                        .header("X-FBS-API-Key", unknownKey)
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("API Key 无效"));

        verifyNoInteractions(pointsService);
    }

    @Test
    void disabledKeyIsRejectedByTheApiKeyFilterBeforeTheController() throws Exception {
        FbsApiKey disabled = activeKey(API_KEY);
        disabled.setStatus(0);
        when(apiKeyMapper.selectByApiKey(API_KEY)).thenReturn(disabled);

        mockMvc.perform(post("/fbs/skill-api/points/earn")
                        .header("X-FBS-API-Key", API_KEY)
                        .contentType("application/json")
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("API Key 已禁用"));

        verifyNoInteractions(pointsService);
    }

    private static FbsApiKey activeKey(String apiKey) {
        FbsApiKey key = new FbsApiKey();
        key.setApiKey(apiKey);
        key.setUserId(42L);
        key.setStatus(1);
        key.setRateLimitPerMin(60);
        return key;
    }

    private static String hmac(String key, String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "\n" + body).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
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
        FbsApiKeyMapper fbsApiKeyMapper() {
            return Mockito.mock(FbsApiKeyMapper.class);
        }

        @Bean
        FbsApiKeyAuthService fbsApiKeyAuthService() {
            return new FbsApiKeyAuthService();
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
            PermitAllUrlProperties properties = Mockito.mock(PermitAllUrlProperties.class);
            when(properties.getUrls()).thenReturn(List.of());
            return properties;
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
        RightsCheckService rightsCheckService() {
            return Mockito.mock(RightsCheckService.class);
        }

        @Bean
        SkillConsumeService skillConsumeService() {
            return Mockito.mock(SkillConsumeService.class);
        }

        @Bean
        FbsSkillUsageRecordMapper usageRecordMapper() {
            return Mockito.mock(FbsSkillUsageRecordMapper.class);
        }

        @Bean
        FbsScenePackMapper scenePackMapper() {
            return Mockito.mock(FbsScenePackMapper.class);
        }

        @Bean
        FbsUserPackMapper userPackMapper() {
            return Mockito.mock(FbsUserPackMapper.class);
        }

        @Bean
        IPointsService pointsService() {
            return Mockito.mock(IPointsService.class);
        }

        @Bean
        GlobalExceptionHandler globalExceptionHandler() {
            return new GlobalExceptionHandler();
        }
    }
}
