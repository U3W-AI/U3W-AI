package com.wx.fbsir.business.board.attribution.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Applies no-store before security, routing or exception handling can end. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IndependentBoardAttributionNoStoreFilter
        extends OncePerRequestFilter {
    private static final Set<String> PATHS = Set.of(
            IndependentBoardAttributionIngressController.PATH,
            "/business/independent-board/attribution/summary");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty()
                && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return !PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
        chain.doFilter(request, response);
    }
}
