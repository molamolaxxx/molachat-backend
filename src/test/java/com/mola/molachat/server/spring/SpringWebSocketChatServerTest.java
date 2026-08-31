package com.mola.molachat.server.spring;

import com.mola.molachat.server.ChatServer;
import org.junit.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Field;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SpringWebSocketChatServerTest {

    @Test
    @SuppressWarnings("unchecked")
    public void closeUsesPhysicalSessionBinding() throws Exception {
        SpringWebSocketChatServer handler = new SpringWebSocketChatServer();
        WebSocketSession session = mock(WebSocketSession.class);
        ChatServer server = mock(ChatServer.class);
        when(session.getId()).thenReturn("socket-1");

        Field field = SpringWebSocketChatServer.class.getDeclaredField("sessionServerMap");
        field.setAccessible(true);
        ((Map<String, ChatServer>) field.get(handler)).put("socket-1", server);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(server).onClose();
    }
}
