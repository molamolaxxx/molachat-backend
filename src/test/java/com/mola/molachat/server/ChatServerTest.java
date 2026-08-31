package com.mola.molachat.server;

import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.server.session.SessionWrapper;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.session.solution.VideoSessionSolution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChatServerTest {

    @Mock
    private ServerService serverService;

    @Mock
    private VideoSessionSolution videoSessionSolution;

    @Mock
    private ChatterService chatterService;

    @Mock
    private MessageSolution messageSolution;

    @Mock
    private SessionWrapper session;

    @InjectMocks
    private ChatServer chatServer;

    @Test
    public void closeIsIdempotentWhenHttpAndSocketCallbacksRace() throws Exception {
        chatServer.setChatterId("shared-chatter");
        chatServer.setDeviceId("device-a");
        chatServer.setSession(session);
        when(serverService.selectByChatterId("shared-chatter"))
                .thenReturn(Collections.singletonList(chatServer));

        chatServer.onClose();
        chatServer.onClose();

        verify(serverService, times(1)).remove(chatServer);
        verify(session, times(1)).close();
    }

    @Test
    public void newPhysicalConnectionReplacesPreviousDeviceConnection() throws Exception {
        ChatServer previous = org.mockito.Mockito.mock(ChatServer.class);
        when(serverService.selectByChatterId("shared-chatter", "device-a")).thenReturn(previous);
        when(chatterService.getQueueById("shared-chatter")).thenReturn(new LinkedBlockingQueue<>());

        chatServer.onOpen(session, "shared-chatter", "device-a");

        verify(previous).onClose();
        verify(serverService).create(chatServer);
        verify(chatterService).setChatterStatus(
                "shared-chatter", ChatterStatusEnum.ONLINE.getCode());
    }
}
