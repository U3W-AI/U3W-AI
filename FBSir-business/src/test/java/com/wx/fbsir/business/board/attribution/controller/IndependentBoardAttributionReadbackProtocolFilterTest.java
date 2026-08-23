package com.wx.fbsir.business.board.attribution.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndependentBoardAttributionReadbackProtocolFilterTest {
    private final IndependentBoardAttributionReadbackProtocolFilter filter =
            new IndependentBoardAttributionReadbackProtocolFilter();

    @Test
    void streamsUnknownLengthBodyThroughStrictShapeAndCachesIt()
            throws Exception {
        byte[] body = validBody().getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest source = request(body);
        HttpServletRequest unknownLength = new HttpServletRequestWrapper(source) {
            @Override
            public int getContentLength() {
                return -1;
            }

            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<byte[]> observed = new AtomicReference<>();

        filter.doFilter(unknownLength, response, (request, ignored) ->
                observed.set(request.getInputStream().readAllBytes()));

        assertEquals(200, response.getStatus());
        assertArrayEquals(body, observed.get());
    }

    @Test
    void rejectsUnknownLengthOverflowBeforeControllerBinding()
            throws Exception {
        byte[] body = new byte[16 * 1024 + 1];
        java.util.Arrays.fill(body, (byte) 'a');
        MockHttpServletRequest source = request(body);
        HttpServletRequest unknownLength = new HttpServletRequestWrapper(source) {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();

        filter.doFilter(unknownLength, response,
                (request, ignored) -> called.set(true));

        assertEquals(413, response.getStatus());
        assertFalse(called.get());
        assertTrue(response.getContentAsString().contains(
                "request_body_too_large"));
    }

    @Test
    void rejectsDuplicateUnknownAndNonTextJsonFields() throws Exception {
        assertRejected(
                validBody().replace(
                        "\"signature\":\"\"",
                        "\"signature\":\"\",\"signature\":\"x\""));
        assertRejected(
                validBody().replace(
                        "\"signature\":\"\"",
                        "\"signature\":\"\",\"extra\":\"x\""));
        assertRejected(
                validBody().replace(
                        "\"eventId\":\"",
                        "\"eventId\":1,\"ignored\":\""));
        assertRejected(validBody() + "{}");
    }

    @Test
    void acceptsExactlySixteenKibButRejectsTheNextByte() throws Exception {
        byte[] base = validBody().getBytes(StandardCharsets.UTF_8);
        String exact = validBody()
                + " ".repeat(16 * 1024 - base.length);
        MockHttpServletRequest accepted = request(
                exact.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse acceptedResponse =
                new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(accepted, acceptedResponse,
                (ignoredRequest, ignoredResponse) -> called.set(true));
        assertEquals(200, acceptedResponse.getStatus());
        assertTrue(called.get());

        MockHttpServletRequest rejected = request(
                (exact + " ").getBytes(StandardCharsets.UTF_8));
        assertStatus(rejected, 413);
    }

    @Test
    void rejectsQueryMethodEncodingAndWrongMedia() throws Exception {
        MockHttpServletRequest query = request(
                validBody().getBytes(StandardCharsets.UTF_8));
        query.setQueryString("eventId=forbidden");
        assertStatus(query, 400);

        MockHttpServletRequest get = request(
                validBody().getBytes(StandardCharsets.UTF_8));
        get.setMethod("GET");
        assertStatus(get, 405);

        MockHttpServletRequest encoding = request(
                validBody().getBytes(StandardCharsets.UTF_8));
        encoding.addHeader("Content-Encoding", "gzip");
        assertStatus(encoding, 415);

        MockHttpServletRequest media = request(
                validBody().getBytes(StandardCharsets.UTF_8));
        media.setContentType("text/plain");
        assertStatus(media, 415);
    }

    private void assertRejected(String body) throws Exception {
        MockHttpServletRequest request = request(
                body.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter(request, response,
                (ignoredRequest, ignoredResponse) -> called.set(true));
        assertEquals(400, response.getStatus());
        assertFalse(called.get());
    }

    private void assertStatus(
            MockHttpServletRequest request,
            int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
        });
        assertEquals(status, response.getStatus());
        assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    }

    private MockHttpServletRequest request(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                IndependentBoardAttributionReadbackController.PATH);
        request.setContentType("application/json");
        request.addHeader("Accept", "application/json");
        request.setContent(body);
        return request;
    }

    private String validBody() {
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
}
