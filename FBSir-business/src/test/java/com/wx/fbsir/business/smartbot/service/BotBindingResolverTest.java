package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.smartbot.domain.WecomBotBinding;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.mapper.WecomBotBindingMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotBindingResolverTest {

    private WecomBotBindingMapper mapper;
    private SecretReferenceResolver secretResolver;
    private BotBindingResolver resolver;

    @BeforeEach
    void setUp() {
        mapper = mock(WecomBotBindingMapper.class);
        secretResolver = mock(SecretReferenceResolver.class);
        resolver = new BotBindingResolver(mapper, secretResolver);
    }

    @Test
    void resolvesActiveBindingWithoutExposingSecretsInToStringOrJson() throws Exception {
        WecomBotBinding binding = binding();
        when(mapper.selectActiveByCallbackKey("bot_callback_key_01")).thenReturn(binding);
        when(secretResolver.resolve("env:BOT_TOKEN")).thenReturn("token-secret");
        when(secretResolver.resolve("env:BOT_AES_KEY")).thenReturn("aes-secret");

        ResolvedBotBinding result = resolver.resolve("bot_callback_key_01");

        assertEquals(7L, result.bindingId());
        assertEquals("AIBOT-01", result.aibotId());
        assertEquals("token-secret", result.token());
        assertFalse(result.toString().contains("token-secret"));
        assertFalse(result.toString().contains("aes-secret"));
        assertFalse(result.toString().contains("bot_callback_key_01"));
        String json = new ObjectMapper().writeValueAsString(result);
        assertFalse(json.contains("token-secret"));
        assertFalse(json.contains("aes-secret"));
        assertFalse(json.contains("bot_callback_key_01"));
    }

    @Test
    void invalidCallbackKeyIsRejectedBeforeDatabaseLookup() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve("../bot"));
        verify(mapper, never()).selectActiveByCallbackKey("../bot");
    }

    @Test
    void unknownBindingFailsClosed() {
        when(mapper.selectActiveByCallbackKey("unknown_callback_01")).thenReturn(null);
        assertThrows(SecurityException.class, () -> resolver.resolve("unknown_callback_01"));
    }

    @Test
    void longConnectionPlaceholderIsNotAcceptedAsCallbackCapability() {
        WecomBotBinding binding = binding();
        binding.setMode("LONG_CONNECTION");
        when(mapper.selectActiveByCallbackKey("bot_callback_key_01")).thenReturn(binding);

        assertThrows(SecurityException.class, () -> resolver.resolve("bot_callback_key_01"));
        verify(secretResolver, never()).resolve("env:BOT_TOKEN");
    }

    private WecomBotBinding binding() {
        WecomBotBinding binding = new WecomBotBinding();
        binding.setId(7L);
        binding.setCallbackKey("bot_callback_key_01");
        binding.setAibotId("AIBOT-01");
        binding.setEnterpriseId(11L);
        binding.setMode("CALLBACK");
        binding.setTokenSecretRef("env:BOT_TOKEN");
        binding.setAesKeySecretRef("env:BOT_AES_KEY");
        binding.setCredentialVersion(3);
        binding.setStatus(1);
        return binding;
    }
}
