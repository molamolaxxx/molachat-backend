package com.mola.molachat.session.solution;

import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.service.SessionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;

@RunWith(MockitoJUnitRunner.class)
public class MessageSolutionTest {

    @Mock
    private SessionFactoryInterface sessionFactory;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private MessageSolution solution;

    @Test
    public void removesDeferredMessageFromCurrentHistory() {
        Message interrupt = message("interrupt", "停一下");
        Message oldOutput = message("old-output", "好，我会继续");
        Session session = new Session();
        session.setMessageList(new ArrayList<>());
        session.getMessageList().add(interrupt);
        session.getMessageList().add(oldOutput);
        when(sessionFactory.selectById("group-1")).thenReturn(session);

        assertTrue(solution.removeMessage("group-1", interrupt));

        assertEquals(1, session.getMessageList().size());
        assertEquals("old-output", session.getMessageList().get(0).getId());
    }

    @Test
    public void terminalWithoutSessionRefreshPersistsOldStreamWithoutCreateSessionProjection() {
        Session session = new Session();
        session.setSessionId("group-1");
        session.setChatterSet(Collections.emptySet());
        when(sessionFactory.selectById("group-1")).thenReturn(session);

        StreamMessage chunk = new StreamMessage();
        chunk.setChatterId("acp-codex");
        chunk.setContent("old reply");
        solution.sendStreamMessage("group-1", chunk);

        StreamMessage terminal = new StreamMessage();
        terminal.setChatterId("acp-codex");
        terminal.setContent("");
        terminal.setEnd(true);
        solution.sendStreamMessageWithoutSessionRefresh("group-1", terminal);

        verify(sessionFactory).insertMessage(eq("group-1"), argThat(message ->
                "old reply".equals(message.getContent())));
        verify(sessionService, never()).findSession(anyString());
    }

    private static Message message(String id, String content) {
        Message message = new Message();
        message.setId(id);
        message.setContent(content);
        return message;
    }
}
