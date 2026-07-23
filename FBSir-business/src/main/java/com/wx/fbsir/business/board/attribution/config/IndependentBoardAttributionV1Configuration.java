package com.wx.fbsir.business.board.attribution.config;

import com.wx.fbsir.business.board.attribution.binding.BoardSameBindingKeyDeriver;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionEventV1Verifier;
import com.wx.fbsir.business.board.attribution.receipt.BoardAttributionEventVerifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Production cryptographic bindings for the default-off Wave 1 writer. */
@Configuration
@ConditionalOnProperty(
        prefix = "fbsir.independent-board.attribution",
        name = "observation-writer-enabled",
        havingValue = "true",
        matchIfMissing = false)
public class IndependentBoardAttributionV1Configuration {
    @Bean
    public BoardAttributionEventVerifier boardAttributionEventVerifier(
            IndependentBoardAttributionProperties properties) {
        BoardAttributionEventV1Verifier verifier =
                new BoardAttributionEventV1Verifier(
                        properties.getEventKeys(), Clock.systemUTC());
        if (!verifier.isConfigured()) {
            throw new IllegalStateException(
                    "attribution_event_keyring_unconfigured");
        }
        return verifier;
    }

    @Bean
    public BoardSameBindingKeyDeriver boardSameBindingKeyDeriver(
            IndependentBoardAttributionProperties properties) {
        return new BoardSameBindingKeyDeriver(
                properties.getSameBindingSecret());
    }
}
