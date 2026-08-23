package com.wx.fbsir.business.board.attribution.controller;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestV1;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestVerifier;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackResponseV1;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackService;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackAdmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndependentBoardAttributionReadbackControllerTest {
    private static final String EVENT_ID = "1".repeat(64);
    private static final String RECEIPT_ID = "2".repeat(64);
    private static final String EVENT_DIGEST = "3".repeat(64);

    private final IndependentBoardAttributionReadbackService service =
            mock(IndependentBoardAttributionReadbackService.class);
    private final BoardAttributionReadbackRequestVerifier verifier =
            mock(BoardAttributionReadbackRequestVerifier.class);
    private final IndependentBoardAttributionProperties properties =
            new IndependentBoardAttributionProperties();
    private final IndependentBoardAttributionReadbackAdmission admission =
            new IndependentBoardAttributionReadbackAdmission(properties);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(service, verifier);
        mvc = MockMvcBuilders.standaloneSetup(
                        new IndependentBoardAttributionReadbackController(
                                service, admission, verifier, properties))
                .setControllerAdvice(
                        new IndependentBoardAttributionReadbackExceptionHandler())
                .addFilters(new IndependentBoardAttributionNoStoreFilter())
                .build();
    }

    @Test
    void returnsSignedExactNotFoundAndCollisionStatuses() throws Exception {
        assertOutcome("COMMITTED_EXACT", 200, true);
        assertOutcome("NOT_FOUND_AUTHORITATIVE", 404, true);
        assertOutcome("IDENTITY_COLLISION", 409, true);
    }

    @Test
    void returnsSigned503WithRetryAfterWhenPrimaryReadFails()
            throws Exception {
        VerifiedBoardAttributionReadbackRequest verified = verified();
        when(verifier.verify(any(), eq(properties))).thenReturn(verified);
        when(service.read(verified)).thenThrow(
                new DataAccessResourceFailureException("secret-db-error"));
        BoardAttributionReadbackResponseV1 unavailable = unsigned(
                "READBACK_UNAVAILABLE", false);
        when(service.unavailable(verified)).thenReturn(unavailable);
        when(verifier.signResponse(
                unavailable, verified, 503, properties))
                .thenReturn(signed("READBACK_UNAVAILABLE", 503, false));

        mvc.perform(post(IndependentBoardAttributionReadbackController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status")
                        .value("READBACK_UNAVAILABLE"))
                .andExpect(jsonPath("$.httpStatus").value(503))
                .andExpect(jsonPath("$.authoritativeRead").value(false))
                .andExpect(jsonPath("$.signature").value("f".repeat(64)))
                .andExpect(jsonPath("$.productCreditEligible").value(false));
    }

    @Test
    void invalidSignatureIsGeneric400BeforeServiceLookup() throws Exception {
        when(verifier.verify(any(), eq(properties))).thenThrow(
                new IllegalArgumentException("must-not-leak"));

        mvc.perform(post(IndependentBoardAttributionReadbackController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason")
                        .value("readback_request_invalid"));
    }

    @Test
    void unexpectedRuntimeIsGeneric500WithoutInternalDetail()
            throws Exception {
        when(verifier.verify(any(), eq(properties))).thenReturn(verified());
        when(service.read(any())).thenThrow(
                new RuntimeException("must-not-leak"));

        mvc.perform(post(IndependentBoardAttributionReadbackController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.reason")
                        .value("readback_internal_error"));
    }

    private void assertOutcome(
            String outcome,
            int httpStatus,
            boolean authoritative) throws Exception {
        reset(service, verifier);
        VerifiedBoardAttributionReadbackRequest verified = verified();
        BoardAttributionReadbackResponseV1 unsigned = unsigned(
                outcome, authoritative);
        BoardAttributionReadbackResponseV1 signed = signed(
                outcome, httpStatus, authoritative);
        when(verifier.verify(any(), eq(properties))).thenReturn(verified);
        when(service.read(verified)).thenReturn(unsigned);
        when(verifier.signResponse(
                unsigned, verified, httpStatus, properties))
                .thenReturn(signed);

        mvc.perform(post(IndependentBoardAttributionReadbackController.PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(httpStatus))
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value(outcome))
                .andExpect(jsonPath("$.httpStatus").value(httpStatus))
                .andExpect(jsonPath("$.authoritativeRead")
                        .value(authoritative))
                .andExpect(jsonPath("$.requestDigest").value("4".repeat(64)))
                .andExpect(jsonPath("$.requestNonceHash")
                        .value("5".repeat(64)))
                .andExpect(jsonPath("$.signature").value("f".repeat(64)))
                .andExpect(jsonPath("$.productCreditEligible").value(false));
    }

    private VerifiedBoardAttributionReadbackRequest verified() {
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

    private BoardAttributionReadbackResponseV1 unsigned(
            String outcome,
            boolean authoritative) {
        return response(outcome, 0, authoritative, "");
    }

    private BoardAttributionReadbackResponseV1 signed(
            String outcome,
            int httpStatus,
            boolean authoritative) {
        return response(outcome, httpStatus, authoritative, "f".repeat(64));
    }

    private BoardAttributionReadbackResponseV1 response(
            String outcome,
            int httpStatus,
            boolean authoritative,
            String signature) {
        return new BoardAttributionReadbackResponseV1(
                BoardAttributionReadbackResponseV1.SCHEMA_VERSION,
                outcome,
                EVENT_ID,
                RECEIPT_ID,
                EVENT_DIGEST,
                authoritative,
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
}
