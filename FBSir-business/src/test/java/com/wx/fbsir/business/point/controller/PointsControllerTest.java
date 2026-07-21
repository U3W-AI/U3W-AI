package com.wx.fbsir.business.point.controller;

import com.wx.fbsir.business.point.service.IPointsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PointsControllerTest {
    @Test
    void legacyDirectMutationReturnsGoneWithoutCallingPointsService() throws Exception {
        PointsController controller = new PointsController();
        IPointsService pointsService = mock(IPointsService.class);
        ReflectionTestUtils.setField(controller, "pointsService", pointsService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/points/changePoints")
                        .param("ruleCode", "ADMIN_GRANT")
                        .param("changeAmount", "1000000"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.msg").value("POINTS_DIRECT_MUTATION_DISABLED"));

        verifyNoInteractions(pointsService);
    }

    @Test
    void malformedLegacyParametersStillReturnTheStableTombstone() throws Exception {
        PointsController controller = new PointsController();
        IPointsService pointsService = mock(IPointsService.class);
        ReflectionTestUtils.setField(controller, "pointsService", pointsService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(post("/points/changePoints")
                        .param("ruleCode", "ADMIN_GRANT")
                        .param("changeAmount", "not-an-integer"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.msg").value("POINTS_DIRECT_MUTATION_DISABLED"));

        verifyNoInteractions(pointsService);
    }
}
