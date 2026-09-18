package com.mola.molachat.robot.handler.impl.acp;

import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.solution.AcpPromptSolution;
import com.mola.molachat.robot.solution.AcpDeferredMessageSolution;
import com.mola.molachat.robot.solution.AcpRuntimeStatusSolution;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AcpExecHandlerTest {

    @Mock
    private ChatterService chatterService;

    @Mock
    private AcpRuntimeStatusSolution acpRuntimeStatusSolution;

    @Mock
    private AcpPromptSolution acpPromptSolution;

    @Mock
    private AcpDeferredMessageSolution acpDeferredMessageSolution;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AcpExecHandler handler;

    @Test
    public void sessionTransitionImmediatelyShowsDisconnectedStatus() {
        ChatterDTO acp = new ChatterDTO();
        acp.setId("acp-codex");
        acp.setStatus(ChatterStatusEnum.ONLINE.getCode());
        when(chatterService.selectById("acp-codex")).thenReturn(acp);

        handler.markAcpTransitioning("acp-codex");

        verify(chatterService).setChatterStatus(
                "acp-codex", ChatterStatusEnum.DISCONNECT.getCode());
    }

    @Test
    public void busyTextUsesInterruptingSubmission() {
        MessageReceiveEvent event = event("continue");
        when(acpRuntimeStatusSolution.getStatus("session-1")).thenReturn("BUSY");
        when(acpDeferredMessageSolution.defer("session-1", event.getMessage()))
                .thenReturn(true);
        when(acpPromptSolution.sendInterrupting(eq("session-1"), eq("continue"),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(AcpPromptSolution.InvokeResult.accepted(
                        "INTERRUPTED_PENDING", "pending"));

        BaseAction action = handler.handler(event);

        assertTrue(action.getSkip());
        verify(acpPromptSolution).sendInterrupting(eq("session-1"), eq("continue"),
                org.mockito.ArgumentMatchers.anyList());
        verify(acpDeferredMessageSolution).defer("session-1", event.getMessage());
    }

    @Test
    public void attachmentCollectionSkipsRobotCancellationMessage() {
        FileMessage file = new FileMessage();
        file.setChatterId("user-1");
        file.setFileName("note.txt");
        file.setUrl("files/stored_note.txt");
        Message cancelled = new Message();
        cancelled.setChatterId("acp-1");
        Message current = new Message();
        current.setChatterId("user-1");
        current.setContent("read it");
        SessionDTO session = new SessionDTO();
        session.setMessageList(Arrays.asList(file, cancelled, current));
        when(sessionService.findSession("session-1")).thenReturn(session);

        List<Map<String, String>> files = handler.collectRecentFileUrls(
                "session-1", event(current));

        assertEquals(1, files.size());
        assertEquals("https://106.54.193.10:8550/chat/files/stored_note.txt",
                files.get(0).get("note.txt"));
    }

    @Test
    public void attachmentCollectionStopsAtPreviousUserText() {
        FileMessage oldFile = new FileMessage();
        oldFile.setChatterId("user-1");
        oldFile.setFileName("old.txt");
        oldFile.setUrl("files/old.txt");
        Message previousPrompt = new Message();
        previousPrompt.setChatterId("user-1");
        previousPrompt.setContent("use old file");
        Message robotReply = new Message();
        robotReply.setChatterId("acp-1");
        Message current = new Message();
        current.setChatterId("user-1");
        current.setContent("new prompt");
        SessionDTO session = new SessionDTO();
        session.setMessageList(Arrays.asList(oldFile, previousPrompt, robotReply, current));
        when(sessionService.findSession("session-1")).thenReturn(session);

        List<Map<String, String>> files = handler.collectRecentFileUrls(
                "session-1", event(current));

        assertTrue(files.isEmpty());
    }

    private MessageReceiveEvent event(String content) {
        Message message = new Message();
        message.setId("message-1");
        message.setChatterId("user-1");
        message.setContent(content);
        return event(message);
    }

    private MessageReceiveEvent event(Message message) {
        RobotChatter robot = new RobotChatter();
        robot.setId("acp-1");
        robot.setRobotGroup("acp");
        MessageReceiveEvent event = new MessageReceiveEvent();
        event.setSessionId("session-1");
        event.setRobotChatter(robot);
        event.setMessage(message);
        return event;
    }
}
