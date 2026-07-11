package com.wx.fbsir.business.smartbot.task;

import com.wx.fbsir.business.smartbot.service.InternalOutboxDispatcherService;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartBotInternalDispatcherTaskTest {

    @Test
    void drainsOnlyTheConfiguredBoundedBatch() {
        InternalOutboxDispatcherService dispatcher = mock(InternalOutboxDispatcherService.class);
        when(dispatcher.dispatchOne(matches("smartbot-dispatcher-[a-f0-9]{8}"),
            any(Duration.class))).thenReturn(
                Optional.of(new InternalOutboxDispatcherService.DispatchOutcome("run-1", "READY")),
                Optional.of(new InternalOutboxDispatcherService.DispatchOutcome("run-2", "READY")));
        SmartBotInternalDispatcherTask task = new SmartBotInternalDispatcherTask(dispatcher, 2, 30);

        assertEquals(2, task.dispatchAvailable());
        verify(dispatcher, times(2)).dispatchOne(any(), any());
    }

    @Test
    void stopsOnAnUnlockedQueueSnapshotAndRejectsUnsafeConfiguration() {
        InternalOutboxDispatcherService dispatcher = mock(InternalOutboxDispatcherService.class);
        when(dispatcher.dispatchOne(any(), any())).thenReturn(Optional.empty());
        SmartBotInternalDispatcherTask task = new SmartBotInternalDispatcherTask(dispatcher, 20, 30);

        assertEquals(0, task.dispatchAvailable());
        verify(dispatcher, times(1)).dispatchOne(any(), any());
        assertThrows(IllegalArgumentException.class,
            () -> new SmartBotInternalDispatcherTask(dispatcher, 0, 30));
        assertThrows(IllegalArgumentException.class,
            () -> new SmartBotInternalDispatcherTask(dispatcher, 20, 4));
    }
}
