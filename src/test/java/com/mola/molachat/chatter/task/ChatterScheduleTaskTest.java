package com.mola.molachat.chatter.task;

import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.robot.constant.CmdProxyConstant;
import com.mola.molachat.robot.solution.AcpRuntimeStatusSolution;
import com.mola.molachat.team.solution.TeamGatewaySolution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Method;
import java.util.Collections;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ChatterScheduleTaskTest {

    @Mock
    private ChatterService chatterService;

    @Mock
    private TeamGatewaySolution teamGatewaySolution;

    @Mock
    private AcpRuntimeStatusSolution acpRuntimeStatusSolution;

    @InjectMocks
    private ChatterScheduleTask task;

    @Test
    public void mainAcpShowsDisconnectedStatusWhenItsInstanceIsUnavailable() throws Exception {
        ChatterDTO acp = acp(ChatterStatusEnum.ONLINE.getCode());
        when(chatterService.list()).thenReturn(Collections.singletonList(acp));
        when(teamGatewaySolution.findAcpSourceGroupIds(
                acp.getId(), acp.getVisibleChatterIds()))
                .thenReturn(Collections.singletonList("group-1"));
        when(acpRuntimeStatusSolution.isOnline("group-1")).thenReturn(false);

        invokeStatusCheck();

        verify(chatterService).setChatterStatus(
                acp.getId(), ChatterStatusEnum.DISCONNECT.getCode());
    }

    @Test
    public void mainAcpReturnsOnlineWhenItsInstanceReconnects() throws Exception {
        ChatterDTO acp = acp(ChatterStatusEnum.DISCONNECT.getCode());
        when(chatterService.list()).thenReturn(Collections.singletonList(acp));
        when(teamGatewaySolution.findAcpSourceGroupIds(
                acp.getId(), acp.getVisibleChatterIds()))
                .thenReturn(Collections.singletonList("group-1"));
        when(acpRuntimeStatusSolution.isOnline("group-1")).thenReturn(true);

        invokeStatusCheck();

        verify(chatterService).setChatterStatus(
                acp.getId(), ChatterStatusEnum.ONLINE.getCode());
    }

    private ChatterDTO acp(int status) {
        ChatterDTO acp = new ChatterDTO();
        acp.setId("acp-codex");
        acp.setRobot(true);
        acp.setRobotGroup(CmdProxyConstant.ACP);
        acp.setStatus(status);
        acp.setVisibleChatterIds(Collections.singleton("owner"));
        return acp;
    }

    private void invokeStatusCheck() throws Exception {
        Method method = ChatterScheduleTask.class
                .getDeclaredMethod("handleRobotStatusChange");
        method.setAccessible(true);
        method.invoke(task);
    }
}
