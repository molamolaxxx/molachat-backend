package com.mola.molachat.robot.solution;

import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.server.websocket.WSResponse;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.team.dto.TeamMemberSourceDTO;
import com.mola.molachat.team.solution.TeamGatewaySolution;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AcpSessionChangedSolutionTest {

    @Mock
    private TeamGatewaySolution teamGatewaySolution;

    @Mock
    private MessageSolution messageSolution;

    @Mock
    private ServerService serverService;

    @InjectMocks
    private AcpSessionChangedSolution solution;

    @Test
    public void rejectsEventWithoutExactInstanceAndGroupBinding() {
        solution.handle(event("instance-other", "group-1", "session-2"));

        assertNull(solution.currentSessionId("instance-other", "group-1"));
        verify(messageSolution, never()).forceStopStream(any(), any());
        verify(serverService, never()).sendResponse(any(), any());
    }

    @Test
    public void updatesProjectionClearsOldStreamAndNotifiesOwnerOnce() {
        TeamMemberSourceDTO source = new TeamMemberSourceDTO();
        source.setOwnerChatterId("owner");
        source.setSourceRobotId("acp-codex");
        source.setSourceGroupId("group-1");
        when(teamGatewaySolution.findAcpSource("instance-1", "group-1"))
                .thenReturn(source);

        Map<String, String> event = event("instance-1", "group-1", "session-2");
        solution.handle(event);
        solution.handle(event);

        assertEquals("session-2", solution.currentSessionId("instance-1", "group-1"));
        verify(messageSolution, times(1)).forceStopStream("acp-codex", "group-1");
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageSolution, times(1)).insertMessage(eq("group-1"), messageCaptor.capture());
        assertEquals("已自动开启新会话", messageCaptor.getValue().getContent());
        assertEquals("acp-codex", messageCaptor.getValue().getChatterId());
        ArgumentCaptor<WSResponse> responseCaptor = ArgumentCaptor.forClass(WSResponse.class);
        verify(serverService, times(1)).sendResponse(eq("owner"), responseCaptor.capture());
        assertEquals(Integer.valueOf(489), responseCaptor.getValue().getCode());
        Map data = (Map) responseCaptor.getValue().getData();
        assertEquals("session-2", data.get("newSessionId"));
        assertEquals("AUTO_IDLE", data.get("reason"));
    }

    @Test
    public void manualSessionChangeKeepsCmdProxyResponseAsTheOnlyChatMessage() {
        TeamMemberSourceDTO source = new TeamMemberSourceDTO();
        source.setOwnerChatterId("owner");
        source.setSourceRobotId("acp-codex");
        when(teamGatewaySolution.findAcpSource("instance-1", "group-1"))
                .thenReturn(source);
        Map<String, String> event = event("instance-1", "group-1", "session-manual");
        event.put("reason", "MANUAL");

        solution.handle(event);

        verify(messageSolution, never()).insertMessage(any(), any());
    }

    private Map<String, String> event(String instanceId, String groupId,
                                      String newSessionId) {
        Map<String, String> event = new HashMap<>();
        event.put("schemaVersion", "1");
        event.put("instanceId", instanceId);
        event.put("groupId", groupId);
        event.put("oldSessionId", "session-1");
        event.put("newSessionId", newSessionId);
        event.put("reason", "AUTO_IDLE");
        event.put("timestamp", "1786120000000");
        return event;
    }
}
