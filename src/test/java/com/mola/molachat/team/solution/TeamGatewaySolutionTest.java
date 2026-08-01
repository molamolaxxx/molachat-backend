package com.mola.molachat.team.solution;

import com.mola.molachat.team.dto.TeamCreateMemberRequest;
import com.mola.molachat.team.dto.TeamCreateRequest;
import com.mola.molachat.team.dto.TeamDTO;
import com.alibaba.fastjson.JSON;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TeamGatewaySolutionTest {

    @Mock
    private TeamEventSolution teamEventSolution;

    @Mock
    private TeamCommandTransport teamCommandTransport;

    @Mock
    private TeamRobotProjectionSolution teamRobotProjectionSolution;

    @Mock
    private TeamSnapshotSolution teamSnapshotSolution;

    @Mock
    private KeyValueFactoryInterface keyValueFactory;

    @InjectMocks
    private TeamGatewaySolution gatewaySolution;

    @Test
    public void updateDiscoveryAcceptsMatchingVersionedDescriptor() {
        gatewaySolution.updateDiscovery(discoveryResult(false));

        assertEquals("team-acp-instance-1",
                gatewaySolution.getDiscovery("owner").getTransportGroup());
        assertEquals("acpTeamDescribe",
                gatewaySolution.getDiscovery("owner").getDescribeCommand());
        assertFalse(gatewaySolution.isBusinessReady());
        verify(teamCommandTransport).registerEventCallback(
                org.mockito.ArgumentMatchers.eq("acpTeamEvent"),
                org.mockito.ArgumentMatchers.eq("team-acp-instance-1"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void updateDiscoveryRejectsMismatchedSummaryFields() {
        Map<String, String> result = discoveryResult(false);
        result.put("teamTransportGroup", "team-acp-another-instance");

        gatewaySolution.updateDiscovery(result);

        try {
            gatewaySolution.getDiscovery("owner");
            fail("invalid discovery must remain unavailable");
        } catch (TeamCommandException expected) {
            assertEquals("TEAM_NOT_READY", expected.getCode());
        }
    }

    @Test
    public void missingDiscoveryKeepsGatewayUnavailable() {
        gatewaySolution.updateDiscovery(new HashMap<>());

        try {
            gatewaySolution.getDiscovery("owner");
            fail("missing discovery must remain unavailable");
        } catch (TeamCommandException expected) {
            assertEquals("TEAM_NOT_READY", expected.getCode());
        }
        assertFalse(gatewaySolution.isBusinessReady());
    }

    @Test
    public void listUsesSingleJsonPayloadAndParsesStringDataEnvelope() {
        gatewaySolution.updateDiscovery(discoveryResult(true));
        Map<String, String> result = acceptedResult(
                "{\"teams\":[{\"teamId\":\"team-1\",\"ownerChatterId\":\"owner\","
                        + "\"name\":\"Fast\",\"state\":\"CREATING\",\"version\":1,\"members\":[]}],"
                        + "\"snapshotVersion\":1}");
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-instance-1"), anyString())).thenReturn(result);

        List<TeamDTO> teams = gatewaySolution.list("owner");

        assertEquals(1, teams.size());
        assertEquals("CREATING", teams.get(0).getStatus());
        verify(teamRobotProjectionSolution).syncAll("owner", teams);
        org.mockito.ArgumentCaptor<String> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport).send(eq("acpTeamList"),
                eq("team-acp-instance-1"), payloadCaptor.capture());
        assertEquals("owner", JSON.parseObject(payloadCaptor.getValue()).getString("ownerChatterId"));
        assertEquals("1", JSON.parseObject(payloadCaptor.getValue()).getString("schemaVersion"));
    }

    @Test
    public void emptyListDoesNotDestructivelyClearStaleOwnerProjection() {
        gatewaySolution.updateDiscovery(discoveryResult(true));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-instance-1"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[],\"snapshotVersion\":1}"));

        List<TeamDTO> teams = gatewaySolution.list("owner");

        assertEquals(0, teams.size());
        org.mockito.Mockito.verifyZeroInteractions(teamRobotProjectionSolution);
    }

    @Test
    public void existingTeamSelectsItsOwningInstanceInsteadOfLastDiscovery() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-linux"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[{\"teamId\":\"team-linux\","
                        + "\"ownerChatterId\":\"owner\",\"state\":\"READY\","
                        + "\"version\":56,\"members\":[]}],\"snapshotVersion\":56}"));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[],\"snapshotVersion\":0}"));

        assertEquals("linux",
                gatewaySolution.getDiscovery("owner").getCmdProxyInstanceId());
        assertEquals(1, gatewaySolution.list("owner").size());
        verify(teamRobotProjectionSolution).syncAll(eq("owner"),
                org.mockito.ArgumentMatchers.<List<TeamDTO>>argThat(teams ->
                        teams.size() == 1 && "team-linux".equals(teams.get(0).getTeamId())));
    }

    @Test
    public void laterDiscoveryWithExistingTeamRepairsEarlierEmptyBinding() {
        gatewaySolution.updateDiscovery(discoveryResult("windows", true));
        assertEquals("windows",
                gatewaySolution.getDiscovery("owner").getCmdProxyInstanceId());

        gatewaySolution.updateDiscovery(discoveryResult("linux", true));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-linux"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[{\"teamId\":\"team-linux\","
                        + "\"ownerChatterId\":\"owner\",\"state\":\"READY\","
                        + "\"version\":56,\"members\":[]}]}"));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[]}"));

        assertEquals("linux",
                gatewaySolution.getDiscovery("owner").getCmdProxyInstanceId());
    }

    @Test
    public void multipleEmptyInstancesFailClosedInsteadOfLastWriterWins() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true));
        when(teamCommandTransport.send(eq("acpTeamList"), anyString(), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[],\"snapshotVersion\":0}"));

        try {
            gatewaySolution.getDiscovery("owner");
            fail("ambiguous instances must not be selected");
        } catch (TeamCommandException expected) {
            assertEquals("CMD_PROXY_INSTANCE_CONFLICT", expected.getCode());
        }
    }

    @Test
    public void candidatesComeOnlyFromBoundInstanceSnapshot() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                "[{\"name\":\"Linux Robot\",\"avatar\":\"linux.png\"}]"));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[{\"name\":\"Windows Robot\",\"avatar\":\"windows.png\"}]"));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-linux"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[{\"teamId\":\"team-linux\","
                        + "\"ownerChatterId\":\"owner\",\"state\":\"READY\","
                        + "\"version\":1,\"members\":[]}]}"));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[]}"));

        assertEquals(1, gatewaySolution.listCandidates("owner").size());
        assertEquals("linux", gatewaySolution.listCandidates("owner").get(0)
                .getCmdProxyInstanceId());
        assertEquals("acp-Linux_Robot", gatewaySolution.listCandidates("owner").get(0)
                .getSourceRobotId());
    }

    @Test
    public void createDerivesStableIdsFromRequestIdForSafeHttpRetry() {
        gatewaySolution.updateDiscovery(discoveryResult(true));
        TeamCreateRequest request = createRequest();
        when(teamCommandTransport.send(eq("acpTeamCreate"),
                eq("team-acp-instance-1"), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"team-result\",\"state\":\"CREATING\","
                        + "\"version\":1,\"members\":[]}"));

        gatewaySolution.create(request);
        gatewaySolution.create(request);

        org.mockito.ArgumentCaptor<String> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport, org.mockito.Mockito.times(2)).send(
                eq("acpTeamCreate"), eq("team-acp-instance-1"), payloadCaptor.capture());
        assertEquals(payloadCaptor.getAllValues().get(0), payloadCaptor.getAllValues().get(1));
    }

    @Test
    public void deleteCarriesExpectedVersionAndParsesTerminalTeam() {
        gatewaySolution.updateDiscovery(discoveryResult(true));
        when(teamCommandTransport.send(eq("acpTeamDelete"),
                eq("team-acp-instance-1"), anyString()))
                .thenReturn(acceptedResult("{\"team\":{\"teamId\":\"team-1\","
                        + "\"state\":\"DELETED\",\"version\":4,\"members\":[]},"
                        + "\"warnings\":[],\"resources\":{\"clients\":0}}"));

        TeamDTO deleted = gatewaySolution.delete("owner", "team-1", "delete-1", 2L);

        assertEquals("DELETED", deleted.getStatus());
        org.mockito.ArgumentCaptor<String> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport).send(eq("acpTeamDelete"),
                eq("team-acp-instance-1"), payloadCaptor.capture());
        assertEquals(2L, JSON.parseObject(payloadCaptor.getValue())
                .getLongValue("expectedVersion"));
        assertEquals("delete-1", JSON.parseObject(payloadCaptor.getValue())
                .getString("requestId"));
    }

    private Map<String, String> discoveryResult(boolean ready) {
        return discoveryResult("instance-1", ready);
    }

    private Map<String, String> discoveryResult(String instanceId, boolean ready) {
        return discoveryResult(instanceId, ready, "[]");
    }

    private Map<String, String> discoveryResult(String instanceId, boolean ready,
                                                 String robots) {
        Map<String, String> result = new HashMap<>();
        result.put("teamSchemaVersion", "1");
        result.put("teamCmdProxyInstanceId", instanceId);
        result.put("teamTransportGroup", "team-acp-" + instanceId);
        result.put("visibleChatterIds", "[\"owner\"]");
        result.put("robots", robots);
        String commands = ready
                ? "[\"acpTeamDescribe\",\"acpTeamCreate\",\"acpTeamList\",\"acpTeamGet\","
                    + "\"acpTeamDelete\",\"acpTeamMemoryDream\"]"
                : "[\"acpTeamDescribe\"]";
        result.put("teamDiscovery", "{"
                + "\"schemaVersion\":\"1\","
                + "\"cmdProxyInstanceId\":\"" + instanceId + "\","
                + "\"transportGroup\":\"team-acp-" + instanceId + "\","
                + "\"robotGroup\":\"team-acp\","
                + "\"describeCommand\":\"acpTeamDescribe\","
                + "\"eventCommand\":\"acpTeamEvent\","
                + "\"businessCommandsReady\":" + ready + ","
                + "\"commands\":" + commands
                + "}");
        return result;
    }

    private Map<String, String> acceptedResult(String data) {
        Map<String, String> result = new HashMap<>();
        result.put("schemaVersion", "1");
        result.put("requestId", "request-1");
        result.put("accepted", "true");
        result.put("code", "OK");
        result.put("message", "ok");
        result.put("teamVersion", "1");
        result.put("data", data);
        return result;
    }

    private TeamCreateRequest createRequest() {
        TeamCreateRequest request = new TeamCreateRequest();
        request.setChatterId("owner");
        request.setToken("token");
        request.setRequestId("request-1");
        request.setName("Fast");
        TeamCreateMemberRequest first = new TeamCreateMemberRequest();
        first.setSourceRobotId("acp-first");
        first.setSourceGroupId("first-group");
        TeamCreateMemberRequest second = new TeamCreateMemberRequest();
        second.setSourceRobotId("acp-second");
        second.setSourceGroupId("second-group");
        request.setMembers(java.util.Arrays.asList(first, second));
        return request;
    }
}
