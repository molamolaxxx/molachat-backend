package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import com.alibaba.fastjson.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.Assert.assertEquals;
import org.mockito.ArgumentCaptor;

@RunWith(MockitoJUnitRunner.class)
public class TeamAcpExecSolutionTest {

    @Mock
    private TeamGatewaySolution teamGatewaySolution;

    @Mock
    private SessionService sessionService;

    @Mock
    private MessageSolution messageSolution;

    @InjectMocks
    private TeamAcpExecSolution execSolution;

    @Test
    public void normalMessageUsesTeamSendAndSkipsOrdinaryAcpPath() {
        MessageReceiveEvent event = event("hello");

        BaseAction action = execSolution.handle(event);

        verify(teamGatewaySolution).send(eq("owner"), eq("team-1"), eq("member-1"),
                eq("team-acp-member-1"), eq("hello"), anyList());
        assertTrue(action.getSkip());
    }

    @Test
    public void cancelUsesTeamCancelCommand() {
        MessageSendAction action = (MessageSendAction) execSolution.handle(event("#acp-cancel#"));

        verify(teamGatewaySolution).cancel(
                "owner", "team-1", "member-1", "team-acp-member-1");
        assertEquals("已发送取消指令", action.getResponsesText());
    }

    @Test
    public void stopStreamCancelsBusyMemberAndStopsLocalTeamStream() {
        when(messageSolution.forceStopStream(
                "team-acp-member-1", "ownerteam-acp-member-1")).thenReturn(true);

        BaseAction action = execSolution.handle(event("#stop-stream#"));

        assertTrue(action.getSkip());
        verify(teamGatewaySolution).cancel(
                "owner", "team-1", "member-1", "team-acp-member-1");
        verify(messageSolution).forceStopStream(
                "team-acp-member-1", "ownerteam-acp-member-1");
    }

    @Test
    public void readyTeamMemberExposesNormalAcpCommands() {
        JSONObject status = new JSONObject();
        status.put("clientState", "READY");
        when(teamGatewaySolution.getStatus(
                "owner", "team-1", "member-1", "team-acp-member-1")).thenReturn(status);
        when(teamGatewaySolution.supportsCommand(
                "owner", "acpTeamMemoryDream")).thenReturn(true);
        RobotChatter robot = event("hello").getRobotChatter();
        robot.setVisibleChatterIds(java.util.Collections.singleton("owner"));

        java.util.List<com.mola.molachat.robot.model.CmdDescription> commands =
                execSolution.cmdDescriptions(robot, "ownerteam-acp-member-1");

        assertEquals(3, commands.size());
        assertEquals("#new-session#", commands.get(0).getCmdName());
        assertEquals("#acp-dream#", commands.get(1).getCmdName());
        assertEquals("#list-sessions#", commands.get(2).getCmdName());
    }

    @Test
    public void memoryDreamUsesTeamMemberCommand() {
        MessageSendAction action = (MessageSendAction) execSolution.handle(event("#acp-dream#"));

        verify(teamGatewaySolution).memoryDream(
                "owner", "team-1", "member-1", "team-acp-member-1");
        assertEquals("记忆整理已触发，将在后台执行", action.getResponsesText());
    }

    @Test
    public void newSessionReturnsPlainTextInsteadOfRuntimeJson() {
        MessageSendAction action = (MessageSendAction) execSolution.handle(event("#new-session#"));

        verify(teamGatewaySolution).newSession(
                "owner", "team-1", "member-1", "team-acp-member-1");
        assertEquals("已开启新会话", action.getResponsesText());
    }

    @Test
    public void contextUsageUsesTeamMemberCommand() {
        JSONObject data = new JSONObject();
        data.put("contextUsagePercentage", 42.5D);
        when(teamGatewaySolution.getContextUsage(
                "owner", "team-1", "member-1", "team-acp-member-1")).thenReturn(data);
        RobotChatter robot = event("hello").getRobotChatter();
        robot.setVisibleChatterIds(java.util.Collections.singleton("owner"));

        Double percentage = execSolution.fetchContextUsage(robot);

        assertEquals(42.5D, percentage, 0D);
    }

    @Test
    public void listSessionsUsesTheSameMarkdownTableAsOrdinaryAcp() {
        JSONObject data = com.alibaba.fastjson.JSON.parseObject("{\"sessions\":[{"
                + "\"sessionId\":\"session-1\",\"preview\":\"hello\","
                + "\"lastModified\":\"2026-07-30\",\"current\":true}]}");
        when(teamGatewaySolution.listSessions(
                "owner", "team-1", "member-1", "team-acp-member-1", 20)).thenReturn(data);

        BaseAction action = execSolution.handle(event("#list-sessions#"));

        ArgumentCaptor<StreamMessage> captor = ArgumentCaptor.forClass(StreamMessage.class);
        verify(messageSolution).sendStreamMessage(
                eq("ownerteam-acp-member-1"), captor.capture());
        assertTrue(action.getSkip());
        assertTrue(captor.getValue().getContent().startsWith("| 会话预览 | 最后修改 | 操作 |"));
        assertTrue(captor.getValue().getContent().contains("hello"));
        assertTrue(captor.getValue().isEnd());
    }

    private MessageReceiveEvent event(String content) {
        RobotChatter robot = new RobotChatter();
        robot.setId("team-acp-member-1");
        robot.setTeamId("team-1");
        robot.setTeamMemberId("member-1");
        Message message = new Message();
        message.setChatterId("owner");
        message.setContent(content);
        MessageReceiveEvent event = new MessageReceiveEvent();
        event.setRobotChatter(robot);
        event.setSessionId("ownerteam-acp-member-1");
        event.setMessage(message);
        return event;
    }
}
