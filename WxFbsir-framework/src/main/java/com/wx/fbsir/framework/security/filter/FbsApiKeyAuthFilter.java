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
import java.util.List;

/**
 * FBS Skill API Key 认证过滤器
 *
 * 拦截 /fbs/skill-api/** 请求，校验 X-FBS-API-Key Header。
 * 注册在 JwtAuthenticationTokenFilter 之前。
 *
 * 认证链路：
 * - Header 缺失/Key 不存在 → 401 SKILL_API_KEY_INVALID
 * - Key 已禁用 → 403 SKILL_API_KEY_DISABLED
 * - 速率超限 → 429 SKILL_API_RATE_LIMITED
 * - 通过 → 设置 SecurityContext + chain.doFilter
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@Component
public class FbsApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FbsApiKeyAuthFilter.class);

    private static final String API_KEY_HEADER = "X-FBS-API-Key";
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

        // 读取 X-FBS-API-Key Header
        String apiKey = request.getHeader(API_KEY_HEADER);

        // 调用认证服务
        FbsApiKeyAuthService.ApiKeyCheckResult result = apiKeyAuthService.checkApiKey(apiKey);

        if (!result.isSuccess()) {
            // 认证失败：返回错误 JSON
            log.warn("Skill API Key 认证失败 uri={}, httpStatus={}, errorCode={}",
                    requestURI, result.getHttpStatus(), result.getErrorCode());
            writeErrorResponse(response, result.getHttpStatus(), result.getErrorCode(), result.getErrorMessage());
            return;
        }

        // 认证成功：设置 SecurityContext
        FbsApiKey keyEntity = result.getKeyEntity();
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                keyEntity, // principal：API Key 实体
                null,      // credentials
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_API"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 继续过滤器链
        filterChain.doFilter(request, response);
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
