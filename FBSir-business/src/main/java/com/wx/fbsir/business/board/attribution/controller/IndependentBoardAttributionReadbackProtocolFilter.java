package com.wx.fbsir.business.board.attribution.controller;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Exact route, strict JSON and streaming size fence for signed readback. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "authoritative-readback-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionReadbackProtocolFilter
        extends OncePerRequestFilter {
    private static final int MAX_REQUEST_BYTES = 16 * 1024;
    private static final Set<String> REQUEST_FIELDS = Set.of(
            "schemaVersion", "eventId", "receiptId", "eventDigest",
            "issuedAt", "expiresAt", "nonce", "keyId", "signature");
    private static final ObjectMapper STRICT_JSON = new ObjectMapper(
            JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty()
                && uri.startsWith(context)
                ? uri.substring(context.length()) : uri;
        return !IndependentBoardAttributionReadbackController.PATH.equals(path);
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
        if (request.getQueryString() != null) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", "query_string_forbidden");
            return;
        }
        if (request.getHeader(HttpHeaders.CONTENT_ENCODING) != null) {
            reject(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MEDIA_TYPE", "content_encoding_forbidden");
            return;
        }
        try {
            MediaType mediaType =
                    MediaType.parseMediaType(request.getContentType());
            if (!MediaType.APPLICATION_JSON.isCompatibleWith(mediaType)
                    || (mediaType.getCharset() != null
                        && !StandardCharsets.UTF_8.equals(
                            mediaType.getCharset()))) {
                reject(response,
                        HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                        "UNSUPPORTED_MEDIA_TYPE", "media_type_not_supported");
                return;
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            reject(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "UNSUPPORTED_MEDIA_TYPE", "media_type_not_supported");
            return;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        try {
            if (accept != null && !accept.isBlank()
                    && MediaType.parseMediaTypes(accept).stream().noneMatch(
                        value -> value.isCompatibleWith(
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
        if (request.getContentLengthLong() > MAX_REQUEST_BYTES) {
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "request_body_too_large");
            return;
        }
        byte[] body;
        try {
            body = boundedBody(request);
        } catch (PayloadTooLargeException error) {
            reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "request_body_too_large");
            return;
        }
        if (!strictRequestShape(body)) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST", "readback_request_invalid");
            return;
        }
        chain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private byte[] boundedBody(HttpServletRequest request)
            throws IOException, PayloadTooLargeException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int total = 0;
        int count;
        while ((count = request.getInputStream().read(buffer)) != -1) {
            total += count;
            if (total > MAX_REQUEST_BYTES) {
                throw new PayloadTooLargeException();
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private boolean strictRequestShape(byte[] body) {
        try {
            JsonNode root = STRICT_JSON.readTree(body);
            if (root == null || !root.isObject()
                    || root.size() != REQUEST_FIELDS.size()) {
                return false;
            }
            Set<String> names = new HashSet<>();
            root.fieldNames().forEachRemaining(names::add);
            return names.equals(REQUEST_FIELDS)
                    && REQUEST_FIELDS.stream().allMatch(
                        name -> root.path(name).isTextual());
        } catch (IOException error) {
            return false;
        }
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

    private static final class PayloadTooLargeException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    private static final class CachedBodyRequest
            extends HttpServletRequestWrapper {
        private final byte[] body;

        private CachedBodyRequest(
                HttpServletRequest request,
                byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // This internal route is handled synchronously.
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
