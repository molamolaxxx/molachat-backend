package com.mola.molachat.session.solution;

import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MessageSolutionTest {

    @Mock
    private SessionFactoryInterface sessionFactory;

    @InjectMocks
    private MessageSolution solution;

    @Test
    public void movesInterruptingMessageBehindOldTurnOutput() {
        Message interrupt = message("interrupt", "停一下");
        Message oldOutput = message("old-output", "好，我会继续");
        Session session = new Session();
        session.setMessageList(new ArrayList<>(Arrays.asList(interrupt, oldOutput)));
        when(sessionFactory.selectById("group-1")).thenReturn(session);

        assertTrue(solution.moveMessageToEnd("group-1", "interrupt"));

        assertEquals("old-output", session.getMessageList().get(0).getId());
        assertEquals("interrupt", session.getMessageList().get(1).getId());
    }

    private static Message message(String id, String content) {
        Message message = new Message();
        message.setId(id);
        message.setContent(content);
        return message;
    }
}
