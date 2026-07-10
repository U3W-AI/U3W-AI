package com.wx.fbsir.business.smartbot.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Minimal secret resolver. Only explicit env:NAME references are accepted;
 * arbitrary property paths, files, commands, and URLs are deliberately rejected.
 */
@Component
public class EnvironmentSecretReferenceResolver implements SecretReferenceResolver {

    private static final Pattern ENV_NAME = Pattern.compile("[A-Z][A-Z0-9_]{2,127}");
    private final Function<String, String> environmentLookup;

    public EnvironmentSecretReferenceResolver() {
        this(System::getenv);
    }

    EnvironmentSecretReferenceResolver(Function<String, String> environmentLookup) {
        this.environmentLookup = environmentLookup;
    }

    @Override
    public String resolve(String secretReference) {
        if (!StringUtils.hasText(secretReference) || !secretReference.startsWith("env:")) {
            throw new IllegalArgumentException("仅支持 env:NAME 形式的凭据引用");
        }
        String name = secretReference.substring(4);
        if (!ENV_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("凭据环境变量名称不合法");
        }
        String secret = environmentLookup.apply(name);
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("机器人凭据不可用");
        }
        return secret;
    }
}
