package com.wx.fbsir.business.board.attribution.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Enforces the ingress wire protocol only while the writer route is mounted. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "observation-writer-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionIngressProtocolFilter
        extends OncePerRequestFilter {
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty()
                && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return !IndependentBoardAttributionIngressController.PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            response.setHeader(HttpHeaders.ALLOW, HttpMethod.POST.name());
            reject(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "METHOD_NOT_ALLOWED", "method_not_allowed");
            return;
        }
        String contentType = request.getContentType();
        try {
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(
                    MediaType.parseMediaType(contentType))) {
                reject(response,
                        HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE", "media_type_not_supported");
                return;
            }
        } catch (IllegalArgumentException error) {
            reject(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MEDIA_TYPE", "media_type_not_supported");
            return;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        try {
            if (accept != null && !accept.isBlank()
                    && MediaType.parseMediaTypes(accept).stream().noneMatch(
                        mediaType -> mediaType.isCompatibleWith(
                            MediaType.APPLICATION_JSON))) {
                reject(response, HttpServletResponse.SC_NOT_ACCEPTABLE,
                        "NOT_ACCEPTABLE", "media_type_not_acceptable");
                return;
            }
        } catch (IllegalArgumentException error) {
            reject(response, HttpServletResponse.SC_NOT_ACCEPTABLE,
                    "NOT_ACCEPTABLE", "media_type_not_acceptable");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(
            HttpServletResponse response,
            int status,
            String statusName,
            String reason) throws IOException {
        response.setHeader(
                "Cache-Control",
                "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":\"" + statusName
                        + "\",\"reason\":\"" + reason + "\"}");
    }
}
