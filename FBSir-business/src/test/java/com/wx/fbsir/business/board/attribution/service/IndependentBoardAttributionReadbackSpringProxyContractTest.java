package com.wx.fbsir.business.board.attribution.service;

import com.wx.fbsir.business.board.attribution.config.IndependentBoardAttributionProperties;
import com.wx.fbsir.business.board.attribution.mapper.IndependentBoardAttributionV1Mapper;
import com.wx.fbsir.business.board.attribution.receipt.VerifiedBoardAttributionReadbackRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Proves the production entry method is intercepted by Spring transaction AOP. */
class IndependentBoardAttributionReadbackSpringProxyContractTest {
    private AnnotationConfigApplicationContext context;

    @BeforeEach
    void openContext() {
        context = new AnnotationConfigApplicationContext(
                ConfigurationUnderTest.class);
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void readUsesRealRequiresNewRepeatableReadOnlyProxyBoundary() {
        PlatformTransactionManager manager =
                context.getBean(PlatformTransactionManager.class);
        IndependentBoardAttributionV1Mapper mapper =
                context.getBean(IndependentBoardAttributionV1Mapper.class);
        IndependentBoardAttributionReadbackService service =
                context.getBean(IndependentBoardAttributionReadbackService.class);
        TransactionStatus transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(mapper.selectTransactionReadOnlyState()).thenReturn(1);

        var response = service.read(new VerifiedBoardAttributionReadbackRequest(
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                Instant.parse("2026-08-23T08:39:30Z"),
                Instant.parse("2026-08-23T08:40:30Z"),
                "4".repeat(64),
                "5".repeat(64),
                "readback-k1"));

        assertTrue(AopUtils.isAopProxy(service));
        assertEquals("NOT_FOUND_AUTHORITATIVE", response.status());
        ArgumentCaptor<TransactionDefinition> definition =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager).getTransaction(definition.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                definition.getValue().getPropagationBehavior());
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
                definition.getValue().getIsolationLevel());
        assertEquals(5, definition.getValue().getTimeout());
        assertTrue(definition.getValue().isReadOnly());
        verify(manager).commit(transaction);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class ConfigurationUnderTest {
        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }

        @Bean
        IndependentBoardAttributionV1Mapper mapper() {
            return mock(IndependentBoardAttributionV1Mapper.class);
        }

        @Bean
        IndependentBoardAttributionProperties properties() {
            IndependentBoardAttributionProperties value =
                    new IndependentBoardAttributionProperties();
            value.setAuthoritativeReadbackEnabled(true);
            value.setAuthoritativeReadbackReceiverReleaseId(
                    "w05e-readback-proxy-test");
            value.setAuthoritativeReadbackReceiverJarSha256("a".repeat(64));
            return value;
        }

        @Bean
        IndependentBoardAttributionReadbackService readbackService(
                IndependentBoardAttributionV1Mapper mapper,
                IndependentBoardAttributionProperties properties) {
            return new IndependentBoardAttributionReadbackService(
                    mapper, properties);
        }
    }
}
