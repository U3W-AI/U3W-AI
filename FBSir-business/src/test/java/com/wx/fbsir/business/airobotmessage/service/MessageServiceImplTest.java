package com.wx.fbsir.business.airobotmessage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.airobotmessage.domain.WecomWebhook;
import com.wx.fbsir.business.airobotmessage.domain.WebhookDelivery;
import com.wx.fbsir.business.airobotmessage.dto.WebhookDeliveryReceipt;
import com.wx.fbsir.business.airobotmessage.dto.WebhookSendRequest;
import com.wx.fbsir.business.airobotmessage.mapper.MessageMapper;
import com.wx.fbsir.business.airobotmessage.mapper.WebhookDeliveryMapper;
import com.wx.fbsir.business.airobotmessage.service.impl.MessageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceImplTest {
    @Mock MessageMapper messageMapper;
    @Mock WebhookDeliveryMapper deliveryMapper;
    @Mock WebhookScopeGuard scopeGuard;
    @Mock WebhookSecretCodec secretCodec;
    @Mock WecomWebhookTransport transport;

    private MessageServiceImpl service;
    private WebhookSendRequest request;

    @BeforeEach
    void setUp() {
        service = new MessageServiceImpl(messageMapper, deliveryMapper, scopeGuard, secretCodec,
                transport, new ObjectMapper());
        request = new WebhookSendRequest(10L, 20L, "campaign:test:0001",
                "U3W归因联测", "测试消息", null);
        WecomWebhook webhook = new WecomWebhook();
        webhook.setId(20L);
        webhook.setEnterpriseId(10L);
        webhook.setStatus(true);
        webhook.setWebhookSecretRef("enc:test");
        when(messageMapper.selectWecomWebhookById(20L, 10L)).thenReturn(webhook);
        lenient().when(secretCodec.resolve("enc:test")).thenReturn(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abcdefghijklmnop");
        lenient().when(deliveryMapper.insert(any(WebhookDelivery.class))).thenAnswer(invocation -> {
            WebhookDelivery delivery = invocation.getArgument(0);
            delivery.setId(99L);
            return 1;
        });
        lenient().when(deliveryMapper.updateResult(any(WebhookDelivery.class))).thenReturn(1);
    }

    @Test
    void succeedsOnlyWhenHttpAndProviderBothAccept() throws Exception {
        when(transport.send(anyString(), anyString()))
                .thenReturn(new WecomWebhookTransport.TransportResult(200, "{\"errcode\":0,\"errmsg\":\"ok\"}"));

        WebhookDeliveryReceipt receipt = service.send(request, 7L);

        assertEquals("PROVIDER_ACCEPTED", receipt.status());
        assertEquals(0, receipt.providerErrcode());
        verify(deliveryMapper).updateResult(any(WebhookDelivery.class));
    }

    @Test
    void providerErrcodeIsRejectedNotSuccess() throws Exception {
        when(transport.send(anyString(), anyString()))
                .thenReturn(new WecomWebhookTransport.TransportResult(200, "{\"errcode\":93000,\"errmsg\":\"invalid webhook\"}"));

        WebhookDeliveryReceipt receipt = service.send(request, 7L);

        assertEquals("REJECTED", receipt.status());
        assertEquals(93000, receipt.providerErrcode());
    }

    @Test
    void sameIdempotencyKeyReplaysWithoutSendingAgain() throws Exception {
        when(transport.send(anyString(), anyString()))
                .thenReturn(new WecomWebhookTransport.TransportResult(200, "{\"errcode\":0,\"errmsg\":\"ok\"}"));
        service.send(request, 7L);

        ArgumentCaptor<WebhookDelivery> captor = ArgumentCaptor.forClass(WebhookDelivery.class);
        verify(deliveryMapper).insert(captor.capture());
        WebhookDelivery existing = captor.getValue();
        existing.setStatus("PROVIDER_ACCEPTED");
        when(deliveryMapper.selectByIdempotencyKey(10L, "campaign:test:0001"))
                .thenReturn(existing);

        WebhookDeliveryReceipt replay = service.send(request, 7L);
        assertEquals("PROVIDER_ACCEPTED", replay.status());
        verify(transport, org.mockito.Mockito.times(1)).send(anyString(), anyString());
    }

    @Test
    void sameIdempotencyKeyCannotMoveToAnotherWebhook() throws Exception {
        WebhookDelivery existing = new WebhookDelivery();
        existing.setEnterpriseId(10L);
        existing.setWebhookId(21L);
        existing.setIdempotencyKey("campaign:test:0001");
        existing.setPayloadHash("same-payload-is-not-enough");
        existing.setStatus("PROVIDER_ACCEPTED");
        when(deliveryMapper.selectByIdempotencyKey(10L, "campaign:test:0001")).thenReturn(existing);

        assertThrows(IllegalStateException.class, () -> service.send(request, 7L));
        verify(transport, never()).send(anyString(), anyString());
    }

    @Test
    void providerAcceptanceIsNotReportedWhenLedgerUpdateFails() throws Exception {
        when(transport.send(anyString(), anyString()))
                .thenReturn(new WecomWebhookTransport.TransportResult(200, "{\"errcode\":0,\"errmsg\":\"ok\"}"));
        when(deliveryMapper.updateResult(any(WebhookDelivery.class))).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.send(request, 7L));
        verify(transport, times(1)).send(anyString(), anyString());
    }

    @Test
    void http5xxRemainsUnknownEvenWhenBodyIsNotUseful() throws Exception {
        when(transport.send(anyString(), anyString()))
                .thenReturn(new WecomWebhookTransport.TransportResult(503, "temporarily unavailable"));

        WebhookDeliveryReceipt receipt = service.send(request, 7L);

        assertEquals("UNKNOWN", receipt.status());
    }

    @Test
    void actionUrlMustUseExplicitlyAllowedHttpsHost() throws Exception {
        ReflectionTestUtils.setField(service, "allowedActionHosts", "track.u3w.com");
        when(transport.send(anyString(), anyString()))
                .thenReturn(new WecomWebhookTransport.TransportResult(200, "{\"errcode\":0,\"errmsg\":\"ok\"}"));
        WebhookSendRequest allowed = new WebhookSendRequest(10L, 20L, "campaign:test:allowed",
                "U3W归因联测", "测试消息", "https://track.u3w.com/t/1");

        assertEquals("PROVIDER_ACCEPTED", service.send(allowed, 7L).status());

        WebhookSendRequest rejected = new WebhookSendRequest(10L, 20L, "campaign:test:rejected",
                "U3W归因联测", "测试消息", "https://attacker.example/t/1");
        assertThrows(IllegalArgumentException.class, () -> service.send(rejected, 7L));
    }
}
