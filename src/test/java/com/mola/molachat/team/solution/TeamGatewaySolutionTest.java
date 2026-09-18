package com.mola.molachat.team.solution;

import com.mola.molachat.team.dto.TeamCreateMemberRequest;
import com.mola.molachat.team.dto.TeamCreateRequest;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamHomeDTO;
import com.mola.molachat.team.model.MixedTeamRecord;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;

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

    @Mock
    private MixedTeamStore mixedTeamStore;

    @InjectMocks
    private TeamGatewaySolution gatewaySolution;

    @Before
    public void defaultEmptyMixedTeamList() {
        when(mixedTeamStore.listByOwner(anyString())).thenReturn(Collections.emptyList());
    }

    @Test
    public void starweaveGatewayRoutesReadOperationsAwayFromMutations() {
        Map<String, String> request = new HashMap<>();
        request.put("operation", "list");
        assertEquals(TeamCommandTransport.CallbackLane.GATEWAY_QUERY,
                gatewaySolution.starweaveCallbackLane(request));

        request.put("operation", "sources");
        assertEquals(TeamCommandTransport.CallbackLane.GATEWAY_QUERY,
                gatewaySolution.starweaveCallbackLane(request));

        request.put("operation", "member");
        request.put("payload", "{\"action\":\"status\"}");
        assertEquals(TeamCommandTransport.CallbackLane.GATEWAY_QUERY,
                gatewaySolution.starweaveCallbackLane(request));

        request.put("payload", "{\"action\":\"send\"}");
        assertEquals(TeamCommandTransport.CallbackLane.GATEWAY_MUTATION,
                gatewaySolution.starweaveCallbackLane(request));

        request.put("operation", "create");
        assertEquals(TeamCommandTransport.CallbackLane.GATEWAY_MUTATION,
                gatewaySolution.starweaveCallbackLane(request));
    }

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
    public void starweaveOwnerDiscoversHomeAndAuthorizedRemoteSources() {
        String owner = "starweave-instance-b";
        gatewaySolution.updateDiscovery(discoveryResult("instance-b", true,
                "[{\"ownerChatterId\":\"" + owner + "\","
                        + "\"sourceGroupId\":\"starweave-main:instance-b:local\","
                        + "\"sourceRobotId\":\"acp-local\","
                        + "\"robotName\":\"Local\",\"displayName\":\"Local\"}]"));
        gatewaySolution.updateDiscovery(discoveryResult("instance-a", true, "[]",
                "[{\"granteeOwnerChatterId\":\"" + owner + "\","
                        + "\"participantInstanceId\":\"instance-a\","
                        + "\"sourceGroupId\":\"team-shared-instance-a-acp-remote\","
                        + "\"sourceRobotId\":\"acp-remote\","
                        + "\"robotName\":\"Remote\",\"displayName\":\"Remote\"}]"));

        List<com.mola.molachat.team.dto.TeamMemberDTO> candidates =
                gatewaySolution.listCandidates(owner);

        assertEquals(2, candidates.size());
        assertEquals("instance-b", gatewaySolution.getHome(owner)
                .getHomeCmdProxyInstanceId());
        assertTrue(candidates.stream().anyMatch(candidate ->
                "HOME".equals(candidate.getSourceType())
                        && "acp-local".equals(candidate.getSourceRobotId())));
        assertTrue(candidates.stream().anyMatch(candidate ->
                "REMOTE".equals(candidate.getSourceType())
                        && "acp-remote".equals(candidate.getSourceRobotId())));
    }

    @Test
    public void starweaveOwnerCreatesMixedTeamFromHomeAndAuthorizedRemoteSource() {
        backMixedStoreWithMemory();
        String owner = "starweave-instance-b";
        gatewaySolution.updateDiscovery(discoveryResult("instance-b", true,
                "[{\"ownerChatterId\":\"" + owner + "\","
                        + "\"sourceGroupId\":\"starweave-main:instance-b:local\","
                        + "\"sourceRobotId\":\"acp-local\","
                        + "\"robotName\":\"Local\",\"displayName\":\"Local\"}]"));
        gatewaySolution.updateDiscovery(discoveryResult("instance-a", true, "[]",
                "[{\"granteeOwnerChatterId\":\"" + owner + "\","
                        + "\"participantInstanceId\":\"instance-a\","
                        + "\"sourceGroupId\":\"team-shared-instance-a-acp-remote\","
                        + "\"sourceRobotId\":\"acp-remote\","
                        + "\"robotName\":\"Remote\",\"displayName\":\"Remote\"}]"));
        when(teamCommandTransport.send(eq("acpTeamCreate"), anyString(), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"fragment\","
                        + "\"state\":\"CREATING\",\"members\":[]}"));
        TeamCreateRequest request = new TeamCreateRequest();
        request.setChatterId(owner);
        request.setToken("token");
        request.setRequestId("starweave-mixed-request");
        request.setName("Starweave Mixed");
        TeamCreateMemberRequest home = new TeamCreateMemberRequest();
        home.setCmdProxyInstanceId("instance-b");
        home.setTransportGroup("team-acp-instance-b");
        home.setSourceGroupId("starweave-main:instance-b:local");
        home.setSourceRobotId("acp-local");
        TeamCreateMemberRequest remote = new TeamCreateMemberRequest();
        remote.setCmdProxyInstanceId("instance-a");
        remote.setTransportGroup("team-acp-instance-a");
        remote.setSourceGroupId("team-shared-instance-a-acp-remote");
        remote.setSourceRobotId("acp-remote");
        request.setMembers(java.util.Arrays.asList(home, remote));

        TeamDTO created = gatewaySolution.create(request);

        assertEquals(owner, created.getOwnerChatterId());
        assertEquals("instance-b", created.getMembers().get(0).getCmdProxyInstanceId());
        assertEquals("instance-a", created.getMembers().get(1).getCmdProxyInstanceId());
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-instance-b"), anyString());
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-instance-a"), anyString());
    }

    @Test
    public void mixedParticipantEventIsProjectedBackToStarweaveHomeTransport() {
        Map<String, Consumer<Map<String, String>>> callbacks = captureAllCallbacks();
        String owner = "starweave-instance-b";
        gatewaySolution.updateDiscovery(discoveryResult("instance-b", true,
                "[{\"ownerChatterId\":\"" + owner + "\","
                        + "\"sourceGroupId\":\"starweave-main:instance-b:local\","
                        + "\"sourceRobotId\":\"acp-local\","
                        + "\"robotName\":\"Local\",\"displayName\":\"Local\"}]"));
        MixedTeamRecord record = new MixedTeamRecord();
        record.setTeamId("starweave-mixed-team");
        record.setOwnerChatterId(owner);
        record.setName("Starweave Mixed");
        record.setState("READY");
        record.setHomeInstanceId("instance-b");
        MixedTeamRecord.Participant home = new MixedTeamRecord.Participant();
        home.setInstanceId("instance-b");
        home.setTransportGroup("team-acp-instance-b");
        home.setState("READY");
        record.setParticipants(Collections.singletonList(home));
        when(mixedTeamStore.find("starweave-mixed-team")).thenReturn(record);
        Map<String, String> event = event("team-acp-instance-b",
                "starweave-mixed-team", "MESSAGE_COMPLETE", "{\"content\":\"done\"}");
        event.put("eventId", "event-1");
        event.put("eventSeq", "7");

        callbacks.get("acpTeamEvent@team-acp-instance-b").accept(event);

        org.mockito.ArgumentCaptor<String> payload =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport, timeout(1000)).send(
                eq("starweaveTeamGatewayEvent"),
                eq("team-acp-instance-b"), payload.capture());
        JSONObject wrapper = JSON.parseObject(payload.getValue());
        assertEquals(owner, wrapper.getString("ownerChatterId"));
        assertEquals("event-1", wrapper.getJSONObject("event").getString("eventId"));
        assertEquals("done", wrapper.getJSONObject("event")
                .getJSONObject("data").getString("content"));
    }

    @Test
    public void starweaveGatewayAuthenticatesCallbackTransportAndResendsReadyHandshake() {
        Map<String, Consumer<Map<String, String>>> callbacks = captureAllCallbacks();
        AtomicReference<JSONObject> response = new AtomicReference<>();
        doAnswer(invocation -> {
            String command = (String) invocation.getArguments()[0];
            if ("starweaveTeamGatewayResult".equals(command)) {
                response.set(JSON.parseObject((String) invocation.getArguments()[2]));
            }
            return acceptedResult("{}");
        }).when(teamCommandTransport).send(anyString(), anyString(), anyString());
        String owner = "starweave-instance-b";
        Map<String, String> discovery = discoveryResult("instance-b", true,
                "[{\"ownerChatterId\":\"" + owner + "\","
                        + "\"sourceGroupId\":\"starweave-main:instance-b:local\","
                        + "\"sourceRobotId\":\"acp-local\","
                        + "\"robotName\":\"Local\",\"displayName\":\"Local\"}]");
        gatewaySolution.updateDiscovery(discovery);
        gatewaySolution.updateDiscovery(discovery);

        Map<String, String> request = new HashMap<>();
        request.put("requestId", "star-request-1");
        request.put("instanceId", "instance-b");
        request.put("ownerChatterId", owner);
        request.put("operation", "sources");
        request.put("payload", "{}");
        callbacks.get("starweaveTeamGateway@team-acp-instance-b").accept(request);

        assertTrue(response.get().getBooleanValue("accepted"));
        assertEquals(1, response.get().getJSONObject("data")
                .getJSONArray("sources").size());
        verify(teamCommandTransport, times(2)).send(
                eq("starweaveTeamGatewayReady"), eq("team-acp-instance-b"), anyString());

        request.put("instanceId", "instance-a");
        request.put("requestId", "forged-request");
        callbacks.get("starweaveTeamGateway@team-acp-instance-b").accept(request);
        assertFalse(response.get().getBooleanValue("accepted"));
        assertEquals("UNAUTHORIZED", response.get().getString("code"));
    }

    @Test
    public void acpSourceLookupRequiresMatchingInstanceAndGroup() {
        gatewaySolution.updateDiscovery(discoveryResult("instance-1", true,
                "[{\"ownerChatterId\":\"owner\",\"sourceGroupId\":\"group-1\","
                        + "\"sourceRobotId\":\"acp-codex\"}]"));

        assertNotNull(gatewaySolution.findAcpSource("instance-1", "group-1"));
        assertNull(gatewaySolution.findAcpSource("instance-other", "group-1"));
        assertNull(gatewaySolution.findAcpSource("instance-1", "group-other"));
        assertEquals(true, gatewaySolution.isAcpSourceAvailable(
                "acp-codex", Collections.singleton("owner")));
        assertEquals(false, gatewaySolution.isAcpSourceAvailable(
                "acp-codex", Collections.singleton("other-owner")));
        assertEquals(Collections.singletonList("group-1"),
                gatewaySolution.findAcpSourceGroupIds(
                        "acp-codex", Collections.singleton("owner")));
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
    public void slowHomeProbeDoesNotHoldOwnerLock() throws Exception {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true));
        CountDownLatch probeStarted = new CountDownLatch(1);
        CountDownLatch releaseProbe = new CountDownLatch(1);
        when(teamCommandTransport.send(eq("acpTeamList"), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    probeStarted.countDown();
                    releaseProbe.await();
                    return acceptedResult("{\"teams\":[],\"snapshotVersion\":0}");
                });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> list = executor.submit(() -> {
                try {
                    gatewaySolution.list("owner");
                } catch (TeamCommandException expected) {
                    assertEquals("CMD_PROXY_INSTANCE_CONFLICT", expected.getCode());
                }
            });
            assertTrue(probeStarted.await(1, TimeUnit.SECONDS));

            Future<TeamHomeDTO> home = executor.submit(() -> gatewaySolution.getHome("owner"));
            assertTrue(home.get(1, TimeUnit.SECONDS).isSelectionRequired());

            releaseProbe.countDown();
            list.get(2, TimeUnit.SECONDS);
        } finally {
            releaseProbe.countDown();
            executor.shutdownNow();
        }
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
    public void singleHomeCandidateIsPersistedAutomatically() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true));

        TeamHomeDTO home = gatewaySolution.getHome("owner");

        assertEquals("linux", home.getHomeCmdProxyInstanceId());
        assertFalse(home.isSelectionRequired());
        assertEquals(1, home.getDevices().size());
        assertEquals(true, home.getDevices().get(0).isSelected());
        verify(keyValueFactory).save(org.mockito.ArgumentMatchers.argThat(value ->
                "fast-team.instance-binding.owner".equals(value.getKey())
                        && "linux".equals(value.getValue())));
    }

    @Test
    public void multipleHomeCandidatesRequireExplicitSelection() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                sources("owner", "linux", false)));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                sources("owner", "windows", false)));

        TeamHomeDTO home = gatewaySolution.getHome("owner");

        assertNull(home.getHomeCmdProxyInstanceId());
        assertEquals(true, home.isSelectionRequired());
        assertEquals(2, home.getDevices().size());
        assertEquals("HOME_SELECTION_REQUIRED",
                gatewaySolution.listCandidates("owner").get(0).getStatus());
    }

    @Test
    public void explicitHomeSelectionMarksOnlySelectedPlacementAsHome() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                "[]", remoteSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                sources("owner", "windows", false)));

        gatewaySolution.selectHome("owner", "windows");

        List<com.mola.molachat.team.dto.TeamMemberDTO> candidates =
                gatewaySolution.listCandidates("owner");
        assertEquals("REMOTE", candidates.get(0).getSourceType());
        assertEquals("HOME", candidates.get(1).getSourceType());
        assertEquals("本机", candidates.get(1).getSourceLabel());
    }

    @Test
    public void candidatesComeFromEveryInstanceTeamDiscoveryForSameOwner() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                sourcesWithRemark("owner", "linux", "负责代码实现")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");

        assertEquals(2, gatewaySolution.listCandidates("owner").size());
        assertEquals("linux", gatewaySolution.listCandidates("owner").get(0)
                .getCmdProxyInstanceId());
        assertEquals("source-linux-1", gatewaySolution.listCandidates("owner").get(0)
                .getSourceRobotId());
        assertEquals("负责代码实现", gatewaySolution.listCandidates("owner").get(0)
                .getRemark());
        assertEquals("REMOTE", gatewaySolution.listCandidates("owner").get(1)
                .getSourceType());
        assertEquals("remote", gatewaySolution.listCandidates("owner").get(1)
                .getRemark());
    }

    @Test
    public void candidatesAreFilteredBySourceOwner() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                sources("another-owner", "linux", false)));

        assertEquals(0, gatewaySolution.listCandidates("owner").size());
    }

    @Test
    public void oldProtocolIsExplicitAndNeverFallsBackToRobots() {
        Map<String, String> result = discoveryResult("legacy", true, null);
        result.put("robots", "[{\"name\":\"must-not-leak\"}]");
        gatewaySolution.updateDiscovery(result);

        assertEquals(1, gatewaySolution.listCandidates("owner").size());
        assertEquals("DISCOVERY_UNSUPPORTED",
                gatewaySolution.listCandidates("owner").get(0).getStatus());
        assertNull(gatewaySolution.listCandidates("owner").get(0).getSourceRobotId());
    }

    @Test
    public void notReadyInstanceKeepsSourceButRejectsPlacement() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", false,
                sources("owner", "linux", true)));

        assertEquals("BUSINESS_COMMANDS_NOT_READY",
                gatewaySolution.listCandidates("owner").get(0).getStatus());
        try {
            gatewaySolution.create(createRequest("linux"));
            fail("not ready placement must be rejected");
        } catch (TeamCommandException expected) {
            assertEquals("TEAM_NOT_READY", expected.getCode());
        }
    }

    @Test
    public void staleDiscoveryKeepsSourceButRejectsPlacement() throws Exception {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.getHome("owner");
        java.lang.reflect.Field discoveriesField = TeamGatewaySolution.class
                .getDeclaredField("discoveries");
        discoveriesField.setAccessible(true);
        Object registration = ((Map<?, ?>) discoveriesField.get(gatewaySolution)).get("linux");
        java.lang.reflect.Field lastSeenAt = registration.getClass()
                .getDeclaredField("lastSeenAt");
        lastSeenAt.setAccessible(true);
        lastSeenAt.setLong(registration, 0L);

        assertEquals("DISCOVERY_STALE",
                gatewaySolution.listCandidates("owner").get(0).getStatus());
        try {
            gatewaySolution.create(createRequest("linux"));
            fail("stale placement must be rejected");
        } catch (TeamCommandException expected) {
            assertEquals("HOME_INSTANCE_REQUIRED", expected.getCode());
        }
    }

    @Test
    public void unreachableTransportDoesNotClearExistingSnapshot() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-linux"), anyString())).thenReturn(null);

        try {
            gatewaySolution.list("owner");
            fail("unreachable transport must fail the live refresh");
        } catch (TeamCommandException expected) {
            assertEquals("INTERNAL_ERROR", expected.getCode());
        }

        assertEquals("TRANSPORT_UNREACHABLE",
                gatewaySolution.listCandidates("owner").get(0).getStatus());
        org.mockito.Mockito.verify(teamSnapshotSolution, org.mockito.Mockito.never())
                .replaceAll(eq("owner"), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    public void createDerivesStableIdsFromRequestIdForSafeHttpRetry() {
        gatewaySolution.updateDiscovery(discoveryResult("instance-1", true,
                twoSources("owner", "instance-1")));
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
    public void createIncludesUserTeamRemarkAndSerializesMissingRemarkAsEmptyString() {
        gatewaySolution.updateDiscovery(discoveryResult("instance-1", true,
                sourcesWithRemark("owner", "instance-1", "负责代码实现")
                        .replace("]", ",{"
                                + "\"ownerChatterId\":\"owner\","
                                + "\"sourceGroupId\":\"group-instance-1-2\","
                                + "\"sourceRobotId\":\"source-instance-1-2\","
                                + "\"robotName\":\"Robot 2\","
                                + "\"displayName\":\"Robot 2\"}]")));
        when(teamCommandTransport.send(eq("acpTeamCreate"),
                eq("team-acp-instance-1"), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"team-result\","
                        + "\"state\":\"CREATING\",\"version\":1,\"members\":[]}"));

        TeamCreateRequest request = createRequest();
        request.getMembers().get(0).setRemark("用户填写的职责");
        gatewaySolution.create(request);

        org.mockito.ArgumentCaptor<String> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-instance-1"), payloadCaptor.capture());
        com.alibaba.fastjson.JSONArray members = JSON.parseObject(payloadCaptor.getValue())
                .getJSONArray("members");
        assertEquals("用户填写的职责", members.getJSONObject(0).getString("remark"));
        assertEquals("", members.getJSONObject(1).getString("remark"));
        assertEquals(true, members.getJSONObject(0).containsKey("remark"));
        assertEquals(true, members.getJSONObject(1).containsKey("remark"));
    }

    @Test
    public void captainCreateCarriesStableMemberIdsAndExplicitCaptain() {
        gatewaySolution.updateDiscovery(discoveryResult("instance-1", true,
                twoSources("owner", "instance-1")));
        when(teamCommandTransport.send(eq("acpTeamCreate"),
                eq("team-acp-instance-1"), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"team-result\","
                        + "\"mode\":\"CAPTAIN\",\"captainTeamMemberId\":\"member-2\","
                        + "\"state\":\"CREATING\",\"version\":1,\"members\":[]}"));
        TeamCreateRequest request = createRequest();
        request.setMode("CAPTAIN");
        request.setCaptainTeamMemberId("member-2");
        request.getMembers().get(0).setTeamMemberId("member-1");
        request.getMembers().get(1).setTeamMemberId("member-2");

        TeamDTO created = gatewaySolution.create(request);

        org.mockito.ArgumentCaptor<String> payloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-instance-1"), payloadCaptor.capture());
        JSONObject payload = JSON.parseObject(payloadCaptor.getValue());
        assertEquals("CAPTAIN", payload.getString("mode"));
        assertEquals("member-2", payload.getString("captainTeamMemberId"));
        assertEquals("member-1", payload.getJSONArray("members")
                .getJSONObject(0).getString("teamMemberId"));
        assertEquals("member-2", payload.getJSONArray("members")
                .getJSONObject(1).getString("teamMemberId"));
        assertEquals("CAPTAIN", created.getMode());
    }

    @Test
    public void captainCreateRequiresTwoMembersAndCaptainFromRosterBeforeRpc() {
        TeamCreateRequest missingCaptain = createRequest();
        missingCaptain.setMode("CAPTAIN");
        try {
            gatewaySolution.create(missingCaptain);
            fail("captain mode must require an explicit captain");
        } catch (TeamCommandException expected) {
            assertEquals("CAPTAIN_REQUIRED", expected.getCode());
        }

        TeamCreateRequest outsideRoster = createRequest();
        outsideRoster.setMode("CAPTAIN");
        outsideRoster.setCaptainTeamMemberId("not-selected");
        outsideRoster.getMembers().get(0).setTeamMemberId("member-1");
        outsideRoster.getMembers().get(1).setTeamMemberId("member-2");
        gatewaySolution.updateDiscovery(discoveryResult("instance-1", true,
                twoSources("owner", "instance-1")));
        try {
            gatewaySolution.create(outsideRoster);
            fail("captain must belong to the selected roster");
        } catch (TeamCommandException expected) {
            assertEquals("CAPTAIN_REQUIRED", expected.getCode());
        }
        org.mockito.Mockito.verify(teamCommandTransport, org.mockito.Mockito.never())
                .send(eq("acpTeamCreate"), anyString(), anyString());
    }

    @Test
    public void normalCreateRejectsCaptainAndUnknownModeBeforeRpc() {
        TeamCreateRequest normalWithCaptain = createRequest();
        normalWithCaptain.setCaptainTeamMemberId("member-1");
        try {
            gatewaySolution.create(normalWithCaptain);
            fail("normal mode must reject captainTeamMemberId");
        } catch (TeamCommandException expected) {
            assertEquals("VALIDATION_ERROR", expected.getCode());
        }

        TeamCreateRequest unknownMode = createRequest();
        unknownMode.setMode("mesh");
        try {
            gatewaySolution.create(unknownMode);
            fail("unknown team mode must fail closed");
        } catch (TeamCommandException expected) {
            assertEquals("VALIDATION_ERROR", expected.getCode());
        }

        org.mockito.Mockito.verify(teamCommandTransport, org.mockito.Mockito.never())
                .send(eq("acpTeamCreate"), anyString(), anyString());
    }

    @Test
    public void createRoutesToSelectedInstanceTransport() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        when(teamCommandTransport.send(eq("acpTeamCreate"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"team-result\",\"state\":\"CREATING\","
                        + "\"version\":1,\"members\":[]}"));

        gatewaySolution.selectHome("owner", "windows");

        gatewaySolution.create(createRequest("windows"));

        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-windows"), anyString());
    }

    @Test
    public void mixedCreatePersistsPlacementBeforeFirstParticipantRpc() {
        backMixedStoreWithMemory();
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");
        when(teamCommandTransport.send(eq("acpTeamCreate"), anyString(), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"ignored-local-fragment\","
                        + "\"state\":\"CREATING\",\"members\":[]}"));

        TeamDTO team = gatewaySolution.create(mixedCreateRequest());

        assertEquals("CREATING", team.getStatus());
        assertEquals(2, team.getMembers().size());
        org.mockito.InOrder order = inOrder(mixedTeamStore, teamCommandTransport);
        order.verify(mixedTeamStore).save(org.mockito.ArgumentMatchers.argThat(record ->
                "CREATING".equals(record.getState()) && record.getParticipants().size() == 2));
        order.verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-linux"), anyString());
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-windows"), anyString());
    }

    @Test
    public void mixedCreateDoesNotHoldOwnerLockWhileParticipantCallbacksReenter()
            throws Exception {
        Map<String, Consumer<Map<String, String>>> callbacks = captureCallbacks();
        AtomicReference<MixedTeamRecord> stored = backMixedStoreWithMemory();
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");
        when(teamCommandTransport.send(eq("acpTeamCreate"), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String transportGroup = (String) invocation.getArguments()[1];
                    Map<String, String> event = event(transportGroup,
                            stored.get().getTeamId(), "TEAM_CREATE_ACCEPTED", "{}");
                    invokeCallbackAndWait(callbacks.get(transportGroup), event);
                    return acceptedResult("{\"teamId\":\"fragment\","
                            + "\"state\":\"CREATING\",\"members\":[]}");
                });

        TeamDTO created = gatewaySolution.create(mixedCreateRequest());

        assertEquals("CREATING", created.getStatus());
    }

    @Test
    public void mixedCreateSendsIdenticalRosterRemarksAndLocalMemberRemarks() {
        backMixedStoreWithMemory();
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                sourcesWithRemark("owner", "linux", "本机隐藏 fallback")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");
        when(teamCommandTransport.send(eq("acpTeamCreate"), anyString(), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"ignored-local-fragment\","
                        + "\"state\":\"CREATING\",\"members\":[]}"));

        TeamCreateRequest request = mixedCreateRequest();
        request.getMembers().get(0).setRemark("");
        request.getMembers().get(1).setRemark("用户填写的远程职责");
        gatewaySolution.create(request);

        org.mockito.ArgumentCaptor<String> homePayloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> remotePayloadCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-linux"), homePayloadCaptor.capture());
        verify(teamCommandTransport).send(eq("acpTeamCreate"),
                eq("team-acp-windows"), remotePayloadCaptor.capture());
        com.alibaba.fastjson.JSONObject homePayload =
                JSON.parseObject(homePayloadCaptor.getValue());
        com.alibaba.fastjson.JSONObject remotePayload =
                JSON.parseObject(remotePayloadCaptor.getValue());
        assertEquals(homePayload.getJSONArray("roster").toJSONString(),
                remotePayload.getJSONArray("roster").toJSONString());
        assertEquals("本机隐藏 fallback", homePayload.getJSONArray("roster")
                .getJSONObject(0).getString("remark"));
        assertEquals("用户填写的远程职责", homePayload.getJSONArray("roster")
                .getJSONObject(1).getString("remark"));
        assertEquals("", homePayload.getJSONArray("members")
                .getJSONObject(0).getString("remark"));
        assertEquals("用户填写的远程职责", remotePayload.getJSONArray("members")
                .getJSONObject(0).getString("remark"));
        assertEquals(true, homePayload.getJSONArray("roster")
                .getJSONObject(0).containsKey("remark"));
        assertEquals(true, homePayload.getJSONArray("roster")
                .getJSONObject(1).containsKey("remark"));
    }

    @Test
    public void mixedCaptainCreatePersistsAndProjectsSameCaptainToEveryFragment() {
        AtomicReference<MixedTeamRecord> stored = backMixedStoreWithMemory();
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");
        when(teamCommandTransport.send(eq("acpTeamCreate"), anyString(), anyString()))
                .thenReturn(acceptedResult("{\"teamId\":\"fragment\","
                        + "\"state\":\"CREATING\",\"members\":[]}"));
        TeamCreateRequest request = mixedCreateRequest();
        request.setMode("CAPTAIN");
        request.setCaptainTeamMemberId("captain-remote");
        request.getMembers().get(0).setTeamMemberId("member-home");
        request.getMembers().get(1).setTeamMemberId("captain-remote");

        TeamDTO created = gatewaySolution.create(request);

        org.mockito.ArgumentCaptor<String> payloads =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport, times(2)).send(
                eq("acpTeamCreate"), anyString(), payloads.capture());
        for (String value : payloads.getAllValues()) {
            JSONObject payload = JSON.parseObject(value);
            assertEquals("CAPTAIN", payload.getString("mode"));
            assertEquals("captain-remote", payload.getString("captainTeamMemberId"));
        }
        assertEquals("CAPTAIN", stored.get().getMode());
        assertEquals("captain-remote", stored.get().getCaptainTeamMemberId());
        assertEquals("CAPTAIN", created.getMode());
        assertEquals("captain-remote", created.getCaptainTeamMemberId());
    }

    @Test
    public void remoteOnlyCreateIsRejectedBeforeRpc() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");

        try {
            gatewaySolution.create(createRequest("windows"));
            fail("remote-only Team must be rejected");
        } catch (TeamCommandException expected) {
            assertEquals("REMOTE_ONLY_TEAM", expected.getCode());
        }
        org.mockito.Mockito.verify(teamCommandTransport, org.mockito.Mockito.never())
                .send(eq("acpTeamCreate"), anyString(), anyString());
    }

    @Test
    public void mixedMemberCommandRoutesToRecordedParticipant() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        when(teamCommandTransport.send(eq("acpTeamSend"),
                eq("team-acp-windows"), anyString(), eq(8_000L)))
                .thenReturn(acceptedResult("{}"));

        gatewaySolution.send("owner", "mixed-team", "member-remote",
                "client-remote", "hello", Collections.emptyList());

        verify(teamCommandTransport).send(eq("acpTeamSend"),
                eq("team-acp-windows"), anyString(), eq(8_000L));
        verify(teamCommandTransport, org.mockito.Mockito.never()).send(eq("acpTeamSend"),
                eq("team-acp-linux"), anyString());
    }

    @Test
    public void captainDirectConversationAllowsOrdinaryMemberThroughProductEntry() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        record.setMode("CAPTAIN");
        record.setCaptainTeamMemberId("member-home");
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        org.mockito.ArgumentCaptor<String> payload =
                org.mockito.ArgumentCaptor.forClass(String.class);
        when(teamCommandTransport.send(eq("acpTeamSend"),
                eq("team-acp-windows"), payload.capture(), eq(8_000L)))
                .thenReturn(acceptedResult("{}"));
        Map<String, String> attachment = new HashMap<>();
        attachment.put("brief.txt", "YnJpZWY=");

        gatewaySolution.send("owner", "mixed-team", "member-remote",
                "client-remote", "direct product message",
                Collections.singletonList(attachment));

        verify(teamCommandTransport).send(eq("acpTeamSend"),
                eq("team-acp-windows"), anyString(), eq(8_000L));
        JSONObject sent = JSON.parseObject(payload.getValue());
        assertEquals("member-remote", sent.getString("teamMemberId"));
        assertEquals("YnJpZWY=", sent.getJSONArray("files").getJSONObject(0)
                .getString("brief.txt"));
    }

    @Test
    public void sendSubmissionTimeoutIsExplicitAndDoesNotPoisonTransport() {
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        when(teamCommandTransport.send(eq("acpTeamSend"),
                eq("team-acp-windows"), anyString(), eq(8_000L)))
                .thenThrow(new TeamCommandTransport.CommandTimeoutException(
                        "timed out", new RuntimeException("provider time out")))
                .thenReturn(acceptedResult("{}"));

        try {
            gatewaySolution.send("owner", "mixed-team", "member-remote",
                    "client-remote", "first", Collections.emptyList());
            fail("submission timeout must be surfaced");
        } catch (TeamCommandException expected) {
            assertEquals("SUBMISSION_TIMEOUT", expected.getCode());
            assertTrue(expected.getMessage().contains("requestId="));
        }

        gatewaySolution.send("owner", "mixed-team", "member-remote",
                "client-remote", "retry", Collections.emptyList());
        verify(teamCommandTransport, times(2)).send(eq("acpTeamSend"),
                eq("team-acp-windows"), anyString(), eq(8_000L));
    }

    @Test
    public void mixedMemberCommandReconcilesSessionIdIntoPersistedProjection() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        AtomicReference<MixedTeamRecord> stored =
                backMixedStoreWithMemory(mixedRecord());
        when(teamCommandTransport.send(eq("acpTeamGetSessionHistory"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"sessionId\":\"session-remote\","
                        + "\"messages\":[]}"));

        JSONObject history = gatewaySolution.getSessionHistory(
                "owner", "mixed-team", "member-remote", "client-remote");

        assertEquals("session-remote", history.getString("sessionId"));
        assertEquals("session-remote", stored.get().getMembers().get(1).getSessionId());
        assertEquals("session-remote", gatewaySolution.get("owner", "mixed-team")
                .getMembers().get(1).getSessionId());
    }

    @Test
    public void mixedReadyEventPersistsAndProjectsParticipantSessionId() {
        Map<String, Consumer<Map<String, String>>> callbacks = captureCallbacks();
        AtomicReference<MixedTeamRecord> stored =
                backMixedStoreWithMemory(mixedRecord());
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        Map<String, String> ready = event("team-acp-windows", "mixed-team",
                "TEAM_READY", "{\"team\":{\"members\":[{"
                        + "\"teamMemberId\":\"member-remote\","
                        + "\"state\":\"READY\","
                        + "\"sessionId\":\"session-ready\"}]}}");

        callbacks.get("team-acp-windows").accept(ready);

        assertEquals("session-ready", stored.get().getMembers().get(1).getSessionId());
        assertEquals("session-ready", gatewaySolution.get("owner", "mixed-team")
                .getMembers().get(1).getSessionId());
    }

    @Test
    public void mixedSessionChangedEventUpdatesPersistedSessionId() {
        Map<String, Consumer<Map<String, String>>> callbacks = captureCallbacks();
        AtomicReference<MixedTeamRecord> stored =
                backMixedStoreWithMemory(mixedRecord());
        stored.get().getMembers().get(1).setSessionId("session-old");
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        Map<String, String> changed = event("team-acp-windows", "mixed-team",
                "MEMBER_SESSION_CHANGED", "{\"oldSessionId\":\"session-old\","
                        + "\"newSessionId\":\"session-new\",\"reason\":\"MANUAL\"}");
        changed.put("teamMemberId", "member-remote");

        callbacks.get("team-acp-windows").accept(changed);

        assertEquals("session-new", stored.get().getMembers().get(1).getSessionId());
        assertEquals("session-new", gatewaySolution.get("owner", "mixed-team")
                .getMembers().get(1).getSessionId());
    }

    @Test
    public void recoveredMixedTeamReturnsToReadyAfterAllParticipantsReconnect() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        gatewaySolution.selectHome("owner", "linux");
        MixedTeamRecord record = mixedRecord();
        record.setState("RECOVERING");
        when(mixedTeamStore.listByOwner("owner"))
                .thenReturn(Collections.singletonList(record));
        when(teamCommandTransport.send(eq("acpTeamList"),
                eq("team-acp-linux"), anyString()))
                .thenReturn(acceptedResult("{\"teams\":[],\"snapshotVersion\":1}"));

        List<TeamDTO> teams = gatewaySolution.list("owner");

        assertEquals(1, teams.size());
        assertEquals("READY", teams.get(0).getStatus());
        verify(mixedTeamStore).save(org.mockito.ArgumentMatchers.argThat(saved ->
                "mixed-team".equals(saved.getTeamId())
                        && "READY".equals(saved.getState())));
    }

    @Test
    public void starweaveMixedListUsesPersistedCoordinatorStateWithoutHomeRpc() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        record.getMembers().get(1).setSessionId("session-remote-live");
        when(mixedTeamStore.listByOwner("owner"))
                .thenReturn(Collections.singletonList(record));

        List<TeamDTO> teams = gatewaySolution.listMixedForStarweave("owner");

        assertEquals(1, teams.size());
        assertEquals("mixed-team", teams.get(0).getTeamId());
        assertEquals("READY", teams.get(0).getStatus());
        assertEquals("session-remote-live",
                teams.get(0).getMembers().get(1).getSessionId());
        org.mockito.Mockito.verify(teamCommandTransport, org.mockito.Mockito.never())
                .send(eq("acpTeamList"), anyString(), anyString());
    }

    @Test
    public void mixedDeleteBuildsBarrierAcrossEveryParticipant() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        when(teamCommandTransport.send(eq("acpTeamDelete"), anyString(), anyString()))
                .thenReturn(acceptedResult("{}"));

        TeamDTO deleted = gatewaySolution.delete(
                "owner", "mixed-team", "delete-request", 1L);

        assertEquals("DELETING", deleted.getStatus());
        verify(teamCommandTransport).send(eq("acpTeamDelete"),
                eq("team-acp-linux"), anyString());
        verify(teamCommandTransport).send(eq("acpTeamDelete"),
                eq("team-acp-windows"), anyString());
        verify(mixedTeamStore, org.mockito.Mockito.never()).remove("mixed-team");
    }

    @Test
    public void mixedDeleteDoesNotHoldOwnerLockWhileParticipantCallbacksReenter()
            throws Exception {
        Map<String, Consumer<Map<String, String>>> callbacks = captureCallbacks();
        AtomicReference<MixedTeamRecord> stored = backMixedStoreWithMemory(mixedRecord());
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        when(teamCommandTransport.send(eq("acpTeamDelete"), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String transportGroup = (String) invocation.getArguments()[1];
                    invokeCallbackAndWait(callbacks.get(transportGroup), event(
                            transportGroup, "mixed-team", "TEAM_DELETE_ACCEPTED", "{}"));
                    return acceptedResult("{}");
                });

        TeamDTO deleting = gatewaySolution.delete(
                "owner", "mixed-team", "delete-reentrant", 1L);

        assertEquals("DELETING", deleting.getStatus());
        assertNotNull(stored.get());
    }

    @Test
    public void mixedDeleteDoesNotRegressTerminalParticipantCallbacks()
            throws Exception {
        Map<String, Consumer<Map<String, String>>> callbacks = captureCallbacks();
        backMixedStoreWithMemory(mixedRecord());
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                twoSources("owner", "windows")));
        when(teamCommandTransport.send(eq("acpTeamDelete"), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String transportGroup = (String) invocation.getArguments()[1];
                    invokeCallbackAndWait(callbacks.get(transportGroup), event(
                            transportGroup, "mixed-team", "TEAM_DELETED", "{}"));
                    return acceptedResult("{}");
                });

        TeamDTO deleted = gatewaySolution.delete(
                "owner", "mixed-team", "delete-terminal-reentrant", 1L);

        assertEquals("DELETED", deleted.getStatus());
        verify(mixedTeamStore, org.mockito.Mockito.atLeastOnce()).remove("mixed-team");
        verify(teamSnapshotSolution, org.mockito.Mockito.atLeastOnce()).delete("mixed-team");
        verify(teamRobotProjectionSolution, org.mockito.Mockito.atLeastOnce())
                .delete("mixed-team");
    }

    @Test
    public void cleanupStaleTeamsOnlyRemovesCreatingAndDeletingRecords() {
        MixedTeamRecord creating = mixedRecord();
        creating.setTeamId("team-creating");
        creating.setState("CREATING");
        MixedTeamRecord deleting = mixedRecord();
        deleting.setTeamId("team-deleting");
        deleting.setState("DELETING");
        MixedTeamRecord ready = mixedRecord();
        ready.setTeamId("team-ready");
        when(mixedTeamStore.listByOwner("owner"))
                .thenReturn(java.util.Arrays.asList(creating, deleting, ready));

        List<String> cleaned = gatewaySolution.cleanupStaleTeams("owner");

        assertEquals(java.util.Arrays.asList("team-creating", "team-deleting"), cleaned);
        verify(mixedTeamStore).remove("team-creating");
        verify(mixedTeamStore).remove("team-deleting");
        verify(mixedTeamStore, org.mockito.Mockito.never()).remove("team-ready");
        verify(teamSnapshotSolution).delete("team-creating");
        verify(teamSnapshotSolution).delete("team-deleting");
        verify(teamRobotProjectionSolution).delete("team-creating");
        verify(teamRobotProjectionSolution).delete("team-deleting");
    }

    @Test
    public void mixedTalkToRouteEventDeliversToTargetParticipant() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        when(teamCommandTransport.send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"delivery\":\"DELIVERED\"}"));
        org.mockito.ArgumentCaptor<Consumer<Map<String, String>>> callback =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(teamCommandTransport).registerEventCallback(
                eq("acpTeamEvent"), eq("team-acp-linux"), callback.capture());
        Map<String, String> event = new HashMap<>();
        event.put("transportGroup", "team-acp-linux");
        event.put("teamId", "mixed-team");
        event.put("type", "TALK_TO_ROUTE_REQUEST");
        long createdAt = System.currentTimeMillis();
        event.put("data", "{\"messageId\":\"message-1\","
                + "\"senderTeamMemberId\":\"member-home\","
                + "\"targetTeamMemberId\":\"member-remote\","
                + "\"content\":\"hello\",\"depth\":1,"
                + "\"createdAt\":" + createdAt + ",\"expiresAt\":"
                + (createdAt + 30L * 60L * 1000L) + "}");

        callback.getValue().accept(event);

        org.mockito.ArgumentCaptor<String> payload =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(teamCommandTransport).send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-windows"), payload.capture());
        com.alibaba.fastjson.JSONObject delivered = JSON.parseObject(payload.getValue());
        assertEquals("1", delivered.getString("schemaVersion"));
        assertEquals("member-home", delivered.getString("senderTeamMemberId"));
        assertEquals("member-remote", delivered.getString("targetTeamMemberId"));
        assertEquals(createdAt, delivered.getLongValue("createdAt"));
    }

    @Test
    public void mixedTalkToRejectsInvalidTtlBeforeTargetRpc() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        when(mixedTeamStore.find("mixed-team")).thenReturn(mixedRecord());
        org.mockito.ArgumentCaptor<Consumer<Map<String, String>>> callback =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(teamCommandTransport).registerEventCallback(
                eq("acpTeamEvent"), eq("team-acp-linux"), callback.capture());
        long now = System.currentTimeMillis();
        Map<String, String> event = new HashMap<>();
        event.put("transportGroup", "team-acp-linux");
        event.put("teamId", "mixed-team");
        event.put("type", "TALK_TO_ROUTE_REQUEST");
        event.put("data", "{\"messageId\":\"message-invalid\","
                + "\"senderTeamMemberId\":\"member-home\","
                + "\"targetTeamMemberId\":\"member-remote\","
                + "\"content\":\"hello\",\"depth\":1,"
                + "\"createdAt\":" + now + ",\"expiresAt\":"
                + (now + 30L * 60L * 1000L + 1L) + "}");

        callback.getValue().accept(event);

        verify(teamCommandTransport, org.mockito.Mockito.never()).send(
                eq("acpTeamTalkToDeliver"), anyString(), anyString());
    }

    @Test
    public void mixedCaptainTalkToAllowsCaptainToOrdinaryMember() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        record.setMode("CAPTAIN");
        record.setCaptainTeamMemberId("member-home");
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        when(teamCommandTransport.send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-windows"), anyString()))
                .thenReturn(acceptedResult("{\"delivery\":\"DELIVERED\"}"));
        org.mockito.ArgumentCaptor<Consumer<Map<String, String>>> callback =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(teamCommandTransport).registerEventCallback(
                eq("acpTeamEvent"), eq("team-acp-linux"), callback.capture());
        long now = System.currentTimeMillis();
        Map<String, String> route = event("team-acp-linux", "mixed-team",
                "TALK_TO_ROUTE_REQUEST", "{\"messageId\":\"captain-edge\","
                        + "\"senderTeamMemberId\":\"member-home\","
                        + "\"targetTeamMemberId\":\"member-remote\","
                        + "\"content\":\"delegate\",\"depth\":1,"
                        + "\"createdAt\":" + now + ",\"expiresAt\":"
                        + (now + 60_000L) + "}");

        callback.getValue().accept(route);

        verify(teamCommandTransport).send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-windows"), anyString());
    }

    @Test
    public void mixedCaptainTalkToAllowsOrdinaryMemberToCaptain() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        record.setMode("CAPTAIN");
        record.setCaptainTeamMemberId("member-home");
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        when(teamCommandTransport.send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-linux"), anyString()))
                .thenReturn(acceptedResult("{\"delivery\":\"DELIVERED\"}"));
        org.mockito.ArgumentCaptor<Consumer<Map<String, String>>> callback =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(teamCommandTransport).registerEventCallback(
                eq("acpTeamEvent"), eq("team-acp-windows"), callback.capture());
        long now = System.currentTimeMillis();
        Map<String, String> route = event("team-acp-windows", "mixed-team",
                "TALK_TO_ROUTE_REQUEST", "{\"messageId\":\"ordinary-to-captain\","
                        + "\"senderTeamMemberId\":\"member-remote\","
                        + "\"targetTeamMemberId\":\"member-home\","
                        + "\"content\":\"report\",\"depth\":1,"
                        + "\"createdAt\":" + now + ",\"expiresAt\":"
                        + (now + 60_000L) + "}");

        callback.getValue().accept(route);

        verify(teamCommandTransport).send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-linux"), anyString());
    }

    @Test
    public void mixedCaptainTalkToRejectsOrdinaryMemberEdgeBeforeTargetRpc() {
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        MixedTeamRecord record = mixedRecord();
        record.setMode("CAPTAIN");
        record.setCaptainTeamMemberId("member-home-2");
        record.getParticipants().get(0).setMemberIds(
                java.util.Arrays.asList("member-home", "member-home-2"));
        MixedTeamRecord.Member captain = new MixedTeamRecord.Member();
        captain.setTeamMemberId("member-home-2");
        captain.setAcpClientId("client-home-2");
        captain.setParticipantInstanceId("linux");
        captain.setTransportGroup("team-acp-linux");
        captain.setState("READY");
        List<MixedTeamRecord.Member> captainRoster = new java.util.ArrayList<>(
                record.getMembers());
        captainRoster.add(captain);
        record.setMembers(captainRoster);
        when(mixedTeamStore.find("mixed-team")).thenReturn(record);
        org.mockito.ArgumentCaptor<Consumer<Map<String, String>>> callback =
                org.mockito.ArgumentCaptor.forClass(Consumer.class);
        verify(teamCommandTransport).registerEventCallback(
                eq("acpTeamEvent"), eq("team-acp-linux"), callback.capture());
        long now = System.currentTimeMillis();
        Map<String, String> route = event("team-acp-linux", "mixed-team",
                "TALK_TO_ROUTE_REQUEST", "{\"messageId\":\"forbidden-edge\","
                        + "\"senderTeamMemberId\":\"member-home\","
                        + "\"targetTeamMemberId\":\"member-remote\","
                        + "\"content\":\"bypass\",\"depth\":1,"
                        + "\"createdAt\":" + now + ",\"expiresAt\":"
                        + (now + 60_000L) + "}");

        callback.getValue().accept(route);

        verify(teamCommandTransport, org.mockito.Mockito.never()).send(
                eq("acpTeamTalkToDeliver"), anyString(), anyString());
    }

    @Test
    public void mixedTalkToDoesNotHoldOwnerLockWhileDeliveryCallbacksReenter()
            throws Exception {
        Map<String, Consumer<Map<String, String>>> callbacks = captureCallbacks();
        backMixedStoreWithMemory(mixedRecord());
        gatewaySolution.updateDiscovery(discoveryResult("linux", true,
                twoSources("owner", "linux")));
        gatewaySolution.updateDiscovery(discoveryResult("windows", true,
                "[]", remoteSources("owner", "windows")));
        when(teamCommandTransport.send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-windows"), anyString())).thenAnswer(invocation -> {
                    invokeCallbackAndWait(callbacks.get("team-acp-windows"), event(
                            "team-acp-windows", "mixed-team", "MEMBER_STATE_CHANGED", "{}"));
                    return acceptedResult("{\"delivery\":\"DELIVERED\"}");
                });
        long now = System.currentTimeMillis();
        Map<String, String> route = event("team-acp-linux", "mixed-team",
                "TALK_TO_ROUTE_REQUEST", "{\"messageId\":\"message-reentrant\","
                        + "\"senderTeamMemberId\":\"member-home\","
                        + "\"targetTeamMemberId\":\"member-remote\","
                        + "\"content\":\"hello\",\"depth\":1,"
                        + "\"createdAt\":" + now + ",\"expiresAt\":"
                        + (now + 60_000L) + "}");

        callbacks.get("team-acp-linux").accept(route);

        verify(teamCommandTransport).send(eq("acpTeamTalkToDeliver"),
                eq("team-acp-windows"), anyString());
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
                                                 String teamMemberSources) {
        return discoveryResult(instanceId, ready, teamMemberSources, "[]");
    }

    private Map<String, String> discoveryResult(String instanceId, boolean ready,
                                                 String teamMemberSources,
                                                 String remoteTeamMemberSources) {
        Map<String, String> result = new HashMap<>();
        result.put("teamSchemaVersion", "1");
        result.put("teamCmdProxyInstanceId", instanceId);
        result.put("teamTransportGroup", "team-acp-" + instanceId);
        result.put("visibleChatterIds", "[\"owner\"]");
        result.put("robots", "[]");
        String commands = ready
                ? "[\"acpTeamDescribe\",\"acpTeamCreate\",\"acpTeamList\",\"acpTeamGet\","
                    + "\"acpTeamDelete\",\"acpTeamSend\",\"acpTeamCancel\","
                    + "\"acpTeamNewSession\",\"acpTeamListSessions\","
                    + "\"acpTeamGetSessionHistory\","
                    + "\"acpTeamRestoreSession\",\"acpTeamGetStatus\","
                    + "\"acpTeamGetContextUsage\",\"acpTeamMemoryDream\","
                    + "\"acpTeamTalkToDeliver\"]"
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
                + ",\"capabilities\":{\"mixedTeamFragment\":true,"
                + "\"mixedTeamTalkToDeliver\":true}"
                + (teamMemberSources == null ? ""
                    : ",\"teamMemberSources\":" + teamMemberSources)
                + (remoteTeamMemberSources == null ? ""
                    : ",\"remoteTeamMemberSources\":" + remoteTeamMemberSources)
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

    @SuppressWarnings("unchecked")
    private Map<String, Consumer<Map<String, String>>> captureCallbacks() {
        Map<String, Consumer<Map<String, String>>> callbacks = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            callbacks.put((String) invocation.getArguments()[1],
                    (Consumer<Map<String, String>>) invocation.getArguments()[2]);
            return null;
        }).when(teamCommandTransport).registerEventCallback(
                eq("acpTeamEvent"), anyString(), any());
        return callbacks;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Consumer<Map<String, String>>> captureAllCallbacks() {
        Map<String, Consumer<Map<String, String>>> callbacks = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            callbacks.put(invocation.getArguments()[0] + "@" + invocation.getArguments()[1],
                    (Consumer<Map<String, String>>) invocation.getArguments()[2]);
            return null;
        }).when(teamCommandTransport).registerEventCallback(
                anyString(), anyString(), any());
        doAnswer(invocation -> {
            callbacks.put(invocation.getArguments()[0] + "@" + invocation.getArguments()[1],
                    (Consumer<Map<String, String>>) invocation.getArguments()[3]);
            return null;
        }).when(teamCommandTransport).registerEventCallback(
                anyString(), anyString(), any(), any());
        return callbacks;
    }

    private AtomicReference<MixedTeamRecord> backMixedStoreWithMemory() {
        return backMixedStoreWithMemory(null);
    }

    private AtomicReference<MixedTeamRecord> backMixedStoreWithMemory(
            MixedTeamRecord initial) {
        AtomicReference<MixedTeamRecord> stored = new AtomicReference<>(initial);
        doAnswer(invocation -> {
            stored.set((MixedTeamRecord) invocation.getArguments()[0]);
            return null;
        }).when(mixedTeamStore).save(any(MixedTeamRecord.class));
        when(mixedTeamStore.find(anyString())).thenAnswer(invocation -> {
            MixedTeamRecord current = stored.get();
            return current != null && invocation.getArguments()[0].equals(current.getTeamId())
                    ? current : null;
        });
        return stored;
    }

    private Map<String, String> event(String transportGroup, String teamId,
                                      String type, String data) {
        Map<String, String> event = new HashMap<>();
        event.put("transportGroup", transportGroup);
        event.put("teamId", teamId);
        event.put("type", type);
        event.put("data", data);
        return event;
    }

    private void invokeCallbackAndWait(Consumer<Map<String, String>> callback,
                                       Map<String, String> event) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread callbackThread = new Thread(() -> {
            try {
                callback.accept(event);
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                completed.countDown();
            }
        }, "synchronous-cmdproxy-callback-test");
        callbackThread.setDaemon(true);
        callbackThread.start();
        assertTrue("synchronous callback must not wait for an owner lock",
                completed.await(1, TimeUnit.SECONDS));
        if (failure.get() != null) {
            throw new AssertionError("callback failed", failure.get());
        }
    }

    private TeamCreateRequest createRequest() {
        return createRequest("instance-1");
    }

    private TeamCreateRequest createRequest(String instanceId) {
        TeamCreateRequest request = new TeamCreateRequest();
        request.setChatterId("owner");
        request.setToken("token");
        request.setRequestId("request-1");
        request.setName("Fast");
        TeamCreateMemberRequest first = new TeamCreateMemberRequest();
        first.setCmdProxyInstanceId(instanceId);
        first.setTransportGroup("team-acp-" + instanceId);
        first.setSourceRobotId("source-" + instanceId + "-1");
        first.setSourceGroupId("group-" + instanceId + "-1");
        TeamCreateMemberRequest second = new TeamCreateMemberRequest();
        second.setCmdProxyInstanceId(instanceId);
        second.setTransportGroup("team-acp-" + instanceId);
        second.setSourceRobotId("source-" + instanceId + "-2");
        second.setSourceGroupId("group-" + instanceId + "-2");
        request.setMembers(java.util.Arrays.asList(first, second));
        return request;
    }

    private TeamCreateRequest mixedCreateRequest() {
        TeamCreateRequest request = new TeamCreateRequest();
        request.setChatterId("owner");
        request.setToken("token");
        request.setRequestId("mixed-request-1");
        request.setName("Mixed");
        TeamCreateMemberRequest home = new TeamCreateMemberRequest();
        home.setCmdProxyInstanceId("linux");
        home.setTransportGroup("team-acp-linux");
        home.setSourceRobotId("source-linux-1");
        home.setSourceGroupId("group-linux-1");
        TeamCreateMemberRequest remote = new TeamCreateMemberRequest();
        remote.setCmdProxyInstanceId("windows");
        remote.setTransportGroup("team-acp-windows");
        remote.setSourceRobotId("source-windows-1");
        remote.setSourceGroupId("group-windows-1");
        request.setMembers(java.util.Arrays.asList(home, remote));
        return request;
    }

    private MixedTeamRecord mixedRecord() {
        MixedTeamRecord record = new MixedTeamRecord();
        record.setTeamId("mixed-team");
        record.setOwnerChatterId("owner");
        record.setName("Mixed");
        record.setState("READY");
        record.setHomeInstanceId("linux");
        MixedTeamRecord.Participant home = new MixedTeamRecord.Participant();
        home.setInstanceId("linux");
        home.setTransportGroup("team-acp-linux");
        home.setState("READY");
        home.setMemberIds(Collections.singletonList("member-home"));
        MixedTeamRecord.Participant remote = new MixedTeamRecord.Participant();
        remote.setInstanceId("windows");
        remote.setTransportGroup("team-acp-windows");
        remote.setState("READY");
        remote.setMemberIds(Collections.singletonList("member-remote"));
        record.setParticipants(java.util.Arrays.asList(home, remote));
        MixedTeamRecord.Member homeMember = new MixedTeamRecord.Member();
        homeMember.setTeamMemberId("member-home");
        homeMember.setAcpClientId("client-home");
        homeMember.setParticipantInstanceId("linux");
        homeMember.setTransportGroup("team-acp-linux");
        homeMember.setState("READY");
        MixedTeamRecord.Member remoteMember = new MixedTeamRecord.Member();
        remoteMember.setTeamMemberId("member-remote");
        remoteMember.setAcpClientId("client-remote");
        remoteMember.setParticipantInstanceId("windows");
        remoteMember.setTransportGroup("team-acp-windows");
        remoteMember.setState("READY");
        record.setMembers(java.util.Arrays.asList(homeMember, remoteMember));
        return record;
    }

    private String sources(String owner, String instanceId, boolean onlyTeamMember) {
        return "[{\"ownerChatterId\":\"" + owner + "\","
                + "\"sourceGroupId\":\"group-" + instanceId + "-1\","
                + "\"sourceRobotId\":\"source-" + instanceId + "-1\","
                + "\"robotName\":\"Robot " + instanceId + "\","
                + "\"displayName\":\"Robot " + instanceId + "\","
                + "\"onlyTeamMember\":" + onlyTeamMember + "}]";
    }

    private String remoteSources(String owner, String instanceId) {
        return "[{\"granteeOwnerChatterId\":\"" + owner + "\","
                + "\"participantInstanceId\":\"" + instanceId + "\","
                + "\"sourceGroupId\":\"group-" + instanceId + "-1\","
                + "\"sourceRobotId\":\"source-" + instanceId + "-1\","
                + "\"robotName\":\"Robot " + instanceId + "\","
                + "\"displayName\":\"Robot " + instanceId + "\","
                + "\"avatar\":\"\",\"remark\":\"remote\"}]";
    }

    private String sourcesWithRemark(String owner, String instanceId, String remark) {
        return "[{\"ownerChatterId\":\"" + owner + "\","
                + "\"sourceGroupId\":\"group-" + instanceId + "-1\","
                + "\"sourceRobotId\":\"source-" + instanceId + "-1\","
                + "\"robotName\":\"Robot " + instanceId + "\","
                + "\"displayName\":\"Robot " + instanceId + "\","
                + "\"remark\":\"" + remark + "\",\"onlyTeamMember\":false}]";
    }

    private String twoSources(String owner, String instanceId) {
        return sources(owner, instanceId, false).replace("]", ",{"
                + "\"ownerChatterId\":\"" + owner + "\","
                + "\"sourceGroupId\":\"group-" + instanceId + "-2\","
                + "\"sourceRobotId\":\"source-" + instanceId + "-2\","
                + "\"robotName\":\"Robot 2\",\"displayName\":\"Robot 2\","
                + "\"onlyTeamMember\":true}]");
    }
}
