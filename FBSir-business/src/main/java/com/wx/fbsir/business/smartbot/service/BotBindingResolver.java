package com.wx.fbsir.business.smartbot.service;

import com.wx.fbsir.business.smartbot.domain.WecomBotBinding;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.mapper.WecomBotBindingMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/** Selects credentials before payload decryption, then keeps them transient. */
@Service
public class BotBindingResolver {

    private static final Pattern CALLBACK_KEY = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    private final WecomBotBindingMapper bindingMapper;
    private final SecretReferenceResolver secretResolver;

    public BotBindingResolver(WecomBotBindingMapper bindingMapper,
                              SecretReferenceResolver secretResolver) {
        this.bindingMapper = bindingMapper;
        this.secretResolver = secretResolver;
    }

    public ResolvedBotBinding resolve(String callbackKey) {
        if (callbackKey == null || !CALLBACK_KEY.matcher(callbackKey).matches()) {
            throw new IllegalArgumentException("机器人回调路由键不合法");
        }
        WecomBotBinding binding = bindingMapper.selectActiveByCallbackKey(callbackKey);
        if (binding == null || binding.getId() == null || binding.getEnterpriseId() == null
                || binding.getStatus() == null || binding.getStatus() != 1
                || !StringUtils.hasText(binding.getAibotId()) || binding.getAibotId().length() > 128
                || !"CALLBACK".equals(binding.getMode())
                || binding.getCredentialVersion() == null || binding.getCredentialVersion() <= 0) {
            throw new SecurityException("机器人绑定不可用");
        }

        String token = secretResolver.resolve(binding.getTokenSecretRef());
        String aesKey = secretResolver.resolve(binding.getAesKeySecretRef());
        return new ResolvedBotBinding(binding.getId(), binding.getCallbackKey(),
            binding.getAibotId(), binding.getEnterpriseId(), binding.getMode(),
            binding.getCredentialVersion(), token, aesKey);
    }
}
