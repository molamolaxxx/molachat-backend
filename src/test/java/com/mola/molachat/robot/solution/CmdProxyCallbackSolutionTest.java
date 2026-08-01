package com.mola.molachat.robot.solution;

import com.mola.molachat.team.solution.TeamGatewaySolution;
import com.mola.molachat.team.solution.TeamCommandTransport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.scheduling.TaskScheduler;

import java.util.Date;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class CmdProxyCallbackSolutionTest {

    @Mock
    private TeamGatewaySolution teamGatewaySolution;

    @Mock
    private AcpRobotSyncSolution acpRobotSyncSolution;

    @Mock
    private TeamCommandTransport teamCommandTransport;

    @Mock
    private TaskScheduler taskScheduler;

    @InjectMocks
    private CmdProxyCallbackSolution solution;

    @Test
    public void activeHandshakeAndCallbackShareTheSameSyncProcessor() {
        Map<String, String> result = new HashMap<>();
        result.put("robots", "[]");
        result.put("visibleChatterIds", "[\"owner\"]");
        result.put("teamDiscovery", "{\"schemaVersion\":\"1\"}");

        solution.handleAcpSyncRobots(result);

        verify(teamGatewaySolution).updateDiscovery(result);
        verify(acpRobotSyncSolution).sync(
                Collections.emptyList(), Collections.singleton("owner"));
    }

    @Test
    public void retryDelaySwitchesFromFastBackoffToPersistentRecovery() {
        assertEquals(0L, CmdProxyCallbackSolution.retryDelayMillis(0));
        assertEquals(1_000L, CmdProxyCallbackSolution.retryDelayMillis(1));
        assertEquals(2_000L, CmdProxyCallbackSolution.retryDelayMillis(2));
        assertEquals(16_000L, CmdProxyCallbackSolution.retryDelayMillis(5));
        assertEquals(30_000L, CmdProxyCallbackSolution.retryDelayMillis(6));
        assertEquals(30_000L, CmdProxyCallbackSolution.retryDelayMillis(100));
    }

    @Test
    public void recoveryIsSingleFlightAndKeepsSchedulingAfterFailure() {
        solution.ensureAcpSyncRecovery();
        solution.ensureAcpSyncRecovery();

        org.mockito.ArgumentCaptor<Runnable> taskCaptor =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(taskCaptor.capture(), any(Date.class));

        taskCaptor.getValue().run();

        verify(taskScheduler, times(2)).schedule(any(Runnable.class), any(Date.class));
    }
}
