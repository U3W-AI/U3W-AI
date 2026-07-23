package com.wx.fbsir.business.board.attribution.controller;

import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionAdminReadService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IndependentBoardAttributionAdminControllerTest {
    @Test
    void endpointIsAdminPermissionBoundGetOnlyAndNoStore() throws Exception {
        Method method = IndependentBoardAttributionAdminController.class
                .getMethod("summary", String.class, String.class, String.class);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertEquals(
                "@ss.hasRole('admin') and "
                        + "@ss.hasPermi('board:attribution:query')",
                authorization.value());

        IndependentBoardAttributionAdminReadService service =
                mock(IndependentBoardAttributionAdminReadService.class);
        when(service.summary(anyString(), anyString(), anyString()))
                .thenReturn(new IndependentBoardAttributionAdminReadService.Summary(
                        "2026-07-23T00:00:00Z",
                        "2026-07-23T01:00:00Z",
                        "ALL", 0, List.of()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                        new IndependentBoardAttributionAdminController(service))
                .setControllerAdvice(
                        new IndependentBoardAttributionAdminExceptionHandler())
                .addFilters(new IndependentBoardAttributionNoStoreFilter())
                .build();

        mvc.perform(get("/business/independent-board/attribution/summary")
                        .param("windowStart", "2026-07-23T00:00:00Z")
                        .param("windowEnd", "2026-07-23T01:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")));
        mvc.perform(post("/business/independent-board/attribution/summary"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(
                        "Cache-Control", containsString("no-store")));
    }
}
