package com.wx.fbsir.business.board.attribution;

import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionIngressController;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionIngressExceptionHandler;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionNoStoreFilter;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionIngressProtocolFilter;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionIngestService;
import com.wx.fbsir.framework.web.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndependentBoardAttributionIngressHttpAdviceIntegrationTest {
    private final IndependentBoardAttributionIngestService service =
            mock(IndependentBoardAttributionIngestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new IndependentBoardAttributionIngressController(service))
                .setControllerAdvice(
                        new GlobalExceptionHandler(),
                        new IndependentBoardAttributionIngressExceptionHandler())
                .addFilters(
                        new IndependentBoardAttributionNoStoreFilter(),
                        new IndependentBoardAttributionIngressProtocolFilter())
                .build();
    }

    @Test
    void receiverAdviceOutranksGlobalHttp200WrapperForIdentityConflicts()
            throws Exception {
        when(service.ingest(any()))
                .thenThrow(new IllegalArgumentException(
                        "official_identity_mismatch"));

        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("CONFLICT"))
                .andExpect(jsonPath("$.reason")
                        .value("official_identity_mismatch"));
    }

    @Test
    void receiverAdviceKeepsOtherValidationFailuresAtHttp400()
            throws Exception {
        when(service.ingest(any()))
                .thenThrow(new IllegalArgumentException("signature_mismatch"));

        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.reason").value("signature_mismatch"));
    }

    @Test
    void receiverAdviceConvertsDaoAndUnexpectedFailuresToRealNon2xx()
            throws Exception {
        when(service.ingest(any()))
                .thenThrow(new DataAccessResourceFailureException(
                        "database-secret-must-not-leak"));

        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.reason")
                        .value("receiver_persistence_unavailable"));

        reset(service);
        when(service.ingest(any()))
                .thenThrow(new RuntimeException(
                        "runtime-secret-must-not-leak"));
        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.reason")
                        .value("receiver_internal_error"));
    }

    @Test
    void receiverAdviceReturnsStable400ForMalformedJson() throws Exception {
        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason")
                        .value("request_body_malformed"));
    }

    @Test
    void receiverAdvicePreservesMethodAndMediaProtocolStatuses()
            throws Exception {
        mvc.perform(get("/internal/independent-board/attribution/events"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.reason").value("method_not_allowed"));

        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.reason")
                        .value("media_type_not_supported"));

        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.reason")
                        .value("media_type_not_acceptable"));
    }
}
