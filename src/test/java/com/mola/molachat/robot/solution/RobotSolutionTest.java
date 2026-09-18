package com.mola.molachat.robot.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.solution.TeamAcpExecSolution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RobotSolutionTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @Mock
    private SessionService sessionService;

    @Mock
    private TeamAcpExecSolution teamAcpExecSolution;

    @Mock
    private AcpRuntimeStatusSolution acpRuntimeStatusSolution;

    @Mock
    private AcpPromptSolution acpPromptSolution;

    @Mock
    private OcrSolution ocrSolution;

    @Mock
    private MessageSolution messageSolution;

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

    @Test
    public void busyAcpFileOnlyCancelsCurrentPrompt() {
        RobotChatter robot = new RobotChatter();
        robot.setRobotGroup("acp");
        FileMessage file = new FileMessage();
        file.setId("file-1");
        when(acpRuntimeStatusSolution.getStatus("session-1")).thenReturn("BUSY");
        when(acpPromptSolution.cancelPrompt("session-1"))
                .thenReturn(AcpPromptSolution.InvokeResult.accepted("INTERRUPTED", "cancelled"));
        when(ocrSolution.ocr(file)).thenReturn(ServerResponse.createByError());

        robotSolution.handleFileMessage(file, "session-1", robot);

        verify(acpPromptSolution).cancelPrompt("session-1");
        verify(ocrSolution).ocr(file);
    }

    @Test
    public void readyAcpFileDoesNotCancelOrSendPrompt() {
        RobotChatter robot = new RobotChatter();
        robot.setRobotGroup("acp");
        FileMessage file = new FileMessage();
        when(acpRuntimeStatusSolution.getStatus("session-1")).thenReturn("READY");
        when(ocrSolution.ocr(file)).thenReturn(ServerResponse.createByError());

        robotSolution.handleFileMessage(file, "session-1", robot);

        verify(acpPromptSolution, never()).cancelPrompt("session-1");
        verify(ocrSolution).ocr(file);
    }
}
