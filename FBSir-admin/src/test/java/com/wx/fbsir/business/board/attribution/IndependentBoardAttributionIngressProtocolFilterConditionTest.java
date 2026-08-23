package com.wx.fbsir.business.board.attribution;

import com.wx.fbsir.business.board.attribution.controller.IndependentBoardAttributionIngressProtocolFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndependentBoardAttributionIngressProtocolFilterConditionTest {
    private final ApplicationContextRunner context =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            IndependentBoardAttributionIngressProtocolFilter.class);

    @Test
    void protocolSurfaceIsAbsentByDefault() {
        context.run(application -> assertEquals(
                0,
                application.getBeansOfType(
                        IndependentBoardAttributionIngressProtocolFilter.class)
                    .size()));
    }

    @Test
    void protocolSurfaceExistsOnlyWithTheWriterGate() {
        context.withPropertyValues(
                "fbsir.independent-board.attribution.observation-writer-enabled=true")
                .run(application -> assertEquals(
                        1,
                        application.getBeansOfType(
                                IndependentBoardAttributionIngressProtocolFilter.class)
                            .size()));
    }
}
