package com.wx.fbsir.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Makes {@code fbsir.*} the primary spelling while preserving legacy configuration. */
public class FBSirConfigurationAliasEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "fbsirConfigurationAliases";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ConfigurationPrefixAliasProcessor.apply(environment, "fbsir", "wxfbsir", PROPERTY_SOURCE_NAME);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
