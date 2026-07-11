package com.wx.fbsir.business.websocket.server;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientWebSocketIdentityTest {

    @Test
    void clientIdSupportsLegacyAndPerTabForms() {
        assertEquals("web-42", ClientWebSocketInterceptor.buildClientId("web", 42L, null));
        assertEquals("web-42-tab_12345",
            ClientWebSocketInterceptor.buildClientId("web", 42L, "tab_12345"));
        assertNull(ClientWebSocketInterceptor.buildClientId("web", 42L, "bad instance"));
        assertEquals("42", ClientMessageRouter.extractUserId("web-42"));
        assertEquals("42", ClientMessageRouter.extractUserId("web-42-tab_12345"));
    }

    @Test
    void familyDeliveryReachesEveryMatchingTabOnly() throws Exception {
        ClientSessionManager manager = new ClientSessionManager();
        WebSocketSession firstTab = openSession();
        WebSocketSession secondTab = openSession();
        WebSocketSession otherUser = openSession();

        manager.registerClient("web-42-tab_a123", firstTab);
        manager.registerClient("web-42-tab_b456", secondTab);
        manager.registerClient("web-420-tab_c789", otherUser);
        manager.sendToClientFamily("web-42", "hello");

        verify(firstTab).sendMessage(any(TextMessage.class));
        verify(secondTab).sendMessage(any(TextMessage.class));
        verify(otherUser, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void staleCloseCannotRemoveReplacementSession() {
        ClientSessionManager manager = new ClientSessionManager();
        WebSocketSession oldSession = openSession();
        WebSocketSession replacement = openSession();

        manager.registerClient("web-42", oldSession);
        manager.registerClient("web-42", replacement);
        manager.removeClient("web-42", oldSession);

        assertEquals(1, manager.getOnlineCount());
    }

    private WebSocketSession openSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }
}
