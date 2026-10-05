package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.team.dto.TeamCreateMemberRequest;
import com.mola.molachat.team.dto.TeamCreateRequest;
import com.mola.molachat.team.dto.TeamDiscoveryDTO;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamHomeDTO;
import com.mola.molachat.team.dto.TeamMemberDTO;
import com.mola.molachat.team.dto.TeamMemberSourceDTO;
import com.mola.molachat.team.dto.RemoteTeamMemberSourceDTO;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String TEAM_MODE_NORMAL = "NORMAL";
    private static final String TEAM_MODE_CAPTAIN = "CAPTAIN";
    private static final String BINDING_KEY_PREFIX = "fast-team.instance-binding.";
    private static final long DISCOVERY_TTL_MILLIS = 120_000L;
    private static final long TEAM_SEND_SUBMISSION_TIMEOUT_MILLIS = 8_000L;

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

    public TeamDiscoveryDTO getPlacementCapability(String ownerChatterId) {
        String homeInstanceId = getHome(ownerChatterId).getHomeCmdProxyInstanceId();
        if (StringUtils.isBlank(homeInstanceId)) {
            throw new TeamCommandException("HOME_INSTANCE_REQUIRED", "请先选择本机ACP设备");
        }
        DiscoveryRegistration registration = discoveries.get(homeInstanceId);
        if (registration == null || !isActive(registration)
                || !registration.transportReachable
                || !registration.discovery.isBusinessCommandsReady()
                || authorizedSources(registration, ownerChatterId, true).isEmpty()) {
            throw new TeamCommandException("TEAM_NOT_READY", "本机ACP设备尚未就绪");
        }
        return registration.discovery;
    }

    public boolean isBusinessReady() {
        return discoveries.values().stream()
                .filter(this::isActive)
                .anyMatch(registration -> registration.discovery.isBusinessCommandsReady());
    }

    /**
     * 普通 ACP 是 owner-home 投影；remote callback 只能更新 Team registry。
     * 一个旧式全量 robots 快照覆盖多个 owner 时，必须对全部 owner 都是 home 才能应用。
     */
    public boolean shouldSyncOrdinaryRobots(String instanceId, Set<String> ownerChatterIds) {
        if (StringUtils.isBlank(instanceId) || ownerChatterIds == null
                || ownerChatterIds.isEmpty()) {
            return false;
        }
        for (String ownerChatterId : ownerChatterIds) {
            try {
                if (!instanceId.equals(getHome(ownerChatterId).getHomeCmdProxyInstanceId())) {
                    return false;
                }
            } catch (TeamCommandException unavailable) {
                return false;
            }
        }
        return true;
    }

    public TeamHomeDTO getHome(String ownerChatterId) {
        if (StringUtils.isBlank(ownerChatterId)) {
            throw new TeamCommandException("VALIDATION_ERROR", "ownerChatterId不能为空");
        }
        synchronized (ownerResolutionLocks.computeIfAbsent(ownerChatterId, key -> new Object())) {
            List<DiscoveryRegistration> candidates = homeCandidates(ownerChatterId, false);
            String homeInstanceId = loadBinding(ownerChatterId);
            boolean homeStillEligible = StringUtils.isBlank(homeInstanceId);
            for (DiscoveryRegistration candidate : candidates) {
                homeStillEligible |= candidate.discovery.getCmdProxyInstanceId()
                        .equals(homeInstanceId);
            }
            if (!homeStillEligible) {
                log.warn("忽略不再属于owner普通ACP范围的旧home绑定, owner={}, instanceId={}",
                        ownerChatterId, homeInstanceId);
                homeInstanceId = null;
            }
            if (StringUtils.isBlank(homeInstanceId) && candidates.size() == 1) {
                bind(ownerChatterId, candidates.get(0));
                homeInstanceId = candidates.get(0).discovery.getCmdProxyInstanceId();
            }
            TeamHomeDTO result = new TeamHomeDTO();
            result.setHomeCmdProxyInstanceId(homeInstanceId);
            result.setSelectionRequired(StringUtils.isBlank(homeInstanceId)
                    && candidates.size() > 1);
            for (DiscoveryRegistration candidate : candidates) {
                TeamHomeDTO.Device device = new TeamHomeDTO.Device();
                device.setCmdProxyInstanceId(candidate.discovery.getCmdProxyInstanceId());
                device.setStatus(candidateStatus(candidate));
                device.setSelected(candidate.discovery.getCmdProxyInstanceId()
                        .equals(homeInstanceId));
                result.getDevices().add(device);
            }
            return result;
        }
    }

    public TeamHomeDTO selectHome(String ownerChatterId, String instanceId) {
        if (StringUtils.isBlank(ownerChatterId) || StringUtils.isBlank(instanceId)) {
            throw new TeamCommandException("VALIDATION_ERROR", "ownerChatterId和instanceId不能为空");
        }
        Object ownerLock = ownerResolutionLocks.computeIfAbsent(
                ownerChatterId, key -> new Object());
        String previousId;
        DiscoveryRegistration previous;
        synchronized (ownerLock) {
            homeCandidates(ownerChatterId, true).stream()
                    .filter(candidate -> instanceId.equals(
                            candidate.discovery.getCmdProxyInstanceId()))
                    .findFirst()
                    .orElseThrow(() -> new TeamCommandException(
                            "HOME_INSTANCE_NOT_FOUND", "所选本机ACP设备不可用"));
            previousId = loadBinding(ownerChatterId);
            if (StringUtils.isNotBlank(previousId) && !previousId.equals(instanceId)) {
                boolean hasActiveMixedTeam = hasProjectedActiveTeam(ownerChatterId);
                if (hasActiveMixedTeam) {
                    throw new TeamCommandException("HOME_INSTANCE_CONFLICT",
                            "存在活跃Team，删除后才能切换本机ACP设备");
                }
            }
            previous = StringUtils.isBlank(previousId) ? null : discoveries.get(previousId);
        }
        boolean previousHasTeams = StringUtils.isNotBlank(previousId)
                && !previousId.equals(instanceId) && previous != null
                && hasExistingTeams(previous, ownerChatterId);
        synchronized (ownerLock) {
            DiscoveryRegistration selected = homeCandidates(ownerChatterId, true).stream()
                    .filter(candidate -> instanceId.equals(
                            candidate.discovery.getCmdProxyInstanceId()))
                    .findFirst()
                    .orElseThrow(() -> new TeamCommandException(
                            "HOME_INSTANCE_NOT_FOUND", "所选本机ACP设备不可用"));
            if (!Objects.equals(previousId, loadBinding(ownerChatterId))) {
                throw new TeamCommandException("HOME_INSTANCE_CONFLICT",
                        "本机ACP设备绑定已变化，请刷新后重试");
            }
            boolean hasActiveMixedTeam = hasProjectedActiveTeam(ownerChatterId);
            if (hasActiveMixedTeam || previousHasTeams) {
                throw new TeamCommandException("HOME_INSTANCE_CONFLICT",
                        "存在活跃Team，删除后才能切换本机ACP设备");
            }
            bind(ownerChatterId, selected);
        }
        return getHome(ownerChatterId);
    }

    /**
     * 使用cmd-proxy实例身份和普通ACP路由组联合定位来源，避免多实例之间串事件。
     */
    public TeamMemberSourceDTO findAcpSource(String instanceId, String sourceGroupId) {
        if (StringUtils.isBlank(instanceId) || StringUtils.isBlank(sourceGroupId)) {
            return null;
        }
        DiscoveryRegistration registration = discoveries.get(instanceId);
        if (registration == null || !isActive(registration)
                || registration.discovery.getTeamMemberSources() == null) {
            return null;
        }
        return registration.discovery.getTeamMemberSources().stream()
                .filter(source -> source != null
                        && sourceGroupId.equals(source.getSourceGroupId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 普通ACP联系人是否仍有精确匹配且活跃的cmd-proxy来源。
     */
    public boolean isAcpSourceAvailable(String sourceRobotId, Set<String> ownerChatterIds) {
        if (StringUtils.isBlank(sourceRobotId)) {
            return false;
        }
        return discoveries.values().stream()
                .filter(this::isActive)
                .filter(registration -> registration.transportReachable)
                .filter(registration -> registration.discovery.getTeamMemberSources() != null)
                .flatMap(registration -> registration.discovery.getTeamMemberSources().stream())
                .filter(source -> source != null
                        && sourceRobotId.equals(source.getSourceRobotId()))
                .anyMatch(source -> ownerChatterIds == null || ownerChatterIds.isEmpty()
                        || ownerChatterIds.contains(source.getOwnerChatterId()));
    }

    /**
     * 返回普通ACP机器人的精确路由组；保留最近发现的来源，以便实例刚失联时仍可主动探测。
     */
    public List<String> findAcpSourceGroupIds(String sourceRobotId,
                                               Set<String> ownerChatterIds) {
        if (StringUtils.isBlank(sourceRobotId)) {
            return Collections.emptyList();
        }
        return discoveries.values().stream()
                .filter(registration -> registration.discovery.getTeamMemberSources() != null)
                .flatMap(registration -> registration.discovery.getTeamMemberSources().stream())
                .filter(source -> source != null
                        && sourceRobotId.equals(source.getSourceRobotId()))
                .filter(source -> ownerChatterIds == null || ownerChatterIds.isEmpty()
                        || ownerChatterIds.contains(source.getOwnerChatterId()))
                .map(TeamMemberSourceDTO::getSourceGroupId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    public TeamDTO create(TeamCreateRequest request) {
        normalizeAndValidateTeamMode(request);
        Set<String> placementIds = request.getMembers().stream()
                .map(TeamCreateMemberRequest::getCmdProxyInstanceId)
                .collect(Collectors.toSet());
        if (placementIds.size() > 1) {
            return createMixed(request);
        }
        return createSinglePlacement(request, placementIds);
    }

    private TeamDTO createSinglePlacement(TeamCreateRequest request,
                                          Set<String> placementIds) {
        TeamHomeDTO home = getHome(request.getChatterId());
        if (StringUtils.isBlank(home.getHomeCmdProxyInstanceId())) {
            throw new TeamCommandException("HOME_INSTANCE_REQUIRED", "请先选择本机ACP设备");
        }
        if (!home.getHomeCmdProxyInstanceId().equals(placementIds.iterator().next())) {
            throw new TeamCommandException("REMOTE_ONLY_TEAM",
                    "只选择远程成员不能创建Team，请至少选择一位本机成员");
        }
        DiscoveryRegistration registration = resolvePlacement(request);
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("requestId", request.getRequestId());
        payload.put("teamId", stableUuid("team:" + request.getRequestId()));
        payload.put("ownerChatterId", request.getChatterId());
        payload.put("name", request.getName());
        payload.put("mode", request.getMode());
        if (StringUtils.isNotBlank(request.getCaptainTeamMemberId())) {
            payload.put("captainTeamMemberId", request.getCaptainTeamMemberId());
        }
        JSONArray members = new JSONArray();
        Set<String> memberIds = new HashSet<>();
        for (int index = 0; index < request.getMembers().size(); index++) {
            TeamCreateMemberRequest source = request.getMembers().get(index);
            TeamMemberSourceDTO discovered = requireDiscoveredSource(
                    registration, request.getChatterId(), source);
            JSONObject member = new JSONObject();
            String memberId = StringUtils.defaultIfBlank(
                    StringUtils.trim(source.getTeamMemberId()),
                    stableUuid("team-member:" + request.getRequestId()
                            + ":" + index + ":" + discovered.getSourceGroupId()));
            requireUniqueMemberId(memberIds, memberId);
            member.put("teamMemberId", memberId);
            member.put("sourceRobotId", discovered.getSourceRobotId());
            member.put("sourceGroupId", discovered.getSourceGroupId());
            member.put("order", index);
            member.put("remark", StringUtils.defaultString(source.getRemark()));
            members.add(member);
        }
        validateCaptainMembership(request, memberIds);
        payload.put("members", members);
        Map<String, String> result = invoke(registration, "acpTeamCreate", payload);
        TeamDTO team = JSON.parseObject(requiredData(result), TeamDTO.class);
        rememberTeamInstance(team, registration);
        teamSnapshotSolution.upsert(team);
        return team;
    }

    private TeamDTO createMixed(TeamCreateRequest request) {
        TeamDTO team = coordinator(request.getChatterId(), "create", (JSONObject) JSON.toJSON(request))
                .getJSONObject("team").toJavaObject(TeamDTO.class);
        teamSnapshotSolution.upsert(team); teamRobotProjectionSolution.sync(team); return team;
    }

    public List<TeamDTO> list(String ownerChatterId) {
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, true, true);
        JSONObject payload = new JSONObject(); payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION); payload.put("ownerChatterId", ownerChatterId);
        List<TeamDTO> teams = parseTeams(invoke(registration, "acpTeamList", payload));
        teams.forEach(team -> rememberTeamInstance(team, registration));
        try {
            JSONArray global = coordinator(ownerChatterId, "list", new JSONObject()).getJSONArray("teams");
            if (global != null) for (TeamDTO team : global.toJavaList(TeamDTO.class)) {
                teams.removeIf(local -> team.getTeamId().equals(local.getTeamId())); teams.add(team);
            }
        } catch (TeamCommandException unavailable) {
            log.debug("中心队伍列表暂不可用: {}", unavailable.getCode());
            for (TeamDTO known : teamSnapshotSolution.list(ownerChatterId)) {
                if (!known.isCoordinated()) continue;
                teams.removeIf(local -> known.getTeamId().equals(local.getTeamId()));
                teams.add(known);
            }
        }
        teamSnapshotSolution.replaceAll(ownerChatterId, teams);
        if (!teams.isEmpty()) teamRobotProjectionSolution.syncAll(ownerChatterId, teams);
        return teams;
    }

    public TeamDTO get(String ownerChatterId, String teamId) {
        JSONObject payload = new JSONObject(); payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("ownerChatterId", ownerChatterId); payload.put("teamId", teamId);
        if (isCoordinated(ownerChatterId, teamId)) return coordinator(ownerChatterId, "get", payload).getJSONObject("team").toJavaObject(TeamDTO.class);
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, false, true);
        TeamDTO team = JSON.parseObject(requiredData(invoke(registration, "acpTeamGet", payload))).getJSONObject("team").toJavaObject(TeamDTO.class);
        rememberTeamInstance(team, registration); teamSnapshotSolution.upsert(team); return team;
    }

    public TeamDTO delete(String ownerChatterId, String teamId, String requestId, Long expectedVersion) {
        JSONObject payload = new JSONObject(); payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("ownerChatterId", ownerChatterId); payload.put("teamId", teamId);
        payload.put("requestId", requestId); payload.put("expectedVersion", expectedVersion);
        JSONObject data = isCoordinated(ownerChatterId, teamId) ? coordinator(ownerChatterId, "delete", payload) : invokeData(ownerChatterId, "acpTeamDelete", payload);
        TeamDTO team = data.getJSONObject("team").toJavaObject(TeamDTO.class);
        teamSnapshotSolution.upsert(team); return team;
    }

    public List<TeamDTO> snapshot(String ownerChatterId) {
        return teamSnapshotSolution.list(ownerChatterId);
    }

    public List<String> cleanupStaleTeams(String ownerChatterId) {
        JSONArray ids = coordinator(ownerChatterId, "cleanup", new JSONObject()).getJSONArray("teamIds");
        return ids == null ? Collections.emptyList() : ids.toJavaList(String.class);
    }

    public JSONObject send(String ownerChatterId, String teamId, String teamMemberId,
                           String acpClientId, String message,
                           List<Map<String, String>> files) {
        return send(ownerChatterId, teamId, teamMemberId, acpClientId, message, files,
                UUID.randomUUID().toString());
    }

    private JSONObject send(String ownerChatterId, String teamId, String teamMemberId,
                            String acpClientId, String message,
                            List<Map<String, String>> files, String requestId) {
        JSONObject payload = memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId);
        payload.put("requestId", StringUtils.defaultIfBlank(
                StringUtils.trim(requestId), UUID.randomUUID().toString()));
        payload.put("message", message);
        if (files != null && !files.isEmpty()) {
            payload.put("files", files);
        }
        try {
            return invokeData(ownerChatterId, "acpTeamSend", payload,
                    TEAM_SEND_SUBMISSION_TIMEOUT_MILLIS);
        } catch (TeamCommandTransport.CommandTimeoutException timeout) {
            throw new TeamCommandException("SUBMISSION_TIMEOUT",
                    "Team消息提交超时, requestId=" + payload.getString("requestId"));
        }
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

    public JSONObject readTextFile(String ownerChatterId, String teamId, String teamMemberId,
                                   String acpClientId, String path, int maxBytes) {
        JSONObject payload = memberPayload(ownerChatterId, teamId, teamMemberId, acpClientId);
        payload.put("path", path);
        payload.put("maxBytes", maxBytes);
        return invokeData(ownerChatterId, "acpTeamReadTextFile", payload);
    }

    public JSONObject getSessionHistory(String ownerChatterId, String teamId,
                                        String teamMemberId, String acpClientId) {
        return invokeData(ownerChatterId, "acpTeamGetSessionHistory",
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
        TeamHomeDTO home = getHome(ownerChatterId); List<TeamMemberDTO> candidates = new ArrayList<>();
        if (StringUtils.isBlank(home.getHomeCmdProxyInstanceId())) {
            discoveries.values().forEach(registration -> appendCandidates(candidates, registration, ownerChatterId, null, home.isSelectionRequired()));
            return candidates;
        }
        try {
            JSONArray sources = coordinator(ownerChatterId, "sources", new JSONObject()).getJSONArray("sources");
            if (sources != null) candidates.addAll(sources.toJavaList(TeamMemberDTO.class)); return candidates;
        } catch (TeamCommandException unavailable) {
            discoveries.values().stream().sorted(Comparator.comparing(
                    registration -> registration.discovery.getCmdProxyInstanceId()))
                    .forEach(registration -> appendCandidates(candidates, registration,
                            ownerChatterId, home.getHomeCmdProxyInstanceId(), false));
            // Keep known remote sources visible while the coordinator reconnects.
            candidates.stream().filter(candidate -> "REMOTE".equals(candidate.getSourceType()))
                    .forEach(candidate -> candidate.setStatus("TEAM_NOT_READY"));
            return candidates;
        }
    }

    private void appendCandidates(List<TeamMemberDTO> candidates,
                                  DiscoveryRegistration registration,
                                  String ownerChatterId, String homeInstanceId,
                                  boolean homeSelectionRequired) {
        boolean home = registration.discovery.getCmdProxyInstanceId().equals(homeInstanceId)
                || registration.discovery.getCmdProxyInstanceId().equals(
                        loadBinding(ownerChatterId));
        if (home && registration.discovery.getTeamMemberSources() == null) {
            if (!registration.visibleChatterIds.contains(ownerChatterId)) {
                return;
            }
            TeamMemberDTO unsupported = candidateBase(registration);
            unsupported.setDisplayName("ACP设备");
            unsupported.setStatus("DISCOVERY_UNSUPPORTED");
            unsupported.setDiscoverySupported(false);
            unsupported.setRemark("该 cmd-proxy 不支持 Team 来源发现，请升级后重试");
            decoratePlacement(unsupported, registration, homeInstanceId,
                    homeSelectionRequired);
            candidates.add(unsupported);
            return;
        }
        String status = candidateStatus(registration);
        boolean useLocalSources = home || homeSelectionRequired;
        for (TeamMemberSourceDTO source : authorizedSources(
                registration, ownerChatterId, useLocalSources)) {
            if (source == null
                    || StringUtils.isBlank(source.getSourceRobotId())
                    || StringUtils.isBlank(source.getSourceGroupId())) {
                continue;
            }
            TeamMemberDTO candidate = candidateBase(registration);
            candidate.setOwnerChatterId(source.getOwnerChatterId());
            candidate.setSourceRobotId(source.getSourceRobotId());
            candidate.setSourceGroupId(source.getSourceGroupId());
            candidate.setDisplayName(StringUtils.defaultIfBlank(
                    source.getDisplayName(), source.getRobotName()));
            candidate.setAvatar(StringUtils.defaultIfBlank(source.getAvatar(), "img/kiro.png"));
            candidate.setRemark(source.getRemark());
            candidate.setOnlyTeamMember(source.isOnlyTeamMember());
            candidate.setDiscoverySupported(true);
            candidate.setStatus(status);
            decoratePlacement(candidate, registration, homeInstanceId,
                    homeSelectionRequired);
            candidates.add(candidate);
        }
    }

    private void decoratePlacement(TeamMemberDTO candidate,
                                   DiscoveryRegistration registration,
                                   String homeInstanceId, boolean homeSelectionRequired) {
        boolean home = registration.discovery.getCmdProxyInstanceId().equals(homeInstanceId);
        boolean mixedSupported = registration.discovery.getCapabilities() != null
                && registration.discovery.getCapabilities().isMixedTeamFragment()
                && registration.discovery.getCapabilities().isMixedTeamTalkToDeliver();
        candidate.setSourceType(home ? "HOME" : "REMOTE");
        candidate.setSourceLabel(home ? "本机" : null);
        candidate.setHomeSelectionRequired(homeSelectionRequired);
        candidate.setMixedSupported(mixedSupported);
        if (homeSelectionRequired) {
            candidate.setStatus("HOME_SELECTION_REQUIRED");
        } else if (!home && !mixedSupported) {
            candidate.setStatus("MIXED_UNSUPPORTED");
        }
    }

    private TeamMemberDTO candidateBase(DiscoveryRegistration registration) {
        TeamMemberDTO candidate = new TeamMemberDTO();
        candidate.setCmdProxyInstanceId(registration.discovery.getCmdProxyInstanceId());
        candidate.setTransportGroup(registration.discovery.getTransportGroup());
        return candidate;
    }

    private String candidateStatus(DiscoveryRegistration registration) {
        if (!isActive(registration)) {
            return "DISCOVERY_STALE";
        }
        if (!registration.transportReachable) {
            return "TRANSPORT_UNREACHABLE";
        }
        if (!registration.discovery.isBusinessCommandsReady()) {
            return "BUSINESS_COMMANDS_NOT_READY";
        }
        return "AVAILABLE";
    }

    private DiscoveryRegistration resolvePlacement(TeamCreateRequest request) {
        TeamCreateMemberRequest first = request.getMembers().get(0);
        for (TeamCreateMemberRequest member : request.getMembers()) {
            if (!first.getCmdProxyInstanceId().equals(member.getCmdProxyInstanceId())
                    || !first.getTransportGroup().equals(member.getTransportGroup())) {
                throw new TeamCommandException("MIXED_TEAM_PLACEMENT",
                        "同一支 Team 的成员必须来自同一个 cmd-proxy 实例");
            }
        }
        DiscoveryRegistration registration = discoveries.get(first.getCmdProxyInstanceId());
        if (registration == null
                || !first.getTransportGroup().equals(registration.discovery.getTransportGroup())
                || !isActive(registration)
                || !registration.transportReachable
                || !registration.discovery.isBusinessCommandsReady()) {
            throw new TeamCommandException("TEAM_NOT_READY", "所选 cmd-proxy placement 当前不可用");
        }
        if (!registration.visibleChatterIds.isEmpty()
                && !registration.visibleChatterIds.contains(request.getChatterId())
                && !isStarweaveHome(registration.discovery, request.getChatterId())) {
            throw new TeamCommandException("UNAUTHORIZED", "当前用户不可使用所选 cmd-proxy placement");
        }
        if (registration.discovery.getTeamMemberSources() == null) {
            throw new TeamCommandException("TEAM_SOURCE_DISCOVERY_UNSUPPORTED",
                    "所选 cmd-proxy 不支持 Team 来源发现");
        }
        return registration;
    }

    private TeamMemberSourceDTO requireDiscoveredSource(
            DiscoveryRegistration registration, String ownerChatterId,
            TeamCreateMemberRequest requested) {
        boolean home = registration.discovery.getCmdProxyInstanceId()
                .equals(loadBinding(ownerChatterId));
        return authorizedSources(registration, ownerChatterId, home).stream()
                .filter(source -> source != null
                        && requested.getSourceRobotId().equals(source.getSourceRobotId())
                        && requested.getSourceGroupId().equals(source.getSourceGroupId()))
                .findFirst()
                .orElseThrow(() -> new TeamCommandException("SOURCE_ROBOT_NOT_FOUND",
                        "所选 Team 来源不在当前 discovery 中"));
    }

    private List<TeamMemberSourceDTO> authorizedSources(
            DiscoveryRegistration registration, String ownerChatterId, boolean home) {
        List<TeamMemberSourceDTO> result = new ArrayList<>();
        if (home) {
            if ((!registration.visibleChatterIds.contains(ownerChatterId)
                    && !isStarweaveHome(registration.discovery, ownerChatterId))
                    || registration.discovery.getTeamMemberSources() == null) {
                return Collections.emptyList();
            }
            result.addAll(registration.discovery.getTeamMemberSources().stream()
                    .filter(source -> source != null
                            && ownerChatterId.equals(source.getOwnerChatterId()))
                    .collect(Collectors.toList()));
        }
        if (registration.discovery.getRemoteTeamMemberSources() == null) {
            return result;
        }
        for (RemoteTeamMemberSourceDTO remote
                : registration.discovery.getRemoteTeamMemberSources()) {
            if (remote == null
                    || !ownerChatterId.equals(remote.getGranteeOwnerChatterId())
                    || !registration.discovery.getCmdProxyInstanceId()
                    .equals(remote.getParticipantInstanceId())) {
                continue;
            }
            TeamMemberSourceDTO source = new TeamMemberSourceDTO();
            source.setOwnerChatterId(ownerChatterId);
            source.setSourceGroupId(remote.getSourceGroupId());
            source.setSourceRobotId(remote.getSourceRobotId());
            source.setRobotName(remote.getRobotName());
            source.setDisplayName(remote.getDisplayName());
            source.setAvatar(remote.getAvatar());
            source.setRemark(remote.getRemark());
            result.add(source);
        }
        return result;
    }

    private boolean isStarweaveHome(TeamDiscoveryDTO discovery,
                                    String ownerChatterId) {
        return discovery != null && ownerChatterId != null
                && ownerChatterId.equals("starweave-"
                + discovery.getCmdProxyInstanceId());
    }

    private Map<String, String> invoke(String ownerChatterId, String command,
                                       JSONObject payload) {
        return invoke(resolveRegistration(ownerChatterId, false, true), command, payload);
    }

    private Map<String, String> invoke(DiscoveryRegistration registration, String command,
                                       JSONObject payload) {
        return invoke(registration, command, payload, 0L);
    }

    private Map<String, String> invoke(DiscoveryRegistration registration, String command,
                                       JSONObject payload, long timeoutMillis) {
        ensureCommandReady(registration, command);
        Map<String, String> result;
        try {
            result = timeoutMillis > 0L
                    ? teamCommandTransport.send(command,
                    registration.discovery.getTransportGroup(), payload.toJSONString(),
                    timeoutMillis)
                    : teamCommandTransport.send(command,
                    registration.discovery.getTransportGroup(), payload.toJSONString());
        } catch (TeamCommandTransport.CommandTimeoutException timeout) {
            throw timeout;
        } catch (RuntimeException transportFailure) {
            registration.transportReachable = false;
            log.warn("Fast Team命令调用失败, command={}, instanceId={}", command,
                    registration.discovery.getCmdProxyInstanceId(), transportFailure);
            throw new TeamCommandException("INTERNAL_ERROR", "cmdproxy Team命令调用失败");
        }
        if (result == null) {
            registration.transportReachable = false;
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
        return invokeData(ownerChatterId, command, payload, 0L);
    }

    private JSONObject invokeData(String ownerChatterId, String command, JSONObject payload,
                                  long timeoutMillis) {
        String teamId = payload.getString("teamId");
        String teamMemberId = payload.getString("teamMemberId");
        if (StringUtils.isNotBlank(teamMemberId) && isCoordinated(ownerChatterId, teamId)) {
            JSONObject request = new JSONObject(payload); request.put("action", memberAction(command));
            return coordinator(ownerChatterId, "member", request, timeoutMillis);
        }
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, false, true);
        Map<String, String> result = timeoutMillis > 0L
                ? invoke(registration, command, payload, timeoutMillis)
                : invoke(registration, command, payload);
        return JSON.parseObject(requiredData(result));
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

    private void normalizeAndValidateTeamMode(TeamCreateRequest request) {
        String mode = effectiveMode(request.getMode());
        request.setMode(mode);
        String captainId = StringUtils.trimToNull(request.getCaptainTeamMemberId());
        request.setCaptainTeamMemberId(captainId);
        if (TEAM_MODE_NORMAL.equals(mode)) {
            if (captainId != null) {
                throw new TeamCommandException("VALIDATION_ERROR",
                        "普通模式不能指定队长");
            }
            return;
        }
        if (request.getMembers() == null || request.getMembers().size() < 2
                || captainId == null) {
            throw new TeamCommandException("CAPTAIN_REQUIRED",
                    "队长模式至少需要两名成员，并且必须手工指定一名队长");
        }
    }

    private void validateCaptainMembership(TeamCreateRequest request, Set<String> memberIds) {
        if (TEAM_MODE_CAPTAIN.equals(request.getMode())
                && !memberIds.contains(request.getCaptainTeamMemberId())) {
            throw new TeamCommandException("CAPTAIN_REQUIRED",
                    "指定的队长不在本次选择的成员中");
        }
    }

    private void requireUniqueMemberId(Set<String> memberIds, String memberId) {
        if (StringUtils.isBlank(memberId) || !memberIds.add(memberId)) {
            throw new TeamCommandException("VALIDATION_ERROR", "teamMemberId必须唯一且不能为空");
        }
    }

    private String effectiveMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            return TEAM_MODE_NORMAL;
        }
        String normalized = mode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!TEAM_MODE_NORMAL.equals(normalized) && !TEAM_MODE_CAPTAIN.equals(normalized)) {
            throw new TeamCommandException("VALIDATION_ERROR", "不支持的Team模式: " + mode);
        }
        return normalized;
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
        Object ownerLock = ownerResolutionLocks.computeIfAbsent(
                ownerChatterId, key -> new Object());
        List<DiscoveryRegistration> candidates;
        synchronized (ownerLock) {
            candidates = homeCandidates(ownerChatterId, requireReady);
            DiscoveryRegistration resolved = resolveWithoutProbe(
                    ownerChatterId, candidates, probeExistingTeams);
            if (resolved != null) {
                return resolved;
            }
        }
        List<String> ownerInstanceIds = candidates.stream()
                .filter(candidate -> hasExistingTeams(candidate, ownerChatterId))
                .map(candidate -> candidate.discovery.getCmdProxyInstanceId())
                .collect(Collectors.toList());
        synchronized (ownerLock) {
            List<DiscoveryRegistration> current = homeCandidates(ownerChatterId, requireReady);
            List<DiscoveryRegistration> owners = current.stream()
                    .filter(candidate -> ownerInstanceIds.contains(
                            candidate.discovery.getCmdProxyInstanceId()))
                    .collect(Collectors.toList());
            if (owners.size() == 1) {
                bind(ownerChatterId, owners.get(0));
                return owners.get(0);
            }
            if (owners.size() > 1) {
                throw new TeamCommandException("CMD_PROXY_INSTANCE_CONFLICT",
                        "多个cmdproxy实例同时保存当前用户的Team，请先解决实例冲突");
            }
            DiscoveryRegistration resolved = resolveWithoutProbe(
                    ownerChatterId, current, true);
            if (resolved != null) {
                return resolved;
            }
            String boundInstanceId = loadBinding(ownerChatterId);
            DiscoveryRegistration bound = current.stream()
                    .filter(candidate -> Objects.equals(boundInstanceId,
                            candidate.discovery.getCmdProxyInstanceId()))
                    .findFirst().orElse(null);
            if (bound != null) {
                return bound;
            }
        }
        throw new TeamCommandException("CMD_PROXY_INSTANCE_CONFLICT",
                "当前用户有多个可用ACP设备，请先选择本机设备");
    }

    /**
     * 只执行本地快照判断；返回 null 表示多候选且需要在锁外探测现有 Team。
     */
    private DiscoveryRegistration resolveWithoutProbe(String ownerChatterId,
                                                       List<DiscoveryRegistration> candidates,
                                                       boolean probeExistingTeams) {
        if (candidates.isEmpty()) {
            throw new TeamCommandException("TEAM_NOT_READY", "未发现当前用户的Fast Team transport");
        }
        String boundInstanceId = loadBinding(ownerChatterId);
        DiscoveryRegistration bound = candidates.stream()
                .filter(candidate -> Objects.equals(boundInstanceId,
                        candidate.discovery.getCmdProxyInstanceId()))
                .findFirst().orElse(null);
        if (StringUtils.isNotBlank(boundInstanceId) && bound == null) {
            log.warn("Fast Team已绑定实例未出现在当前discovery中,"
                            + " ownerChatterId={}, boundInstanceId={}",
                    ownerChatterId, boundInstanceId);
        }
        if (bound != null && (!probeExistingTeams || candidates.size() == 1)) {
            return bound;
        }
        if (candidates.size() == 1) {
            if (candidates.get(0).discovery.isBusinessCommandsReady()) {
                bind(ownerChatterId, candidates.get(0));
            }
            return candidates.get(0);
        }
        if (probeExistingTeams) {
            return null;
        }
        throw new TeamCommandException("CMD_PROXY_INSTANCE_CONFLICT",
                "当前用户有多个可用ACP设备，请先选择本机设备");
    }

    private List<DiscoveryRegistration> homeCandidates(String ownerChatterId,
                                                        boolean requireReady) {
        return discoveries.values().stream()
                .filter(this::isActive)
                .filter(registration -> !requireReady
                        || registration.discovery.isBusinessCommandsReady())
                .filter(registration -> registration.visibleChatterIds.contains(ownerChatterId)
                        || isStarweaveHome(registration.discovery, ownerChatterId))
                .sorted(Comparator.comparing(
                        registration -> registration.discovery.getCmdProxyInstanceId()))
                .collect(Collectors.toList());
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

    private String stableUuid(String source) {
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void registerEventCallback(TeamDiscoveryDTO discovery) {
        if (StringUtils.isBlank(discovery.getEventCommand())) return;
        if (registeredEventGroups.add(discovery.getTransportGroup()))
            teamCommandTransport.registerEventCallback(discovery.getEventCommand(), discovery.getTransportGroup(),
                    result -> handleEvent(discovery, result));
    }

    private void handleEvent(TeamDiscoveryDTO discovery, Map<String, String> resultMap) {
        if (resultMap == null || !discovery.getTransportGroup().equals(resultMap.get("transportGroup"))) return;
        String bound = teamInstanceBindings.get(resultMap.get("teamId"));
        if (StringUtils.isNotBlank(bound) && !bound.equals(discovery.getCmdProxyInstanceId())) return;
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

    private JSONObject coordinator(String owner, String operation, JSONObject payload) {
        return coordinator(owner, operation, payload, 0L);
    }

    private JSONObject coordinator(String owner, String operation, JSONObject payload, long timeoutMillis) {
        JSONObject request = new JSONObject(true); request.put("ownerChatterId", owner); request.put("operation", operation); request.put("payload", payload);
        DiscoveryRegistration home = resolveRegistration(owner, false, true);
        return JSON.parseObject(requiredData(timeoutMillis > 0L
                ? invoke(home, "acpTeamCoordinator", request, timeoutMillis)
                : invoke(home, "acpTeamCoordinator", request)));
    }

    private boolean isCoordinated(String owner, String teamId) {
        return teamSnapshotSolution.list(owner).stream().anyMatch(team -> Objects.equals(teamId, team.getTeamId()) && team.isCoordinated());
    }

    private boolean hasProjectedActiveTeam(String owner) {
        return teamSnapshotSolution.list(owner).stream().anyMatch(team -> team.isCoordinated()
                && !"DELETED".equals(team.getState()) && !"FAILED".equals(team.getState()));
    }

    private String memberAction(String command) {
        switch (command) {
            case "acpTeamSend": return "send";
            case "acpTeamCancel": return "cancel";
            case "acpTeamNewSession": return "newSession";
            case "acpTeamListSessions": return "listSessions";
            case "acpTeamGetSessionHistory": return "history";
            case "acpTeamRestoreSession": return "restore";
            case "acpTeamGetStatus": return "status";
            case "acpTeamGetContextUsage": return "context";
            case "acpTeamMemoryDream": return "memoryDream";
            case "readTextFile": case "acpTeamReadTextFile": return "readTextFile";
            default: throw new TeamCommandException("VALIDATION_ERROR", "不支持的成员操作");
        }
    }

    private static final class DiscoveryRegistration {
        private final TeamDiscoveryDTO discovery;
        private final Set<String> visibleChatterIds;
        private final long lastSeenAt;
        private volatile boolean transportReachable = true;

        private DiscoveryRegistration(TeamDiscoveryDTO discovery,
                                      Set<String> visibleChatterIds,
                                      long lastSeenAt) {
            this.discovery = discovery;
            this.visibleChatterIds = visibleChatterIds;
            this.lastSeenAt = lastSeenAt;
        }
    }
}
