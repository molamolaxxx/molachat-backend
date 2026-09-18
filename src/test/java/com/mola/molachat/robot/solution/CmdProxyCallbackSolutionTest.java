package com.mola.molachat.robot.solution;

import com.mola.molachat.team.solution.TeamGatewaySolution;
import com.mola.molachat.team.solution.TeamCommandTransport;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.model.StreamMessageConnect;
import com.mola.molachat.session.solution.MessageSolution;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private MessageSolution messageSolution;

    @Mock
    private AcpDeferredMessageSolution acpDeferredMessageSolution;

    @Mock
    private SessionFactoryInterface sessionFactory;

    @InjectMocks
    private CmdProxyCallbackSolution solution;

    @Test
    public void realInterruptedTerminalPersistsOldTurnWithoutRefreshThenFlushesUser() {
        RobotChatter robot = new RobotChatter();
        robot.setId("acp-codex");
        Session session = new Session();
        session.setChatterSet(Collections.singleton(robot));
        when(sessionFactory.selectById("group-1")).thenReturn(session);
        when(messageSolution.findStreamConnect("acp-codex", "group-1"))
                .thenReturn(org.mockito.Mockito.mock(StreamMessageConnect.class));
        Map<String, String> result = new HashMap<>();
        result.put("groupId", "group-1");
        result.put("content", "");
        result.put("end", "Y");
        result.put("termination", "INTERRUPTED");

        solution.handleAcpResponse(result);

        org.mockito.InOrder order = inOrder(acpDeferredMessageSolution, messageSolution);
        order.verify(messageSolution).sendStreamMessageWithoutSessionRefresh(
                org.mockito.ArgumentMatchers.eq("group-1"),
                org.mockito.ArgumentMatchers.argThat(message ->
                        message instanceof StreamMessage
                                && ((StreamMessage) message).isEnd()
                                && "".equals(message.getContent())));
        order.verify(acpDeferredMessageSolution).flush("group-1");
        verify(messageSolution, never()).sendStreamMessage(
                org.mockito.ArgumentMatchers.eq("group-1"),
                org.mockito.ArgumentMatchers.any(StreamMessage.class));
    }

    @Test
    public void emptyInterruptedTerminalWithoutOldStreamOnlyFlushesUser() {
        RobotChatter robot = new RobotChatter();
        robot.setId("acp-codex");
        Session session = new Session();
        session.setChatterSet(Collections.singleton(robot));
        when(sessionFactory.selectById("group-1")).thenReturn(session);
        Map<String, String> result = new HashMap<>();
        result.put("groupId", "group-1");
        result.put("content", "");
        result.put("end", "Y");
        result.put("termination", "INTERRUPTED");

        solution.handleAcpResponse(result);

        verify(acpDeferredMessageSolution).flush("group-1");
        verify(messageSolution, never()).sendStreamMessageWithoutSessionRefresh(
                org.mockito.ArgumentMatchers.eq("group-1"),
                org.mockito.ArgumentMatchers.any(StreamMessage.class));
        verify(messageSolution, never()).sendStreamMessage(
                org.mockito.ArgumentMatchers.eq("group-1"),
                org.mockito.ArgumentMatchers.any(StreamMessage.class));
    }

    @Test
    public void activeHandshakeAndCallbackShareTheSameSyncProcessor() {
        Map<String, String> result = new HashMap<>();
        result.put("robots", "[]");
        result.put("visibleChatterIds", "[\"owner\"]");
        result.put("teamCmdProxyInstanceId", "instance-1");
        result.put("teamDiscovery", "{\"schemaVersion\":\"1\"}");
        when(teamGatewaySolution.shouldSyncOrdinaryRobots(
                "instance-1", Collections.singleton("owner"))).thenReturn(true);

        solution.handleAcpSyncRobots(result);

        verify(teamGatewaySolution).updateDiscovery(result);
        verify(acpRobotSyncSolution).sync(
                Collections.emptyList(), Collections.singleton("owner"));
    }

    @Test
    public void remoteInstanceUpdatesDiscoveryWithoutOverwritingOrdinaryRobots() {
        Map<String, String> result = new HashMap<>();
        result.put("robots", "[{\"name\":\"Remote\"}]");
        result.put("visibleChatterIds", "[\"owner\"]");
        result.put("teamCmdProxyInstanceId", "remote-1");
        result.put("teamDiscovery", "{\"schemaVersion\":\"1\"}");
        when(teamGatewaySolution.shouldSyncOrdinaryRobots(
                "remote-1", Collections.singleton("owner"))).thenReturn(false);

        solution.handleAcpSyncRobots(result);

        verify(teamGatewaySolution).updateDiscovery(result);
        verify(acpRobotSyncSolution, never()).sync(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    public void legacyOrdinarySyncRemainsCompatibleWithoutTeamInstanceSummary() {
        Map<String, String> result = new HashMap<>();
        result.put("robots", "[]");
        result.put("visibleChatterIds", "[\"owner\"]");

        solution.handleAcpSyncRobots(result);

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
