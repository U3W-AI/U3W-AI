package com.wx.fbsir.business.airobotmessage.task;

import com.wx.fbsir.business.airobotmessage.mapper.WebhookDeliveryMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WebhookDeliveryReconciliationTaskTest {
    @Test
    void marksAbandonedPendingUnknownWithoutResending() {
        WebhookDeliveryMapper mapper = mock(WebhookDeliveryMapper.class);

        new WebhookDeliveryReconciliationTask(mapper).markAbandonedPendingUnknown();

        verify(mapper).markAllStalePendingUnknown();
    }
}
