package com.mola.molachat.robot.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.team.solution.TeamAcpExecSolution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RobotSolutionTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @Mock
    private SessionService sessionService;

    @Mock
    private TeamAcpExecSolution teamAcpExecSolution;

    @InjectMocks
    private RobotSolution robotSolution;

    @Test
    public void contextUsageRoutesTeamRobotToTeamGateway() {
        RobotChatter robot = new RobotChatter();
        robot.setId("team-acp-member-1");
        robot.setRobotGroup("team-acp");
        SessionDTO session = new SessionDTO();
        session.setChatterSet(Collections.singleton(robot));
        when(sessionService.findSession("session-1")).thenReturn(session);
        when(chatterFactory.select(robot.getId())).thenReturn(robot);
        when(teamAcpExecSolution.fetchContextUsage(robot)).thenReturn(42.5D);

        Double percentage = robotSolution.fetchContextUsage("session-1");

        assertEquals(42.5D, percentage, 0D);
        verify(teamAcpExecSolution).fetchContextUsage(robot);
    }
}
