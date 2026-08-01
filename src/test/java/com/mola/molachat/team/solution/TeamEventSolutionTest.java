package com.mola.molachat.team.solution;

import com.mola.molachat.team.dto.TeamDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TeamEventSolutionTest {

    @Mock
    private TeamRobotProjectionSolution projectionSolution;

    @Mock
    private TeamMessageEventSolution messageEventSolution;

    @Mock
    private TeamTalkToEventSolution talkToEventSolution;

    @Mock
    private TeamSnapshotSolution teamSnapshotSolution;

    @InjectMocks
    private TeamEventSolution eventSolution;

    @Test
    public void teamReadySynchronizesRobotProjection() {
        Map<String, String> event = baseEvent("TEAM_READY");
        event.put("data", "{\"team\":{\"teamId\":\"team-1\",\"ownerChatterId\":\"owner\","
                + "\"name\":\"Fast\",\"state\":\"READY\",\"version\":2,\"members\":[]},"
                + "\"members\":[]}");

        eventSolution.handle(event);

        ArgumentCaptor<TeamDTO> captor = ArgumentCaptor.forClass(TeamDTO.class);
        verify(projectionSolution).sync(captor.capture());
        assertEquals("team-1", captor.getValue().getTeamId());
        assertEquals("READY", captor.getValue().getStatus());
    }

    @Test
    public void teamDeletedRemovesProjectionByTeamId() {
        eventSolution.handle(baseEvent("TEAM_DELETED"));

        verify(projectionSolution).delete("team-1");
    }

    @Test
    public void incompleteEventIsIgnored() {
        eventSolution.handle(new HashMap<>());

        verify(projectionSolution, never()).delete("team-1");
    }

    @Test
    public void duplicateEventIdIsHandledOnlyOnce() {
        Map<String, String> event = baseEvent("TEAM_DELETED");

        eventSolution.handle(event);
        eventSolution.handle(event);

        verify(projectionSolution, times(1)).delete("team-1");
    }

    @Test
    public void olderTeamVersionCannotDeleteNewerProjection() {
        Map<String, String> ready = baseEvent("TEAM_READY");
        ready.put("teamVersion", "3");
        ready.put("data", "{\"team\":{\"teamId\":\"team-1\",\"ownerChatterId\":\"owner\","
                + "\"name\":\"Fast\",\"state\":\"READY\",\"version\":3,\"members\":[]},"
                + "\"members\":[]}");
        eventSolution.handle(ready);

        Map<String, String> staleDelete = baseEvent("TEAM_DELETED");
        staleDelete.put("eventId", "event-2");
        staleDelete.put("teamVersion", "2");
        eventSolution.handle(staleDelete);

        verify(projectionSolution, never()).delete("team-1");
    }

    @Test
    public void talkToEventIsDelegatedWithoutChangingProjection() {
        Map<String, String> event = baseEvent("TALK_TO_QUEUED");
        event.put("data", "{\"messageId\":\"message-1\"}");

        eventSolution.handle(event);

        verify(talkToEventSolution).handle(org.mockito.ArgumentMatchers.any());
        verify(projectionSolution, never()).sync(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void deleteAcceptedSynchronizesDeletingProjection() {
        Map<String, String> event = baseEvent("TEAM_DELETE_ACCEPTED");
        event.put("data", "{\"team\":{\"teamId\":\"team-1\",\"state\":\"DELETING\","
                + "\"version\":3,\"members\":[]},\"previousState\":\"READY\","
                + "\"expectedVersion\":2}");

        eventSolution.handle(event);

        ArgumentCaptor<TeamDTO> captor = ArgumentCaptor.forClass(TeamDTO.class);
        verify(projectionSolution).sync(captor.capture());
        assertEquals("DELETING", captor.getValue().getStatus());
    }

    @Test
    public void createFailedUpdatesSnapshotWithoutCreatingProjection() {
        Map<String, String> event = baseEvent("TEAM_CREATE_FAILED");
        event.put("data", "{\"team\":{\"teamId\":\"team-1\","
                + "\"ownerChatterId\":\"owner\",\"state\":\"FAILED\","
                + "\"version\":2,\"members\":[]},"
                + "\"error\":{\"code\":\"SOURCE_ROBOT_NOT_FOUND\"}}");

        eventSolution.handle(event);

        ArgumentCaptor<TeamDTO> captor = ArgumentCaptor.forClass(TeamDTO.class);
        verify(teamSnapshotSolution).upsert(captor.capture());
        assertEquals("FAILED", captor.getValue().getStatus());
        verify(projectionSolution, never()).sync(org.mockito.ArgumentMatchers.any());
    }

    private Map<String, String> baseEvent(String type) {
        Map<String, String> event = new HashMap<>();
        event.put("schemaVersion", "1");
        event.put("eventId", "event-1");
        event.put("eventSeq", "1");
        event.put("transportGroup", "team-acp-instance-1");
        event.put("teamId", "team-1");
        event.put("type", type);
        event.put("teamVersion", "2");
        event.put("timestamp", "1");
        return event;
    }
}
