package com.wx.fbsir.business.board.portal.controller;

import com.alibaba.fastjson2.JSON;
import com.wx.fbsir.common.constant.HttpStatus;
import com.wx.fbsir.common.core.domain.AjaxResult;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Keeps the candidate path surface deterministic without changing global MVC
 * exception semantics. The filter is deliberately registered after the
 * security chain so authentication remains the first externally visible gate.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BoardPortalCandidateBoundaryFilter extends OncePerRequestFilter {

    private static final Set<String> CANDIDATE_READ_PATHS = Set.of(
            "/business/independent-board/oauth/clients",
            "/business/independent-board/oauth/families",
            "/business/independent-board/connector-bindings",
            "/business/independent-board/tenants",
            "/my/independent-board/connector");
    private static final Set<String> HELD_PATHS = Set.of(
            "/business/independent-board/oauth/security-events",
            "/my/independent-board/security-receipts");

    private final boolean enabled;

    public BoardPortalCandidateBoundaryFilter(
            @Value("${fbsir.independent-board.portal-candidate.enabled:false}")
            boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = applicationPath(request);
        if (HELD_PATHS.contains(path)) {
            render(response, HttpServletResponse.SC_NOT_FOUND,
                    AjaxResult.error(HttpStatus.NOT_FOUND, "NOT_FOUND"));
            return;
        }
        if (!CANDIDATE_READ_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!enabled) {
            render(response, HttpServletResponse.SC_NOT_FOUND,
                    AjaxResult.error(HttpStatus.NOT_FOUND, "NOT_FOUND"));
            return;
        }
        if (!"GET".equals(request.getMethod())) {
            response.setHeader("Allow", "GET");
            render(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    AjaxResult.error(HttpStatus.BAD_METHOD, "METHOD_NOT_ALLOWED"));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private static String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath == null || contextPath.isEmpty()
                ? uri : uri.substring(contextPath.length());
    }

    private static void render(
            HttpServletResponse response, int status, AjaxResult body) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        response.getWriter().write(JSON.toJSONString(body));
    }
}
