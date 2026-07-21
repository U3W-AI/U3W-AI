package com.wx.fbsir.business.board.service;

import com.wx.fbsir.business.board.oauth.mapper.IndependentBoardOAuthMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Test-source-only wiring for the production first-protected-request transaction chain. */
@Configuration(proxyBeanMethods = false)
public class IndependentBoardOAuthFirstProtectedRequestMysqlTestConfiguration {
    public static final String RUNNER_BEAN_NAME =
            "independentBoardOAuthFirstProtectedRequestTransactionRunnerForMysqlIt";

    @Bean(name = RUNNER_BEAN_NAME)
    IndependentBoardOAuthFirstProtectedRequestTransactionRunner
            independentBoardOAuthFirstProtectedRequestTransactionRunnerForMysqlIt(
                    IndependentBoardOAuthMapper oauthMapper,
                    IndependentBoardConnectorBindingService bindingService) {
        return new IndependentBoardOAuthFirstProtectedRequestTransactionRunner(
                oauthMapper, bindingService);
    }

    @Bean
    IndependentBoardOAuthFirstProtectedRequestFacade
            independentBoardOAuthFirstProtectedRequestFacadeForMysqlIt(
                    IndependentBoardOAuthFirstProtectedRequestTransactionRunner runner) {
        return new IndependentBoardOAuthFirstProtectedRequestFacade(runner);
    }
}
