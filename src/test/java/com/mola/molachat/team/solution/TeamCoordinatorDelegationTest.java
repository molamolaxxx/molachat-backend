package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.team.dto.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** The MolaChat boundary proxies one request, never creates participant fragments. */
public class TeamCoordinatorDelegationTest {
    private TeamGatewaySolution gateway;
    private TeamCommandTransport transport;
    private TeamSnapshotSolution snapshots;
    @Before public void setup() {
        gateway = new TeamGatewaySolution(); transport = mock(TeamCommandTransport.class);
        snapshots = new TeamSnapshotSolution();
        ReflectionTestUtils.setField(gateway, "teamCommandTransport", transport);
        ReflectionTestUtils.setField(gateway, "teamSnapshotSolution", snapshots);
        ReflectionTestUtils.setField(gateway, "teamRobotProjectionSolution", mock(TeamRobotProjectionSolution.class));
        ReflectionTestUtils.setField(gateway, "teamEventSolution", mock(TeamEventSolution.class));
        ReflectionTestUtils.setField(gateway, "keyValueFactory", mock(KeyValueFactoryInterface.class));
        JSONObject descriptor = JSON.parseObject("{\"schemaVersion\":\"1\",\"cmdProxyInstanceId\":\"home\",\"transportGroup\":\"team-acp-home\",\"robotGroup\":\"team-acp\",\"eventCommand\":\"acpTeamEvent\",\"businessCommandsReady\":true,\"commands\":[\"acpTeamCoordinator\",\"acpTeamList\",\"acpTeamCreate\",\"acpTeamSend\"],\"teamMemberSources\":[{\"ownerChatterId\":\"user\",\"sourceGroupId\":\"local\",\"sourceRobotId\":\"robot\"}]}");
        Map<String, String> discovery = new HashMap<>(); discovery.put("teamDiscovery", descriptor.toJSONString());
        discovery.put("teamSchemaVersion", "1"); discovery.put("teamCmdProxyInstanceId", "home");
        discovery.put("teamTransportGroup", "team-acp-home"); discovery.put("visibleChatterIds", "[\"user\"]");
        gateway.updateDiscovery(discovery);
    }

    @Test public void sharedRemoteCandidatesAreRequestedWithAuthenticatedChatterOwner() {
        when(transport.send(eq("acpTeamCoordinator"), eq("team-acp-home"), anyString()))
                .thenReturn(result("{\"sources\":[{\"ownerChatterId\":\"user\",\"cmdProxyInstanceId\":\"remote\",\"sourceGroupId\":\"shared\",\"sourceRobotId\":\"remote-agent\",\"sourceType\":\"REMOTE\",\"status\":\"AVAILABLE\"}]}"));
        List<TeamMemberDTO> candidates = gateway.listCandidates("user");
        assertEquals(1, candidates.size()); assertEquals("remote", candidates.get(0).getCmdProxyInstanceId());
        JSONObject request = sent(); assertEquals("user", request.getString("ownerChatterId")); assertEquals("sources", request.getString("operation"));
    }

    @Test public void mixedCreateIsOneCoordinatorCallAndPreservesCompleteProjection() {
        when(transport.send(eq("acpTeamCoordinator"), eq("team-acp-home"), anyString()))
                .thenReturn(result("{\"team\":{\"teamId\":\"team\",\"ownerChatterId\":\"user\",\"state\":\"READY\",\"coordinated\":true,\"members\":[{\"teamMemberId\":\"m1\"},{\"teamMemberId\":\"m2\"}]}}"));
        TeamCreateRequest request = new TeamCreateRequest(); request.setChatterId("user"); request.setRequestId("create-id"); request.setName("team");
        TeamCreateMemberRequest a = new TeamCreateMemberRequest(); a.setCmdProxyInstanceId("home");
        TeamCreateMemberRequest b = new TeamCreateMemberRequest(); b.setCmdProxyInstanceId("remote");
        request.setMembers(Arrays.asList(a, b));
        TeamDTO team = gateway.create(request);
        assertTrue(team.isCoordinated()); assertEquals(2, team.getMembers().size());
        assertEquals("create", sent().getString("operation"));
        assertEquals("create-id", sent().getJSONObject("payload").getString("requestId"));
        verify(transport, never()).send(eq("acpTeamCreate"), anyString(), anyString());
    }

    @Test public void productMemberSendRemainsAllowedAndIsDelegatedWithAttachments() {
        TeamDTO team = new TeamDTO(); team.setTeamId("team"); team.setOwnerChatterId("user"); team.setCoordinated(true); snapshots.upsert(team);
        when(transport.send(eq("acpTeamCoordinator"), eq("team-acp-home"), anyString(), eq(8000L))).thenReturn(result("{\"delivery\":\"QUEUED\"}"));
        Map<String, String> file = Collections.singletonMap("a.txt", "aGVsbG8=");
        gateway.send("user", "team", "ordinary-member", "client", "hello", Collections.singletonList(file));
        ArgumentCaptor<String> submitted = ArgumentCaptor.forClass(String.class);
        verify(transport).send(eq("acpTeamCoordinator"), eq("team-acp-home"), submitted.capture(), eq(8000L));
        JSONObject request = JSON.parseObject(submitted.getValue()); assertEquals("member", request.getString("operation"));
        JSONObject payload = request.getJSONObject("payload"); assertEquals("send", payload.getString("action"));
        assertEquals("ordinary-member", payload.getString("teamMemberId")); assertEquals("hello", payload.getString("message"));
        assertEquals("aGVsbG8=", payload.getJSONArray("files").getJSONObject(0).getString("a.txt"));
    }

    @Test public void coordinatorRejectionIsExposedWithoutCreatingOrDeletingLocally() {
        Map<String, String> rejected = result("{}"); rejected.put("accepted", "false"); rejected.put("code", "TEAM_GRANT_REVOKED"); rejected.put("message", "授权已撤销");
        when(transport.send(eq("acpTeamCoordinator"), eq("team-acp-home"), anyString())).thenReturn(rejected);
        TeamDTO team = new TeamDTO(); team.setTeamId("team"); team.setOwnerChatterId("user"); team.setCoordinated(true); snapshots.upsert(team);
        try { gateway.cancel("user", "team", "m1", "client"); fail(); }
        catch (TeamCommandException e) { assertEquals("TEAM_GRANT_REVOKED", e.getCode()); }
        verify(transport, never()).send(eq("acpTeamCancel"), anyString(), anyString());
    }

    private JSONObject sent() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(transport, atLeastOnce()).send(eq("acpTeamCoordinator"), eq("team-acp-home"), payload.capture());
        return JSON.parseObject(payload.getValue());
    }
    private static Map<String, String> result(String data) {
        Map<String, String> result = new HashMap<>(); result.put("schemaVersion", "1"); result.put("accepted", "true");
        result.put("code", "OK"); result.put("message", "OK"); result.put("data", data); return result;
    }
}
