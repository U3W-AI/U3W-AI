package com.wx.fbsir.business.board.attribution;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionReadbackController;
import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionReadbackProtocolFilter;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionReadbackRequestVerifier;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackService;
import com.wx.fbsir.business.board.attribution.service.IndependentBoardAttributionReadbackAdmission;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class IndependentBoardAttributionReadbackSurfaceConditionTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withBean(
                            IndependentBoardAttributionReadbackService.class,
                            () -> mock(
                                    IndependentBoardAttributionReadbackService.class))
                    .withBean(
                            BoardAttributionReadbackRequestVerifier.class,
                            () -> mock(
                                    BoardAttributionReadbackRequestVerifier.class))
                    .withBean(
                            IndependentBoardAttributionReadbackAdmission.class,
                            () -> mock(
                                    IndependentBoardAttributionReadbackAdmission.class))
                    .withBean(
                            IndependentBoardAttributionProperties.class,
                            IndependentBoardAttributionProperties::new)
                    .withUserConfiguration(
                            IndependentBoardAttributionReadbackController.class,
                            IndependentBoardAttributionReadbackProtocolFilter.class);

    @Test
    void routeAndProtocolFilterAreAbsentByDefault() {
        context.run(application -> {
            assertEquals(0, application.getBeansOfType(
                    IndependentBoardAttributionReadbackController.class)
                    .size());
            assertEquals(0, application.getBeansOfType(
                    IndependentBoardAttributionReadbackProtocolFilter.class)
                    .size());
        });
    }

    @Test
    void routeAndProtocolFilterExistOnlyBehindExactGate() {
        context.withPropertyValues(
                "fbsir.independent-board.attribution.authoritative-readback-enabled=true")
                .run(application -> {
                    assertEquals(1, application.getBeansOfType(
                            IndependentBoardAttributionReadbackController.class)
                            .size());
                    assertEquals(1, application.getBeansOfType(
                            IndependentBoardAttributionReadbackProtocolFilter.class)
                            .size());
                });
    }
}
