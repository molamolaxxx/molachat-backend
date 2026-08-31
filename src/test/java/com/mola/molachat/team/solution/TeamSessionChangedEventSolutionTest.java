package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.dto.TeamEventDTO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TeamSessionChangedEventSolutionTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @Mock
    private MessageSolution messageSolution;

    @InjectMocks
    private TeamSessionChangedEventSolution solution;

    @Before
    public void setUp() {
        RobotChatter robot = new RobotChatter();
        robot.setId("team-acp-member-1");
        robot.setRobotGroup(TeamRobotProjectionSolution.TEAM_ACP_GROUP);
        robot.setTeamId("team-1");
        robot.setTeamMemberId("member-1");
        robot.setVisibleChatterIds(Collections.singleton("owner"));
        when(chatterFactory.select("team-acp-member-1")).thenReturn(robot);
    }

    @Test
    public void automaticIdleChangeStopsStreamAndSendsMessageOnce() {
        TeamEventDTO event = event("AUTO_IDLE", "new-session");

        solution.handle(event);
        solution.handle(event);

        verify(messageSolution, times(1)).forceStopStream(
                "team-acp-member-1", "ownerteam-acp-member-1");
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageSolution, times(1)).insertMessage(
                eq("ownerteam-acp-member-1"), captor.capture());
        assertEquals("已自动开启新会话", captor.getValue().getContent());
        assertEquals("team-acp-member-1", captor.getValue().getChatterId());
        assertEquals("ownerteam-acp-member-1", captor.getValue().getSessionId());
    }

    @Test
    public void manualChangeDoesNotDuplicateCommandResponse() {
        solution.handle(event("MANUAL", "manual-session"));

        verify(messageSolution, never()).forceStopStream(any(), any());
        verify(messageSolution, never()).insertMessage(any(), any());
    }

    private TeamEventDTO event(String reason, String newSessionId) {
        TeamEventDTO event = new TeamEventDTO();
        event.setSchemaVersion("1");
        event.setEventId("event-1");
        event.setTeamId("team-1");
        event.setTeamMemberId("member-1");
        event.setAcpClientId("team-acp-member-1");
        event.setType("MEMBER_SESSION_CHANGED");
        event.setData("{\"oldSessionId\":\"old-session\",\"newSessionId\":\""
                + newSessionId + "\",\"reason\":\"" + reason + "\"}");
        return event;
    }
}
