package com.wx.fbsir.business.board.plan.service;

import com.wx.fbsir.business.board.plan.domain.BoardPlanPolicySnapshot;
import com.wx.fbsir.business.board.plan.dto.BoardPlanPolicyRevisionRequest;
import com.wx.fbsir.business.board.plan.mapper.IndependentBoardPlanPolicyMapper;
import com.wx.fbsir.common.exception.ServiceException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves rollback-before-replay through a real Spring transactional proxy. */
class IndependentBoardPlanPolicyTransactionBoundaryTest {
    private AnnotationConfigApplicationContext context;
    private IndependentBoardPlanPolicyMapper mapper;
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void startContext() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        mapper = context.getBean(IndependentBoardPlanPolicyMapper.class);
        transactionManager = context.getBean(PlatformTransactionManager.class);
    }

    @AfterEach
    void stopContext() {
        context.close();
    }

    @Test
    void casFailureRollsBackFreshWriteBeforeOpeningRecoveryReplay() {
        TransactionStatus preflight = new SimpleTransactionStatus();
        TransactionStatus fresh = new SimpleTransactionStatus();
        TransactionStatus recovery = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any()))
                .thenReturn(preflight, fresh, recovery);
        when(mapper.selectPolicyHeadCodesForUpdate(anyString()))
                .thenReturn(List.of(
                        IndependentBoardPlanPolicyService.FREE_PLAN,
                        IndependentBoardPlanPolicyService.VIP_PLAN));
        when(mapper.selectCurrentPolicies(anyString())).thenReturn(baselineCatalog());
        when(mapper.insertReceipt(any())).thenReturn(1);
        when(mapper.updateHeadIfCurrent(
                anyString(), anyString(), anyString(), anyLong(),
                anyString(), anyLong(), any()))
                .thenReturn(0);

        IndependentBoardPlanPolicyTransactionService transactionService =
                context.getBean(IndependentBoardPlanPolicyTransactionService.class);
        assertTrue(AopUtils.isAopProxy(transactionService));
        ServiceException failure = assertThrows(ServiceException.class,
                () -> context.getBean(IndependentBoardPlanPolicyService.class).revise(
                        new BoardPlanPolicyRevisionRequest(
                                "BOARD_VIP", 1L, "Independent Board VIP Plus",
                                8, 30, null, true, null,
                                "plan:20260722:proxy-cas"),
                        900L));

        assertEquals("BOARD_PLAN_POLICY_VERSION_CONFLICT", failure.getMessage());
        InOrder order = inOrder(transactionManager);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(preflight);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).rollback(fresh);
        order.verify(transactionManager).getTransaction(any());
        order.verify(transactionManager).commit(recovery);

        ArgumentCaptor<TransactionDefinition> definitions =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(3)).getTransaction(definitions.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definitions.getAllValues().get(0).getPropagationBehavior());
        assertTrue(definitions.getAllValues().get(0).isReadOnly());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRED,
                definitions.getAllValues().get(1).getPropagationBehavior());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definitions.getAllValues().get(2).getPropagationBehavior());
        assertTrue(definitions.getAllValues().get(2).isReadOnly());
    }

    private static List<BoardPlanPolicySnapshot> baselineCatalog() {
        return List.of(
                baseline("BOARD_FREE", "Independent Board Free", false, 1, 5, 3, false),
                baseline("BOARD_VIP", "Independent Board VIP", true, 5, 30, null, true));
    }

    private static BoardPlanPolicySnapshot baseline(
            String planCode, String name, boolean vip, int daily,
            int agenda, Integer seats, boolean secretary) {
        BoardPlanPolicySnapshot value = new BoardPlanPolicySnapshot();
        value.setReceiptId("plan-policy-baseline-"
                + planCode.toLowerCase().replace('_', '-') + "-v1");
        value.setProductCode(IndependentBoardPlanPolicyService.PRODUCT_CODE);
        value.setPlanCode(planCode);
        value.setPolicyVersion(1L);
        value.setAction("PLAN_POLICY_BASELINED");
        value.setActorType("SYSTEM_MIGRATION");
        value.setIdempotencyKeyDigest("a".repeat(64));
        value.setCommandDigest("b".repeat(64));
        value.setPlanName(name);
        value.setVip(vip);
        value.setConnectorRequired(vip);
        value.setDailyMeetingLimit(daily);
        value.setAgendaLimit(agenda);
        value.setSeatLimit(seats);
        value.setSecretaryEnabled(secretary);
        value.setStatus("ACTIVE");
        value.setEvidenceLevel("ACTION_COMPLETED");
        value.setCreatedAt(Date.from(Instant.parse("2026-07-20T00:00:00Z")));
        value.setPolicyDigest(BoardPlanPolicyDigest.policyDigest(value));
        value.setIdentityProductCode(value.getProductCode());
        value.setIdentityPlanCode(value.getPlanCode());
        value.setIdentityVip(value.getVip());
        value.setIdentityConnectorRequired(value.getConnectorRequired());
        value.setIdentityStatus(value.getStatus());
        return value;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfiguration {
        @Bean
        IndependentBoardPlanPolicyMapper mapper() {
            return mock(IndependentBoardPlanPolicyMapper.class);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        IndependentBoardPlanPolicyTransactionService transactionService(
                IndependentBoardPlanPolicyMapper mapper) {
            return new IndependentBoardPlanPolicyTransactionService(mapper);
        }

        @Bean
        IndependentBoardPlanPolicyService planPolicyService(
                IndependentBoardPlanPolicyTransactionService transactionService) {
            return new IndependentBoardPlanPolicyService(transactionService);
        }
    }
}
