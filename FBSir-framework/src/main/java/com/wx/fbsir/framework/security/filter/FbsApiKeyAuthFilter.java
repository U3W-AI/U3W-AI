package com.wx.fbsir.framework.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.service.FbsApiKeyAuthService;
import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * FBS Skill API Key 认证过滤器（#15 安全加固）
 *
 * 拦截 /fbs/skill-api/** 请求，四步校验链：
 * 1. API Key 校验（Header 缺失/Key 不存在 → 401，已禁用 → 403）
 * 2. 时间戳校验（缺失 → 401 TIMESTAMP_MISSING，过期 → 401 TIMESTAMP_EXPIRED）
 * 3. 签名校验（缺失/不匹配 → 401 SIGNATURE_INVALID）
 * 4. 速率限制（超限 → 429 RATE_LIMITED）
 *
 * 注册在 JwtAuthenticationTokenFilter 之前。
 *
 * @author FBSir
 * @date 2026-04-11
 */
@Component
public class FbsApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FbsApiKeyAuthFilter.class);

    private static final String API_KEY_HEADER = "X-FBS-API-Key";
    private static final String TIMESTAMP_HEADER = "X-FBS-Timestamp";
    private static final String SIGNATURE_HEADER = "X-FBS-Signature";
    private static final String SKILL_API_PATH_PREFIX = "/fbs/skill-api/";

    @Autowired
    private FbsApiKeyAuthService apiKeyAuthService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestURI = request.getRequestURI();

        // 只拦截 /fbs/skill-api/** 路径
        if (!requestURI.startsWith(SKILL_API_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 0: 包装 request（立即缓存 body，供签名校验和 Controller 使用）
        RepeatedlyReadRequestWrapper wrappedRequest = new RepeatedlyReadRequestWrapper(request);

        // Step 1: API Key 校验
        String apiKey = wrappedRequest.getHeader(API_KEY_HEADER);
        FbsApiKeyAuthService.ApiKeyCheckResult keyResult = apiKeyAuthService.checkApiKey(apiKey);

        if (!keyResult.isSuccess()) {
            log.warn("Skill API Key 认证失败 uri={}, httpStatus={}, errorCode={}",
                    requestURI, keyResult.getHttpStatus(), keyResult.getErrorCode());
            writeErrorResponse(response, keyResult.getHttpStatus(), keyResult.getErrorCode(), keyResult.getErrorMessage());
            return;
        }

        // Step 2: 时间戳校验（#15 新增）
        String timestamp = wrappedRequest.getHeader(TIMESTAMP_HEADER);
        FbsApiKeyAuthService.TimestampCheckResult tsResult = apiKeyAuthService.verifyTimestamp(timestamp);

        if (!tsResult.isSuccess()) {
            log.warn("Skill API 时间戳校验失败 uri={}, errorCode={}", requestURI, tsResult.getErrorCode());
            writeErrorResponse(response, tsResult.getHttpStatus(), tsResult.getErrorCode(), tsResult.getErrorMessage());
            return;
        }

        // Step 3: 签名校验（#15 新增）
        String signature = wrappedRequest.getHeader(SIGNATURE_HEADER);
        String body = new String(wrappedRequest.getCachedBody(), StandardCharsets.UTF_8);
        FbsApiKey keyEntity = keyResult.getKeyEntity();
        FbsApiKeyAuthService.SignatureCheckResult sigResult =
                apiKeyAuthService.verifySignature(keyEntity.getApiKey(), timestamp, body, signature);

        if (!sigResult.isSuccess()) {
            log.warn("Skill API 签名校验失败 uri={}, errorCode={}", requestURI, sigResult.getErrorCode());
            writeErrorResponse(response, sigResult.getHttpStatus(), sigResult.getErrorCode(), sigResult.getErrorMessage());
            return;
        }

        // Step 4: 认证成功（速率限制已在 checkApiKey 中完成）
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                keyEntity, // principal：API Key 实体
                null,      // credentials
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_API"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 传递 wrappedRequest，Controller 可正常读取 body
        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * 写入错误响应（JSON 格式，与 AjaxResult 风格一致）
     */
    private void writeErrorResponse(HttpServletResponse response, int httpStatus,
                                     String errorCode, String errorMessage) throws IOException {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // AjaxResult 风格：{code, msg, data}
        var errorBody = java.util.Map.of(
                "code", httpStatus,
                "msg", errorMessage
        );
        response.getWriter().write(objectMapper.writeValueAsString(errorBody));
        response.getWriter().flush();
    }
}
