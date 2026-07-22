package com.wx.fbsir.business.board.plan.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Applies the non-cacheable contract even when security or default-off routing ends first. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IndependentBoardPlanPolicyNoStoreFilter extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of(
            "/business/independent-board/plans",
            "/business/independent-board/plan-policy-revisions",
            "/business/independent-board/plan-policy-receipts");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null && !contextPath.isEmpty()
                && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length()) : requestUri;
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return !PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
        filterChain.doFilter(request, response);
    }
}
