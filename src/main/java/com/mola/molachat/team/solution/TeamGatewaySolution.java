package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.team.dto.TeamCreateMemberRequest;
import com.mola.molachat.team.dto.TeamCreateRequest;
import com.mola.molachat.team.dto.TeamDiscoveryDTO;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamMemberDTO;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.robot.solution.AcpRobotSyncSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * MolaChat到cmd-proxy Team transport的能力发现与门禁。
 */
@Service
@Slf4j
public class TeamGatewaySolution {

    private static final String SUPPORTED_SCHEMA_VERSION = "1";
    private static final String TEAM_ROBOT_GROUP = "team-acp";
    private static final String BINDING_KEY_PREFIX = "fast-team.instance-binding.";
    private static final long DISCOVERY_TTL_MILLIS = 120_000L;

    private final Map<String, DiscoveryRegistration> discoveries = new ConcurrentHashMap<>();
    private final Map<String, String> bindings = new ConcurrentHashMap<>();
    private final Map<String, String> teamInstanceBindings = new ConcurrentHashMap<>();
    private final Map<String, Object> ownerResolutionLocks = new ConcurrentHashMap<>();
    private final Set<String> registeredEventGroups = ConcurrentHashMap.newKeySet();

    @Resource
    private TeamEventSolution teamEventSolution;

    @Resource
    private TeamCommandTransport teamCommandTransport;

    @Resource
    private TeamRobotProjectionSolution teamRobotProjectionSolution;

    @Resource
    private TeamSnapshotSolution teamSnapshotSolution;

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    /**
     * 从现有acpSyncRobots回调更新Team transport discovery。
     * 字段缺失时保持未发现状态，兼容尚未升级的cmd-proxy。
     */
    public void updateDiscovery(Map<String, String> resultMap) {
        if (resultMap == null || StringUtils.isBlank(resultMap.get("teamDiscovery"))) {
            return;
        }
        try {
            TeamDiscoveryDTO discovery = JSON.parseObject(
                    resultMap.get("teamDiscovery"), TeamDiscoveryDTO.class);
            validate(discovery, resultMap);
            DiscoveryRegistration registration = new DiscoveryRegistration(
                    discovery,
                    parseVisibleChatterIds(resultMap.get("visibleChatterIds")),
                    parseRobots(resultMap.get("robots")),
                    System.currentTimeMillis());
            discoveries.put(discovery.getCmdProxyInstanceId(), registration);
            registerEventCallback(discovery);
            log.info("Fast Team transport discovered, instanceId={}, transportGroup={},"
                            + " visibleChatterIds={}, ready={}",
                    discovery.getCmdProxyInstanceId(), discovery.getTransportGroup(),
                    registration.visibleChatterIds,
                    discovery.isBusinessCommandsReady());
        } catch (IllegalArgumentException e) {
            log.warn("忽略不兼容的Fast Team transport discovery: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Fast Team transport discovery解析失败", e);
        }
    }

    public TeamDiscoveryDTO getDiscovery(String ownerChatterId) {
        return resolveRegistration(ownerChatterId, true, false).discovery;
    }

    public boolean isBusinessReady() {
        return discoveries.values().stream()
                .filter(this::isActive)
                .anyMatch(registration -> registration.discovery.isBusinessCommandsReady());
    }

    public TeamDTO create(TeamCreateRequest request) {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("requestId", request.getRequestId());
        payload.put("teamId", stableUuid("team:" + request.getRequestId()));
        payload.put("ownerChatterId", request.getChatterId());
        payload.put("name", request.getName());
        JSONArray members = new JSONArray();
        for (int index = 0; index < request.getMembers().size(); index++) {
            TeamCreateMemberRequest source = request.getMembers().get(index);
            JSONObject member = new JSONObject();
            member.put("teamMemberId", stableUuid("team-member:" + request.getRequestId()
                    + ":" + index + ":" + source.getSourceGroupId()));
            member.put("sourceRobotId", source.getSourceRobotId());
            member.put("sourceGroupId", source.getSourceGroupId());
            member.put("order", index);
            members.add(member);
        }
        payload.put("members", members);
        DiscoveryRegistration registration = resolveRegistration(
                request.getChatterId(), false, true);
        Map<String, String> result = invoke(registration, "acpTeamCreate", payload);
        TeamDTO team = JSON.parseObject(requiredData(result), TeamDTO.class);
        rememberTeamInstance(team, registration);
        teamSnapshotSolution.upsert(team);
        return team;
    }

    public List<TeamDTO> list(String ownerChatterId) {
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, true, true);
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("ownerChatterId", ownerChatterId);
        List<TeamDTO> teams = parseTeams(invoke(
                registration, "acpTeamList", payload));
        teams.forEach(team -> rememberTeamInstance(team, registration));
        teamSnapshotSolution.replaceAll(ownerChatterId, teams);
        if (!teams.isEmpty()) {
            teamRobotProjectionSolution.syncAll(ownerChatterId, teams);
        }
        return teams;
    }

    public TeamDTO get(String ownerChatterId, String teamId) {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("ownerChatterId", ownerChatterId);
        payload.put("teamId", teamId);
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, false, true);
        Map<String, String> result = invoke(registration, "acpTeamGet", payload);
        JSONObject data = JSON.parseObject(requiredData(result));
        if (data == null || data.getJSONObject("team") == null) {
            throw new TeamCommandException("INTERNAL_ERROR", "cmdproxy未返回Team数据");
        }
        TeamDTO team = data.getJSONObject("team").toJavaObject(TeamDTO.class);
        rememberTeamInstance(team, registration);
        teamSnapshotSolution.upsert(team);
        return team;
    }

    public TeamDTO delete(String ownerChatterId, String teamId, String requestId,
                          Long expectedVersion) {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("requestId", requestId);
        payload.put("ownerChatterId", ownerChatterId);
        payload.put("teamId", teamId);
        payload.put("expectedVersion", expectedVersion);
        JSONObject data = invokeData(ownerChatterId, "acpTeamDelete", payload);
        if (data == null || data.getJSONObject("team") == null) {
            throw new TeamCommandException("INTERNAL_ERROR", "cmdproxy未返回删除后的Team数据");
        }
        TeamDTO team = data.getJSONObject("team").toJavaObject(TeamDTO.class);
        teamSnapshotSolution.upsert(team);
        return team;
    }

    public List<TeamDTO> snapshot(String ownerChatterId) {
        return teamSnapshotSolution.list(ownerChatterId);
    }

    public JSONObject send(String ownerChatterId, String teamId, String teamMemberId,
                           String acpClientId, String message,
                           List<Map<String, String>> files) {
        JSONObject payload = memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId);
        payload.put("message", message);
        if (files != null && !files.isEmpty()) {
            payload.put("files", files);
        }
        return invokeData(ownerChatterId, "acpTeamSend", payload);
    }

    public JSONObject cancel(String ownerChatterId, String teamId, String teamMemberId,
                             String acpClientId) {
        return invokeData(ownerChatterId, "acpTeamCancel",
                memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId));
    }

    public JSONObject newSession(String ownerChatterId, String teamId, String teamMemberId,
                                 String acpClientId) {
        return invokeData(ownerChatterId, "acpTeamNewSession",
                memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId));
    }

    public JSONObject listSessions(String ownerChatterId, String teamId, String teamMemberId,
                                   String acpClientId, Integer limit) {
        JSONObject payload = memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId);
        if (limit != null) {
            payload.put("limit", limit);
        }
        return invokeData(ownerChatterId, "acpTeamListSessions", payload);
    }

    public JSONObject restoreSession(String ownerChatterId, String teamId, String teamMemberId,
                                     String acpClientId, String sessionId) {
        JSONObject payload = memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId);
        payload.put("sessionId", sessionId);
        return invokeData(ownerChatterId, "acpTeamRestoreSession", payload);
    }

    public JSONObject getStatus(String ownerChatterId, String teamId, String teamMemberId,
                                String acpClientId) {
        return invokeData(ownerChatterId, "acpTeamGetStatus",
                memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId));
    }

    public JSONObject getContextUsage(String ownerChatterId, String teamId, String teamMemberId,
                                      String acpClientId) {
        return invokeData(ownerChatterId, "acpTeamGetContextUsage",
                memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId));
    }

    public JSONObject memoryDream(String ownerChatterId, String teamId, String teamMemberId,
                                  String acpClientId) {
        return invokeData(ownerChatterId, "acpTeamMemoryDream",
                memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId));
    }

    public boolean supportsCommand(String ownerChatterId, String command) {
        TeamDiscoveryDTO discovery;
        try {
            discovery = getDiscovery(ownerChatterId);
        } catch (TeamCommandException e) {
            return false;
        }
        return discovery.isBusinessCommandsReady()
                && discovery.getCommands() != null
                && discovery.getCommands().contains(command);
    }

    public List<TeamMemberDTO> listCandidates(String ownerChatterId) {
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, true, true);
        List<TeamMemberDTO> candidates = new ArrayList<>();
        for (AcpRobotSyncSolution.AcpRobotParam robot : registration.robots) {
            if (robot == null || StringUtils.isBlank(robot.getName())) {
                continue;
            }
            TeamMemberDTO candidate = new TeamMemberDTO();
            candidate.setCmdProxyInstanceId(registration.discovery.getCmdProxyInstanceId());
            candidate.setSourceRobotId(buildRobotId(robot.getName()));
            candidate.setSourceGroupId(computeSourceGroupId(
                    ownerChatterId, candidate.getSourceRobotId()));
            candidate.setDisplayName(robot.getName());
            candidate.setAvatar(StringUtils.defaultIfBlank(robot.getAvatar(), "img/kiro.png"));
            candidate.setStatus("AVAILABLE");
            candidates.add(candidate);
        }
        return candidates;
    }

    private Map<String, String> invoke(String ownerChatterId, String command,
                                       JSONObject payload) {
        return invoke(resolveRegistration(ownerChatterId, false, true), command, payload);
    }

    private Map<String, String> invoke(DiscoveryRegistration registration, String command,
                                       JSONObject payload) {
        ensureCommandReady(registration, command);
        Map<String, String> result = teamCommandTransport.send(
                command, registration.discovery.getTransportGroup(), payload.toJSONString());
        if (result == null) {
            throw new TeamCommandException("INTERNAL_ERROR", "cmdproxy Team命令响应为空");
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(result.get("schemaVersion"))) {
            throw new TeamCommandException("VALIDATION_ERROR", "不支持的Team协议版本");
        }
        if (!Boolean.parseBoolean(result.get("accepted"))) {
            throw new TeamCommandException(result.get("code"), result.get("message"));
        }
        return result;
    }

    private String requiredData(Map<String, String> result) {
        if (StringUtils.isBlank(result.get("data"))) {
            throw new TeamCommandException("INTERNAL_ERROR", "cmdproxy Team命令缺少data");
        }
        return result.get("data");
    }

    private JSONObject invokeData(String ownerChatterId, String command, JSONObject payload) {
        return JSON.parseObject(requiredData(invoke(ownerChatterId, command, payload)));
    }

    private JSONObject memberPayload(String ownerChatterId, String teamId, String teamMemberId,
                                     String acpClientId) {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("ownerChatterId", ownerChatterId);
        payload.put("teamId", teamId);
        payload.put("teamMemberId", teamMemberId);
        if (StringUtils.isNotBlank(acpClientId)) {
            payload.put("acpClientId", acpClientId);
        }
        return payload;
    }

    private void ensureCommandReady(DiscoveryRegistration registration, String command) {
        TeamDiscoveryDTO discovery = registration.discovery;
        if (!discovery.isBusinessCommandsReady()
                || discovery.getCommands() == null
                || !discovery.getCommands().contains(command)) {
            throw new TeamCommandException("TEAM_NOT_READY", "Fast Team运行时尚未就绪");
        }
    }

    private DiscoveryRegistration resolveRegistration(String ownerChatterId,
                                                      boolean probeExistingTeams,
                                                      boolean requireReady) {
        if (StringUtils.isBlank(ownerChatterId)) {
            throw new TeamCommandException("VALIDATION_ERROR", "ownerChatterId不能为空");
        }
        synchronized (ownerResolutionLocks.computeIfAbsent(ownerChatterId, key -> new Object())) {
            return resolveRegistrationLocked(ownerChatterId, probeExistingTeams, requireReady);
        }
    }

    private DiscoveryRegistration resolveRegistrationLocked(String ownerChatterId,
                                                            boolean probeExistingTeams,
                                                            boolean requireReady) {
        List<DiscoveryRegistration> candidates = discoveries.values().stream()
                .filter(this::isActive)
                .filter(registration -> !requireReady
                        || registration.discovery.isBusinessCommandsReady())
                .filter(registration -> registration.visibleChatterIds.isEmpty()
                        || registration.visibleChatterIds.contains(ownerChatterId))
                .sorted(Comparator.comparing(
                        registration -> registration.discovery.getCmdProxyInstanceId()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) {
            throw new TeamCommandException("TEAM_NOT_READY", "未发现当前用户的Fast Team transport");
        }
        String boundInstanceId = loadBinding(ownerChatterId);
        DiscoveryRegistration bound = null;
        if (StringUtils.isNotBlank(boundInstanceId)) {
            for (DiscoveryRegistration candidate : candidates) {
                if (boundInstanceId.equals(candidate.discovery.getCmdProxyInstanceId())) {
                    bound = candidate;
                    break;
                }
            }
            if (bound == null) {
                log.warn("Fast Team已绑定实例未出现在当前discovery中,"
                                + " ownerChatterId={}, boundInstanceId={}",
                        ownerChatterId, boundInstanceId);
            }
            if (bound != null && (!probeExistingTeams || candidates.size() == 1)) {
                return bound;
            }
        }
        if (candidates.size() == 1) {
            if (candidates.get(0).discovery.isBusinessCommandsReady()) {
                bind(ownerChatterId, candidates.get(0));
            }
            return candidates.get(0);
        }
        if (probeExistingTeams) {
            List<DiscoveryRegistration> owners = candidates.stream()
                    .filter(candidate -> hasExistingTeams(candidate, ownerChatterId))
                    .collect(Collectors.toList());
            if (owners.size() == 1) {
                bind(ownerChatterId, owners.get(0));
                return owners.get(0);
            }
            if (owners.size() > 1) {
                throw new TeamCommandException("CMD_PROXY_INSTANCE_CONFLICT",
                        "多个cmdproxy实例同时保存当前用户的Team，请先解决实例冲突");
            }
            if (bound != null) {
                return bound;
            }
        }
        throw new TeamCommandException("CMD_PROXY_INSTANCE_CONFLICT",
                "当前用户同时连接了多个cmdproxy实例，请关闭多余实例后重试");
    }

    private boolean isActive(DiscoveryRegistration registration) {
        return System.currentTimeMillis() - registration.lastSeenAt <= DISCOVERY_TTL_MILLIS;
    }

    private boolean hasExistingTeams(DiscoveryRegistration registration,
                                     String ownerChatterId) {
        if (registration.discovery.getCommands() == null
                || !registration.discovery.getCommands().contains("acpTeamList")) {
            return false;
        }
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("ownerChatterId", ownerChatterId);
        try {
            return !parseTeams(invoke(registration, "acpTeamList", payload)).isEmpty();
        } catch (TeamCommandException e) {
            log.warn("Fast Team实例探测失败, instanceId={}, ownerChatterId={}, code={}",
                    registration.discovery.getCmdProxyInstanceId(), ownerChatterId, e.getCode());
            return false;
        }
    }

    private List<TeamDTO> parseTeams(Map<String, String> result) {
        JSONObject data = JSON.parseObject(requiredData(result));
        return data == null || data.getJSONArray("teams") == null
                ? Collections.emptyList()
                : new ArrayList<>(data.getJSONArray("teams").toJavaList(TeamDTO.class));
    }

    private void bind(String ownerChatterId, DiscoveryRegistration registration) {
        String instanceId = registration.discovery.getCmdProxyInstanceId();
        bindings.put(ownerChatterId, instanceId);
        keyValueFactory.save(KeyValue.builder()
                .key(BINDING_KEY_PREFIX + ownerChatterId)
                .value(instanceId)
                .owner(ownerChatterId)
                .desc("Fast Team cmdproxy instance binding")
                .share(false)
                .build());
        log.info("Fast Team owner绑定cmdproxy实例, ownerChatterId={}, instanceId={}",
                ownerChatterId, instanceId);
    }

    private String loadBinding(String ownerChatterId) {
        if (bindings.containsKey(ownerChatterId)) {
            return bindings.get(ownerChatterId);
        }
        KeyValue binding = keyValueFactory.selectOne(BINDING_KEY_PREFIX + ownerChatterId);
        if (binding == null || StringUtils.isBlank(binding.getValue())) {
            return null;
        }
        bindings.put(ownerChatterId, binding.getValue());
        return binding.getValue();
    }

    private Set<String> parseVisibleChatterIds(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptySet();
        }
        return new HashSet<>(JSON.parseArray(json, String.class));
    }

    private List<AcpRobotSyncSolution.AcpRobotParam> parseRobots(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        return JSON.parseArray(json, AcpRobotSyncSolution.AcpRobotParam.class);
    }

    private String buildRobotId(String robotName) {
        return "acp-" + robotName.replaceAll("[\\s\u3000]+", "_");
    }

    private String computeSourceGroupId(String ownerChatterId, String robotId) {
        List<String> ids = Arrays.asList(ownerChatterId, robotId);
        Collections.sort(ids);
        return ids.get(0) + ids.get(1);
    }

    private String stableUuid(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void registerEventCallback(TeamDiscoveryDTO discovery) {
        if (StringUtils.isBlank(discovery.getEventCommand())
                || !registeredEventGroups.add(discovery.getTransportGroup())) {
            return;
        }
        teamCommandTransport.registerEventCallback(
                discovery.getEventCommand(), discovery.getTransportGroup(),
                resultMap -> handleEvent(discovery, resultMap));
    }

    private void handleEvent(TeamDiscoveryDTO discovery, Map<String, String> resultMap) {
        if (resultMap == null
                || !discovery.getTransportGroup().equals(resultMap.get("transportGroup"))) {
            log.warn("忽略transportGroup不匹配的Fast Team event, instanceId={},"
                            + " expectedGroup={}, actualGroup={}",
                    discovery.getCmdProxyInstanceId(), discovery.getTransportGroup(),
                    resultMap == null ? null : resultMap.get("transportGroup"));
            return;
        }
        String teamId = resultMap.get("teamId");
        String boundInstanceId = teamInstanceBindings.get(teamId);
        if (StringUtils.isNotBlank(boundInstanceId)
                && !boundInstanceId.equals(discovery.getCmdProxyInstanceId())) {
            log.warn("忽略非Team绑定实例的事件, teamId={}, boundInstanceId={},"
                            + " eventInstanceId={}",
                    teamId, boundInstanceId, discovery.getCmdProxyInstanceId());
            return;
        }
        teamEventSolution.handle(resultMap);
    }

    private void rememberTeamInstance(TeamDTO team, DiscoveryRegistration registration) {
        if (team != null && StringUtils.isNotBlank(team.getTeamId())) {
            teamInstanceBindings.put(
                    team.getTeamId(), registration.discovery.getCmdProxyInstanceId());
        }
    }

    private void validate(TeamDiscoveryDTO discovery, Map<String, String> resultMap) {
        if (discovery == null
                || !SUPPORTED_SCHEMA_VERSION.equals(discovery.getSchemaVersion())
                || StringUtils.isBlank(discovery.getCmdProxyInstanceId())
                || StringUtils.isBlank(discovery.getTransportGroup())
                || !TEAM_ROBOT_GROUP.equals(discovery.getRobotGroup())) {
            throw new IllegalArgumentException("不支持的Fast Team discovery");
        }
        if (!discovery.getSchemaVersion().equals(resultMap.get("teamSchemaVersion"))
                || !discovery.getCmdProxyInstanceId().equals(resultMap.get("teamCmdProxyInstanceId"))
                || !discovery.getTransportGroup().equals(resultMap.get("teamTransportGroup"))) {
            throw new IllegalArgumentException("Fast Team discovery摘要字段不一致");
        }
    }

    private static final class DiscoveryRegistration {
        private final TeamDiscoveryDTO discovery;
        private final Set<String> visibleChatterIds;
        private final List<AcpRobotSyncSolution.AcpRobotParam> robots;
        private final long lastSeenAt;

        private DiscoveryRegistration(TeamDiscoveryDTO discovery,
                                      Set<String> visibleChatterIds,
                                      List<AcpRobotSyncSolution.AcpRobotParam> robots,
                                      long lastSeenAt) {
            this.discovery = discovery;
            this.visibleChatterIds = visibleChatterIds;
            this.robots = robots;
            this.lastSeenAt = lastSeenAt;
        }
    }
}
