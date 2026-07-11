package com.wx.fbsir.business.smartbot.service;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmartBotRunAuditServiceTest {

    @Test
    void auditViewUsesOneReadOnlyRepeatableReadSnapshot() throws Exception {
        Method get = SmartBotRunAuditService.class.getMethod("get", String.class, Long.class);
        TransactionAttribute transaction = new AnnotationTransactionAttributeSource()
            .getTransactionAttribute(get, SmartBotRunAuditService.class);

        assertNotNull(transaction);
        assertTrue(transaction.isReadOnly());
        assertEquals(TransactionDefinition.ISOLATION_REPEATABLE_READ,
            transaction.getIsolationLevel());
    }
}
