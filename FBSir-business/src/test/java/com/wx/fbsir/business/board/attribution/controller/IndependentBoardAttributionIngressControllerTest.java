package com.wx.fbsir.business.board.attribution.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionEventV1;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndependentBoardAttributionIngressControllerTest {
    private final IndependentBoardAttributionIngestService service =
            mock(IndependentBoardAttributionIngestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new IndependentBoardAttributionIngressController(service))
                .setControllerAdvice(
                        new IndependentBoardAttributionIngressExceptionHandler())
                .addFilters(new IndependentBoardAttributionNoStoreFilter())
                .build();
    }

    @Test
    void returns202ForAppendAnd200ForExactReplay() throws Exception {
        when(service.ingest(any())).thenReturn(result(false));
        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsBytes(
                                new BoardAttributionEventV1())))
                .andExpect(status().isAccepted())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.status").value("APPENDED_REPORT_ONLY"));

        when(service.ingest(any())).thenReturn(result(true));
        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    @Test
    void rejectsWrongMethodAndMapsInvalidOrConflictWithoutCaching()
            throws Exception {
        mvc.perform(get("/internal/independent-board/attribution/events"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")));

        when(service.ingest(any()))
                .thenThrow(new IllegalArgumentException("signature_mismatch"));
        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")));

        reset(service);
        when(service.ingest(any()))
                .thenThrow(new IllegalStateException("event_out_of_order"));
        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")));
    }

    @Test
    void rejectsUnverifiedNaturalTrafficWithoutCaching() throws Exception {
        when(service.ingest(any())).thenThrow(new IllegalArgumentException(
                "natural_requires_verified_host_forwarding_ack"));

        mvc.perform(post("/internal/independent-board/attribution/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.reason").value(
                        "natural_requires_verified_host_forwarding_ack"));
    }

    private IndependentBoardAttributionIngestService.IngestResult result(
            boolean replay) {
        return new IndependentBoardAttributionIngestService.IngestResult(
                "1".repeat(64), "2".repeat(64), "3".repeat(64),
                replay ? "IDEMPOTENT_REPLAY" : "APPENDED_REPORT_ONLY",
                replay, "PROBE", "UNKNOWN", false, false);
    }
}
