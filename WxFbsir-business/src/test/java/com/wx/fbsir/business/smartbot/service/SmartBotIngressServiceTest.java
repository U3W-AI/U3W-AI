package com.wx.fbsir.business.smartbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.smartbot.domain.DeliveryOutbox;
import com.wx.fbsir.business.smartbot.domain.OrchestrationRun;
import com.wx.fbsir.business.smartbot.domain.OrchestrationStep;
import com.wx.fbsir.business.smartbot.domain.WecomBotMemberBinding;
import com.wx.fbsir.business.smartbot.domain.WecomBotBinding;
import com.wx.fbsir.business.smartbot.domain.WecomInboundEvent;
import com.wx.fbsir.business.smartbot.dto.ResolvedBotBinding;
import com.wx.fbsir.business.smartbot.dto.SmartBotInboundEnvelope;
import com.wx.fbsir.business.smartbot.dto.SmartBotIngressResult;
import com.wx.fbsir.business.smartbot.mapper.DeliveryOutboxMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationRunMapper;
import com.wx.fbsir.business.smartbot.mapper.OrchestrationStepMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotMemberBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomBotBindingMapper;
import com.wx.fbsir.business.smartbot.mapper.WecomInboundEventMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartBotIngressServiceTest {

    private static final String PAYLOAD_HASH = "a".repeat(64);
    private static final String USER_HASH = "b".repeat(64);
    private static final String MSG_ID_HASH = "e".repeat(64);

    private WecomBotBindingMapper botBindingMapper;
    private WecomBotMemberBindingMapper memberBindingMapper;
    private FbsEnterpriseMapper enterpriseMapper;
    private FbsEnterpriseMemberMapper enterpriseMemberMapper;
    private WecomInboundEventMapper inboundEventMapper;
    private OrchestrationRunMapper runMapper;
    private OrchestrationStepMapper stepMapper;
    private DeliveryOutboxMapper outboxMapper;
    private ExternalIdentityHasher identityHasher;
    private SmartBotIngressService service;

    @BeforeEach
    void setUp() {
        botBindingMapper = mock(WecomBotBindingMapper.class);
        memberBindingMapper = mock(WecomBotMemberBindingMapper.class);
        enterpriseMapper = mock(FbsEnterpriseMapper.class);
        enterpriseMemberMapper = mock(FbsEnterpriseMemberMapper.class);
        inboundEventMapper = mock(WecomInboundEventMapper.class);
        runMapper = mock(OrchestrationRunMapper.class);
        stepMapper = mock(OrchestrationStepMapper.class);
        outboxMapper = mock(DeliveryOutboxMapper.class);
        identityHasher = mock(ExternalIdentityHasher.class);
        service = new SmartBotIngressService(botBindingMapper, memberBindingMapper,
            enterpriseMapper, enterpriseMemberMapper,
            inboundEventMapper, runMapper, stepMapper, outboxMapper, identityHasher,
            new ObjectMapper());
    }

    @Test
    void firstDeliveryCreatesExactlyOneDurableRunStepAndOutbox() {
        arrangeActiveMember();
        when(inboundEventMapper.claimInboundEvent(any())).thenAnswer(invocation -> {
            WecomInboundEvent event = invocation.getArgument(0);
            event.setId(101L);
            return 1;
        });
        when(runMapper.insertRun(any())).thenReturn(1);
        when(stepMapper.insertStep(any())).thenReturn(1);
        when(outboxMapper.insertOutbox(any())).thenReturn(1);

        SmartBotIngressResult result = service.accept(binding(), envelope());

        assertTrue(result.firstDelivery());
        assertEquals(101L, result.inboundEventId());
        assertEquals(11L, result.enterpriseId());
        assertEquals(21L, result.enterpriseMemberId());
        assertEquals(31L, result.userId());
        assertNotNull(result.traceId());
        assertNotNull(result.runId());
        assertTrue(result.streamId().startsWith("u3w-"));

        ArgumentCaptor<OrchestrationRun> runCaptor = ArgumentCaptor.forClass(OrchestrationRun.class);
        verify(runMapper).insertRun(runCaptor.capture());
        OrchestrationRun run = runCaptor.getValue();
        assertEquals(result.runId(), run.getRunId());
        assertEquals(result.streamId(), run.getStreamId());
        assertEquals("PENDING", run.getStatus());
        assertEquals("u3w.smartbot.default", run.getDefinitionCode());

        ArgumentCaptor<OrchestrationStep> stepCaptor = ArgumentCaptor.forClass(OrchestrationStep.class);
        verify(stepMapper).insertStep(stepCaptor.capture());
        assertEquals("ingress.accepted", stepCaptor.getValue().getStepKey());
        assertEquals("SUCCEEDED", stepCaptor.getValue().getStatus());

        ArgumentCaptor<DeliveryOutbox> outboxCaptor = ArgumentCaptor.forClass(DeliveryOutbox.class);
        verify(outboxMapper).insertOutbox(outboxCaptor.capture());
        DeliveryOutbox outbox = outboxCaptor.getValue();
        assertEquals("RUN_CREATED", outbox.getEventType());
        assertEquals("INTERNAL_DISPATCHER", outbox.getDestinationType());
        assertTrue(outbox.getPayloadJson().contains(result.runId()));
        assertFalse(outbox.getPayloadJson().contains("external-user-01"));
        assertFalse(outbox.getPayloadJson().contains("message body"));
        assertFalse(outbox.getPayloadJson().contains("token-secret"));
    }

    @Test
    void duplicateDeliveryReturnsStoredIdentifiersWithoutCreatingMoreWork() {
        arrangeActiveMember();
        when(inboundEventMapper.claimInboundEvent(any())).thenAnswer(invocation -> {
            WecomInboundEvent event = invocation.getArgument(0);
            event.setId(101L);
            return 2;
        });
        WecomInboundEvent stored = storedEvent();
        when(inboundEventMapper.selectById(101L)).thenReturn(stored);
        OrchestrationRun storedRun = new OrchestrationRun();
        storedRun.setRunId("stored-run");
        storedRun.setEnterpriseId(11L);
        storedRun.setEnterpriseMemberId(21L);
        storedRun.setUserId(31L);
        when(runMapper.selectByRunIdForUpdate("stored-run")).thenReturn(storedRun);

        SmartBotIngressResult result = service.accept(binding(), envelope());

        assertFalse(result.firstDelivery());
        assertEquals("stored-trace", result.traceId());
        assertEquals("stored-run", result.runId());
        assertEquals("stored-stream", result.streamId());
        verify(runMapper, never()).insertRun(any());
        verify(stepMapper, never()).insertStep(any());
        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    void botMismatchFailsBeforeIdentityOrDatabaseAccess() {
        SmartBotInboundEnvelope envelope = SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("OTHER-BOT")
            .opaqueSenderId("external-user-01")
            .msgType("text")
            .payloadHash(PAYLOAD_HASH)
            .build();

        assertThrows(SecurityException.class, () -> service.accept(binding(), envelope));

        verify(identityHasher, never()).hashUser(any(), any());
        verify(inboundEventMapper, never()).claimInboundEvent(any());
    }

    @Test
    void unknownExternalIdentityFailsClosed() {
        arrangeControlState();
        when(identityHasher.hashUser(7L, "external-user-01")).thenReturn(USER_HASH);
        when(identityHasher.hashMessage(7L, "msg-01")).thenReturn(MSG_ID_HASH);

        assertThrows(SecurityException.class, () -> service.accept(binding(), envelope()));

        verify(inboundEventMapper, never()).claimInboundEvent(any());
    }

    @Test
    void staleOrCrossEnterpriseMemberFailsClosed() {
        arrangeControlState();
        when(identityHasher.hashUser(7L, "external-user-01")).thenReturn(USER_HASH);
        when(identityHasher.hashMessage(7L, "msg-01")).thenReturn(MSG_ID_HASH);
        WecomBotMemberBinding memberBinding = memberBinding();
        when(memberBindingMapper.selectActiveByExternalHash(7L, USER_HASH)).thenReturn(memberBinding);
        FbsEnterpriseMember stale = activeMember();
        stale.setEnterpriseId(999L);
        when(enterpriseMemberMapper.selectById(21L)).thenReturn(stale);

        assertThrows(SecurityException.class, () -> service.accept(binding(), envelope()));

        verify(inboundEventMapper, never()).claimInboundEvent(any());
    }

    @Test
    void duplicateWithChangedPayloadOrIdentityIsRejected() {
        arrangeActiveMember();
        when(inboundEventMapper.claimInboundEvent(any())).thenAnswer(invocation -> {
            WecomInboundEvent event = invocation.getArgument(0);
            event.setId(101L);
            return 2;
        });
        WecomInboundEvent stored = storedEvent();
        stored.setPayloadHash("c".repeat(64));
        when(inboundEventMapper.selectById(101L)).thenReturn(stored);

        assertThrows(SecurityException.class, () -> service.accept(binding(), envelope()));

        verify(runMapper, never()).selectByRunIdForUpdate(any());
        verify(runMapper, never()).insertRun(any());
    }

    @Test
    void failedClaimOrPartialInsertRaisesErrorForTransactionRollback() {
        arrangeActiveMember();
        when(inboundEventMapper.claimInboundEvent(any())).thenAnswer(invocation -> {
            WecomInboundEvent event = invocation.getArgument(0);
            event.setId(101L);
            return 1;
        });
        when(runMapper.insertRun(any())).thenReturn(1);
        when(stepMapper.insertStep(any())).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> service.accept(binding(), envelope()));

        verify(outboxMapper, never()).insertOutbox(any());
    }

    @Test
    void missingGeneratedIdIsRejectedEvenWhenDatabaseReportsSuccess() {
        arrangeActiveMember();
        when(inboundEventMapper.claimInboundEvent(any())).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> service.accept(binding(), envelope()));

        verify(runMapper, never()).insertRun(any());
    }

    @Test
    void disabledEnterpriseFailsBeforeEventClaim() {
        when(botBindingMapper.selectActiveById(7L)).thenReturn(activeBotBinding());
        FbsEnterprise disabled = activeEnterprise();
        disabled.setStatus(2);
        when(enterpriseMapper.selectById(11L)).thenReturn(disabled);

        assertThrows(SecurityException.class, () -> service.accept(binding(), envelope()));

        verify(inboundEventMapper, never()).claimInboundEvent(any());
    }

    private void arrangeActiveMember() {
        arrangeControlState();
        when(identityHasher.hashUser(7L, "external-user-01")).thenReturn(USER_HASH);
        when(identityHasher.hashMessage(7L, "msg-01")).thenReturn(MSG_ID_HASH);
        when(memberBindingMapper.selectActiveByExternalHash(7L, USER_HASH)).thenReturn(memberBinding());
        when(enterpriseMemberMapper.selectById(21L)).thenReturn(activeMember());
    }

    private void arrangeControlState() {
        when(botBindingMapper.selectActiveById(7L)).thenReturn(activeBotBinding());
        when(enterpriseMapper.selectById(11L)).thenReturn(activeEnterprise());
    }

    private ResolvedBotBinding binding() {
        return new ResolvedBotBinding(7L, "bot_callback_key_01", "AIBOT-01", 11L,
            "CALLBACK", 1, "token-secret", "aes-secret");
    }

    private SmartBotInboundEnvelope envelope() {
        return SmartBotInboundEnvelope.builder()
            .msgId("msg-01")
            .aibotId("AIBOT-01")
            .opaqueSenderId("external-user-01")
            .chatType("single")
            .msgType("text")
            .payloadHash(PAYLOAD_HASH)
            .build();
    }

    private WecomBotMemberBinding memberBinding() {
        WecomBotMemberBinding binding = new WecomBotMemberBinding();
        binding.setId(41L);
        binding.setBotBindingId(7L);
        binding.setEnterpriseId(11L);
        binding.setEnterpriseMemberId(21L);
        binding.setUserId(31L);
        binding.setExternalUserHash(USER_HASH);
        binding.setStatus(1);
        return binding;
    }

    private WecomBotBinding activeBotBinding() {
        WecomBotBinding binding = new WecomBotBinding();
        binding.setId(7L);
        binding.setCallbackKey("bot_callback_key_01");
        binding.setAibotId("AIBOT-01");
        binding.setEnterpriseId(11L);
        binding.setMode("CALLBACK");
        binding.setCredentialVersion(1);
        binding.setStatus(1);
        binding.setDelFlag("0");
        return binding;
    }

    private FbsEnterprise activeEnterprise() {
        FbsEnterprise enterprise = new FbsEnterprise();
        enterprise.setId(11L);
        enterprise.setStatus(1);
        enterprise.setDelFlag("0");
        return enterprise;
    }

    private FbsEnterpriseMember activeMember() {
        FbsEnterpriseMember member = new FbsEnterpriseMember();
        member.setId(21L);
        member.setEnterpriseId(11L);
        member.setUserId(31L);
        member.setStatus(1);
        member.setDelFlag("0");
        return member;
    }

    private WecomInboundEvent storedEvent() {
        WecomInboundEvent stored = new WecomInboundEvent();
        stored.setId(101L);
        stored.setBotBindingId(7L);
        stored.setMsgIdHash(MSG_ID_HASH);
        stored.setAibotId("AIBOT-01");
        stored.setTraceId("stored-trace");
        stored.setRunId("stored-run");
        stored.setStreamId("stored-stream");
        stored.setFromUserHash(USER_HASH);
        stored.setPayloadHash(PAYLOAD_HASH);
        return stored;
    }
}
