package com.mola.molachat.session.controller;

import com.mola.molachat.common.handler.TokenCheckHandler;
import com.mola.molachat.session.solution.SessionPreviewSolution;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(MockitoJUnitRunner.class)
public class SessionMessageControllerTest {

    @Mock
    private TokenCheckHandler tokenCheckHandler;

    @Mock
    private SessionPreviewSolution sessionPreviewSolution;

    @InjectMocks
    private SessionMessageController controller;

    private MockMvc mockMvc;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void returnsFullContentForAuthenticatedSessionMember() throws Exception {
        when(tokenCheckHandler.checkToken(eq("user-1"), eq("token-1"), any())).thenReturn(true);
        when(sessionPreviewSolution.findMessageContent("session-1", "message-1", "user-1"))
                .thenReturn("full content");

        mockMvc.perform(get("/session/message/content")
                .param("sessionId", "session-1")
                .param("messageId", "message-1")
                .param("chatterId", "user-1")
                .param("token", "token-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data").value("full content"));
    }

    @Test
    public void rejectsInvalidTokenBeforeReadingMessage() throws Exception {
        when(tokenCheckHandler.checkToken(eq("user-1"), eq("bad-token"), any())).thenReturn(false);

        mockMvc.perform(get("/session/message/content")
                .param("sessionId", "session-1")
                .param("messageId", "message-1")
                .param("chatterId", "user-1")
                .param("token", "bad-token"))
                .andExpect(status().isBadRequest());

        verify(sessionPreviewSolution, never())
                .findMessageContent("session-1", "message-1", "user-1");
    }

    @Test
    public void hidesMissingOrUnauthorizedMessage() throws Exception {
        when(tokenCheckHandler.checkToken(eq("user-1"), eq("token-1"), any())).thenReturn(true);
        when(sessionPreviewSolution.findMessageContent("session-1", "message-1", "user-1"))
                .thenReturn(null);

        mockMvc.perform(get("/session/message/content")
                .param("sessionId", "session-1")
                .param("messageId", "message-1")
                .param("chatterId", "user-1")
                .param("token", "token-1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.msg").value("消息不存在或无权访问"));
    }
}
