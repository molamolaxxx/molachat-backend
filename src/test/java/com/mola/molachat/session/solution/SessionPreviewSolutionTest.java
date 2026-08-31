package com.mola.molachat.session.solution;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.dto.MessagePreviewDTO;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.dto.SessionPreviewDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SessionPreviewSolutionTest {

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private SessionPreviewSolution solution;

    @Test
    public void truncatesOnlyResponseProjectionAndKeepsOriginalContent() {
        String fullContent = repeat('x', 260);
        Message message = message("message-1", fullContent);
        SessionDTO session = session("session-1", message);

        SessionPreviewDTO preview = solution.toPreview(session);
        MessagePreviewDTO projected = preview.getMessageList().get(0);

        assertEquals(200, projected.getContent().length());
        assertTrue(projected.isContentTruncated());
        assertEquals(fullContent, message.getContent());
        String responseJson = JSONObject.toJSONString(preview);
        assertFalse(responseJson.contains(fullContent));
        assertTrue(responseJson.contains("\"contentTruncated\":true"));
    }

    @Test
    public void keepsShortAndFileMessagesCompatible() {
        Message shortMessage = message("message-1", "short");
        FileMessage fileMessage = new FileMessage();
        fileMessage.setId("file-1");
        fileMessage.setFileName("report.txt");
        fileMessage.setUrl("files/report.txt");
        SessionDTO session = session("session-1", shortMessage, fileMessage);

        SessionPreviewDTO preview = solution.toPreview(session);

        assertFalse(preview.getMessageList().get(0).isContentTruncated());
        assertEquals("short", preview.getMessageList().get(0).getContent());
        assertEquals("report.txt", preview.getMessageList().get(1).getFileName());
        assertEquals("files/report.txt", preview.getMessageList().get(1).getUrl());
    }

    @Test
    public void keepsLegacyMessageCompleteWhenItCannotBeFetchedById() {
        Message legacy = message(null, repeat('z', 260));
        legacy.setSessionId(null);

        MessagePreviewDTO projected = solution.toPreview(session("session-1", legacy))
                .getMessageList().get(0);

        assertFalse(projected.isContentTruncated());
        assertEquals(260, projected.getContent().length());
        assertEquals("session-1", projected.getSessionId());
    }

    @Test
    public void returnsFullContentOnlyToSessionMember() {
        String fullContent = repeat('y', 260);
        SessionDTO session = session("session-1", message("message-1", fullContent));
        when(sessionService.findSession("session-1")).thenReturn(session);

        assertEquals(fullContent,
                solution.findMessageContent("session-1", "message-1", "user-1"));
        assertNull(solution.findMessageContent("session-1", "message-1", "other-user"));
        assertNull(solution.findMessageContent("session-1", "missing", "user-1"));
    }

    private static SessionDTO session(String sessionId, Message... messages) {
        Chatter member = new Chatter();
        member.setId("user-1");
        SessionDTO session = new SessionDTO();
        session.setSessionId(sessionId);
        session.setChatterSet(Collections.singleton(member));
        session.setMessageList(new ArrayList<>());
        Collections.addAll(session.getMessageList(), messages);
        return session;
    }

    private static Message message(String id, String content) {
        Message message = new Message();
        message.setId(id);
        message.setContent(content);
        message.setSessionId("session-1");
        return message;
    }

    private static String repeat(char value, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
