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
import com.mola.molachat.team.model.MixedTeamRecord;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.annotation.PreDestroy;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    private static final long TALK_TO_MAX_TTL_MILLIS = 30L * 60L * 1000L;
    private static final long TALK_TO_MAX_FUTURE_SKEW_MILLIS = 60_000L;
    private static final long TEAM_SEND_SUBMISSION_TIMEOUT_MILLIS = 8_000L;
    private static final int TALK_TO_MAX_DEPTH = 5;

    private final Map<String, DiscoveryRegistration> discoveries = new ConcurrentHashMap<>();
    private final Map<String, String> bindings = new ConcurrentHashMap<>();
    private final Map<String, String> teamInstanceBindings = new ConcurrentHashMap<>();
    private final Map<String, Object> ownerResolutionLocks = new ConcurrentHashMap<>();
    private final Set<String> registeredEventGroups = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor starweaveProjectionExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1024),
            runnable -> {
                Thread thread = new Thread(runnable, "starweave-team-projection");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

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

    @Resource
    private MixedTeamStore mixedTeamStore;

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
                boolean hasActiveMixedTeam = mixedTeamStore.listByOwner(ownerChatterId).stream()
                        .anyMatch(record -> !"DELETED".equals(record.getState())
                                && !"FAILED".equals(record.getState()));
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
            boolean hasActiveMixedTeam = mixedTeamStore.listByOwner(ownerChatterId).stream()
                    .anyMatch(record -> !"DELETED".equals(record.getState())
                            && !"FAILED".equals(record.getState()));
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
        final Object ownerLock = ownerResolutionLocks.computeIfAbsent(
                request.getChatterId(), key -> new Object());
        final Map<String, DiscoveryRegistration> participants;
        final MixedTeamRecord record;
        synchronized (ownerLock) {
            String teamId = stableUuid("team:" + request.getRequestId());
            String payloadHash = sha256(request.getChatterId() + "\n"
                    + request.getName() + "\n" + request.getMode() + "\n"
                    + StringUtils.defaultString(request.getCaptainTeamMemberId()) + "\n"
                    + JSON.toJSONString(request.getMembers()));
            MixedTeamRecord existing = mixedTeamStore.find(teamId);
            if (existing != null) {
                requireMixedOwner(existing, request.getChatterId());
                if (!payloadHash.equals(existing.getPayloadHash())) {
                    throw new TeamCommandException("IDEMPOTENCY_CONFLICT",
                            "requestId已被不同的Team创建请求使用");
                }
                return toTeam(existing);
            }
            String homeInstanceId = getHome(request.getChatterId()).getHomeCmdProxyInstanceId();
            if (StringUtils.isBlank(homeInstanceId)) {
                throw new TeamCommandException("HOME_INSTANCE_REQUIRED", "请先选择本机ACP设备");
            }

            participants = new LinkedHashMap<>();
            List<MixedTeamRecord.Member> members = new ArrayList<>();
            Set<String> memberIds = new HashSet<>();
            Set<String> selectedSources = new HashSet<>();
            boolean containsHome = false;
            for (int index = 0; index < request.getMembers().size(); index++) {
                TeamCreateMemberRequest selected = request.getMembers().get(index);
                DiscoveryRegistration registration = requireMixedRegistration(
                        request.getChatterId(), selected);
                TeamMemberSourceDTO source = requireDiscoveredSource(
                        registration, request.getChatterId(), selected);
                String sourceIdentity = registration.discovery.getCmdProxyInstanceId()
                        + "\n" + source.getSourceGroupId() + "\n" + source.getSourceRobotId();
                if (!selectedSources.add(sourceIdentity)) {
                    throw new TeamCommandException("DUPLICATE_TEAM_MEMBER",
                            "同一ACP来源不能重复加入一支Team");
                }
                participants.put(registration.discovery.getCmdProxyInstanceId(), registration);
                containsHome |= homeInstanceId.equals(registration.discovery.getCmdProxyInstanceId());
                MixedTeamRecord.Member member = new MixedTeamRecord.Member();
                member.setTeamMemberId(StringUtils.defaultIfBlank(
                        StringUtils.trim(selected.getTeamMemberId()),
                        stableUuid("team-member:" + request.getRequestId()
                                + ":" + index + ":"
                                + registration.discovery.getCmdProxyInstanceId()
                                + ":" + source.getSourceGroupId())));
                requireUniqueMemberId(memberIds, member.getTeamMemberId());
                member.setAcpClientId("team-acp-" + member.getTeamMemberId());
                member.setParticipantInstanceId(registration.discovery.getCmdProxyInstanceId());
                member.setTransportGroup(registration.discovery.getTransportGroup());
                member.setSourceRobotId(source.getSourceRobotId());
                member.setSourceGroupId(source.getSourceGroupId());
                member.setDisplayName(StringUtils.defaultIfBlank(
                        source.getDisplayName(), source.getRobotName()));
                member.setAvatar(source.getAvatar());
                member.setTeamRemark(StringUtils.defaultString(selected.getRemark()));
                member.setRemark(StringUtils.isNotBlank(
                        StringUtils.trim(member.getTeamRemark()))
                        ? member.getTeamRemark()
                        : StringUtils.defaultString(source.getRemark()));
                member.setOrder(index);
                member.setState("STARTING");
                members.add(member);
            }
            if (!containsHome) {
                throw new TeamCommandException("REMOTE_ONLY_TEAM",
                        "跨设备Team必须至少包含一位本机成员");
            }
            validateCaptainMembership(request, memberIds);

            record = new MixedTeamRecord();
            record.setTeamId(teamId);
            record.setOwnerChatterId(request.getChatterId());
            record.setName(request.getName());
            record.setMode(request.getMode());
            record.setCaptainTeamMemberId(request.getCaptainTeamMemberId());
            record.setState("CREATING");
            record.setRequestId(request.getRequestId());
            record.setPayloadHash(payloadHash);
            record.setHomeInstanceId(homeInstanceId);
            record.setMembers(members);
            for (DiscoveryRegistration registration : participants.values()) {
                MixedTeamRecord.Participant participant = new MixedTeamRecord.Participant();
                participant.setInstanceId(registration.discovery.getCmdProxyInstanceId());
                participant.setTransportGroup(registration.discovery.getTransportGroup());
                participant.setState("CREATING");
                participant.setRequestId(stableUuid("fragment:" + request.getRequestId()
                        + ":" + participant.getInstanceId()));
                participant.setMemberIds(members.stream()
                        .filter(member -> participant.getInstanceId().equals(
                                member.getParticipantInstanceId()))
                        .map(MixedTeamRecord.Member::getTeamMemberId)
                        .collect(Collectors.toList()));
                record.getParticipants().add(participant);
            }

            // 必须先保存全局 placement，之后才能向任何 participant 发 RPC。
            mixedTeamStore.save(record);
        }
        List<MixedTeamRecord.Participant> accepted = new ArrayList<>();
        MixedTeamRecord.Participant creatingParticipant = null;
        try {
            for (MixedTeamRecord.Participant participant : record.getParticipants()) {
                creatingParticipant = participant;
                invoke(participants.get(participant.getInstanceId()), "acpTeamCreate",
                        mixedCreatePayload(record, participant));
                accepted.add(participant);
            }
            MixedTeamRecord latest = mixedTeamStore.find(record.getTeamId());
            if (latest == null) {
                throw new TeamCommandException("TEAM_CREATE_FAILED",
                        "跨设备Team创建失败并已清理");
            }
            TeamDTO team = toTeam(latest);
            teamSnapshotSolution.upsert(team);
            return team;
        } catch (TeamCommandException createFailure) {
            synchronized (ownerLock) {
                MixedTeamRecord latest = mixedTeamStore.find(record.getTeamId());
                if (latest != null && creatingParticipant != null
                        && !accepted.contains(creatingParticipant)) {
                    MixedTeamRecord.Participant failed = findParticipant(
                            latest, creatingParticipant.getInstanceId());
                    if (failed != null) {
                        failed.setState("FAILED");
                        failed.setLastError(createFailure.getMessage());
                    }
                }
                if (latest != null) {
                    latest.setLastError(createFailure.getMessage());
                    mixedTeamStore.save(latest);
                }
            }
            boolean cleanupPending = compensateCreate(record, participants, accepted);
            MixedTeamRecord failedRecord;
            boolean alreadyCleaned;
            synchronized (ownerLock) {
                failedRecord = mixedTeamStore.find(record.getTeamId());
                alreadyCleaned = failedRecord == null;
                if (!alreadyCleaned) {
                    failedRecord.setState(cleanupPending ? "PENDING_CLEANUP" : "FAILED");
                    mixedTeamStore.save(failedRecord);
                }
            }
            if (alreadyCleaned) {
                teamSnapshotSolution.delete(record.getTeamId());
                teamRobotProjectionSolution.delete(record.getTeamId());
            } else {
                teamSnapshotSolution.upsert(toTeam(failedRecord));
            }
            throw createFailure;
        }
    }

    private DiscoveryRegistration requireMixedRegistration(String ownerChatterId,
                                                            TeamCreateMemberRequest selected) {
        DiscoveryRegistration registration = discoveries.get(selected.getCmdProxyInstanceId());
        if (registration == null
                || !selected.getTransportGroup().equals(
                        registration.discovery.getTransportGroup())
                || !isActive(registration) || !registration.transportReachable
                || !registration.discovery.isBusinessCommandsReady()) {
            throw new TeamCommandException("TEAM_NOT_READY", "所选ACP设备当前不可用");
        }
        if (registration.discovery.getCapabilities() == null
                || !registration.discovery.getCapabilities().isMixedTeamFragment()
                || !registration.discovery.getCapabilities().isMixedTeamTalkToDeliver()) {
            throw new TeamCommandException("MIXED_TEAM_UNSUPPORTED",
                    "所选ACP设备尚未支持跨设备Team");
        }
        ensureCommandReady(registration, "acpTeamCreate");
        return registration;
    }

    private JSONObject mixedCreatePayload(MixedTeamRecord record,
                                          MixedTeamRecord.Participant participant) {
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("requestId", participant.getRequestId());
        payload.put("globalRequestId", record.getRequestId());
        payload.put("teamId", record.getTeamId());
        payload.put("ownerChatterId", record.getOwnerChatterId());
        payload.put("name", record.getName());
        payload.put("mode", effectiveMode(record.getMode()));
        if (StringUtils.isNotBlank(record.getCaptainTeamMemberId())) {
            payload.put("captainTeamMemberId", record.getCaptainTeamMemberId());
        }
        payload.put("mixedPlacement", true);
        JSONArray localMembers = new JSONArray();
        JSONArray roster = new JSONArray();
        for (MixedTeamRecord.Member member : record.getMembers()) {
            JSONObject contact = new JSONObject();
            contact.put("teamMemberId", member.getTeamMemberId());
            contact.put("acpClientId", member.getAcpClientId());
            contact.put("displayName", member.getDisplayName());
            contact.put("remark", StringUtils.defaultString(member.getRemark()));
            contact.put("order", member.getOrder());
            roster.add(contact);
            if (participant.getInstanceId().equals(member.getParticipantInstanceId())) {
                JSONObject local = new JSONObject();
                local.putAll(contact);
                local.put("remark", StringUtils.defaultString(member.getTeamRemark()));
                local.put("sourceRobotId", member.getSourceRobotId());
                local.put("sourceGroupId", member.getSourceGroupId());
                localMembers.add(local);
            }
        }
        payload.put("members", localMembers);
        payload.put("roster", roster);
        return payload;
    }

    private boolean compensateCreate(MixedTeamRecord record,
                                     Map<String, DiscoveryRegistration> registrations,
                                     List<MixedTeamRecord.Participant> accepted) {
        boolean pending = false;
        for (MixedTeamRecord.Participant participant : accepted) {
            JSONObject payload = new JSONObject();
            payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
            payload.put("requestId", stableUuid("compensate:" + record.getRequestId()
                    + ":" + participant.getInstanceId()));
            payload.put("ownerChatterId", record.getOwnerChatterId());
            payload.put("teamId", record.getTeamId());
            String nextState;
            String lastError = null;
            try {
                DiscoveryRegistration registration = registrations.get(
                        participant.getInstanceId());
                if (registration == null || !isActive(registration)
                        || !registration.transportReachable) {
                    throw new TeamCommandException("TEAM_NOT_READY",
                            "participant当前不可达");
                }
                invoke(registration, "acpTeamDelete", payload);
                nextState = "DELETING";
                pending = true;
            } catch (TeamCommandException cleanupFailure) {
                if ("NOT_FOUND".equals(cleanupFailure.getCode())) {
                    nextState = "DELETED";
                } else {
                    nextState = "DELETING";
                    lastError = cleanupFailure.getMessage();
                    pending = true;
                }
            }
            synchronized (ownerResolutionLocks.computeIfAbsent(
                    record.getOwnerChatterId(), key -> new Object())) {
                MixedTeamRecord latest = mixedTeamStore.find(record.getTeamId());
                if (latest != null) {
                    MixedTeamRecord.Participant current = findParticipant(
                            latest, participant.getInstanceId());
                    if (current != null) {
                        current.setState(nextState);
                        current.setLastError(lastError);
                    }
                    mixedTeamStore.save(latest);
                }
            }
        }
        return pending;
    }

    private MixedTeamRecord.Participant findParticipant(MixedTeamRecord record,
                                                        String instanceId) {
        if (record == null || record.getParticipants() == null
                || StringUtils.isBlank(instanceId)) {
            return null;
        }
        return record.getParticipants().stream()
                .filter(participant -> instanceId.equals(participant.getInstanceId()))
                .findFirst().orElse(null);
    }

    public List<TeamDTO> list(String ownerChatterId) {
        List<MixedTeamRecord> mixedRecords = mixedTeamStore.listByOwner(ownerChatterId);
        List<TeamDTO> teams = new ArrayList<>();
        try {
            DiscoveryRegistration registration = resolveRegistration(ownerChatterId, true, true);
            JSONObject payload = new JSONObject();
            payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
            payload.put("ownerChatterId", ownerChatterId);
            teams.addAll(parseTeams(invoke(registration, "acpTeamList", payload)));
            teams.forEach(team -> rememberTeamInstance(team, registration));
        } catch (TeamCommandException homeFailure) {
            if (mixedRecords.isEmpty()) {
                throw homeFailure;
            }
            log.warn("home实例不可用，使用持久化Mixed Team列表, owner={}, code={}",
                    ownerChatterId, homeFailure.getCode());
        }
        Set<String> mixedTeamIds = mixedRecords.stream()
                .map(MixedTeamRecord::getTeamId)
                .collect(Collectors.toSet());
        teams.removeIf(team -> mixedTeamIds.contains(team.getTeamId()));
        appendMixedTeams(mixedRecords, teams);
        teamSnapshotSolution.replaceAll(ownerChatterId, teams);
        if (!teams.isEmpty()) {
            teamRobotProjectionSolution.syncAll(ownerChatterId, teams);
        }
        return teams;
    }

    /**
     * Starweave already owns its local Team fragment. Return only the coordinator-owned
     * mixed projection so a gateway list callback never synchronously calls back into the
     * same home cmd-proxy transport.
     */
    public List<TeamDTO> listMixedForStarweave(String ownerChatterId) {
        List<TeamDTO> teams = new ArrayList<>();
        appendMixedTeams(mixedTeamStore.listByOwner(ownerChatterId), teams);
        return teams;
    }

    private void appendMixedTeams(List<MixedTeamRecord> mixedRecords,
                                  List<TeamDTO> teams) {
        for (MixedTeamRecord record : mixedRecords) {
            if (!"DELETED".equals(record.getState())) {
                boolean allParticipantsReachable = record.getParticipants().stream()
                        .allMatch(this::isParticipantReachable);
                if ("READY".equals(record.getState()) && !allParticipantsReachable) {
                    record.setState("RECOVERING");
                    mixedTeamStore.save(record);
                } else if ("RECOVERING".equals(record.getState())
                        && allParticipantsReachable
                        && record.getParticipants().stream().allMatch(participant ->
                        "READY".equals(participant.getState()))) {
                    record.setState("READY");
                    mixedTeamStore.save(record);
                }
                teams.add(toTeam(record));
            }
        }
    }

    private boolean isParticipantReachable(MixedTeamRecord.Participant participant) {
        DiscoveryRegistration registration = discoveries.get(participant.getInstanceId());
        return registration != null && isActive(registration)
                && registration.transportReachable
                && participant.getTransportGroup().equals(
                registration.discovery.getTransportGroup());
    }

    public TeamDTO get(String ownerChatterId, String teamId) {
        MixedTeamRecord mixed = mixedTeamStore.find(teamId);
        if (mixed != null) {
            requireMixedOwner(mixed, ownerChatterId);
            return toTeam(mixed);
        }
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
        MixedTeamRecord mixed = mixedTeamStore.find(teamId);
        if (mixed != null) {
            synchronized (ownerResolutionLocks.computeIfAbsent(
                    ownerChatterId, key -> new Object())) {
                mixed = mixedTeamStore.find(teamId);
                if (mixed == null) {
                    throw new TeamCommandException("NOT_FOUND", "Team不存在");
                }
                requireMixedOwner(mixed, ownerChatterId);
                mixed.setState("DELETING");
                mixedTeamStore.save(mixed);
            }
            return deleteMixed(mixed, requestId);
        }
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

    public List<String> cleanupStaleTeams(String ownerChatterId) {
        synchronized (ownerResolutionLocks.computeIfAbsent(
                ownerChatterId, key -> new Object())) {
            List<String> cleanedTeamIds = new ArrayList<>();
            for (MixedTeamRecord record : mixedTeamStore.listByOwner(ownerChatterId)) {
                if (!"CREATING".equals(record.getState())
                        && !"DELETING".equals(record.getState())) {
                    continue;
                }
                mixedTeamStore.remove(record.getTeamId());
                teamSnapshotSolution.delete(record.getTeamId());
                teamRobotProjectionSolution.delete(record.getTeamId());
                cleanedTeamIds.add(record.getTeamId());
                log.warn("强制清理残留Fast Team, teamId={}, ownerChatterId={}, state={}",
                        record.getTeamId(), ownerChatterId, record.getState());
            }
            return cleanedTeamIds;
        }
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
        List<TeamMemberDTO> candidates = new ArrayList<>();
        TeamHomeDTO home = getHome(ownerChatterId);
        discoveries.values().stream()
                .sorted(Comparator.comparing(registration ->
                        registration.discovery.getCmdProxyInstanceId()))
                .forEach(registration -> appendCandidates(
                        candidates, registration, ownerChatterId,
                        home.getHomeCmdProxyInstanceId(), home.isSelectionRequired()));
        return candidates;
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
        MixedTeamRecord mixed = mixedTeamStore.find(teamId);
        if (mixed != null && StringUtils.isNotBlank(teamMemberId)) {
            requireMixedOwner(mixed, ownerChatterId);
            MixedTeamRecord.Member member = mixed.getMembers().stream()
                    .filter(candidate -> teamMemberId.equals(candidate.getTeamMemberId()))
                    .findFirst()
                    .orElseThrow(() -> new TeamCommandException(
                            "NOT_FOUND", "Team成员不存在"));
            if (!"READY".equals(mixed.getState())
                    && !"acpTeamCancel".equals(command)) {
                throw new TeamCommandException("TEAM_NOT_READY", "跨设备Team当前不可操作");
            }
            DiscoveryRegistration registration = discoveries.get(
                    member.getParticipantInstanceId());
            if (registration == null || !isActive(registration)
                    || !registration.transportReachable) {
                mixed.setState("RECOVERING");
                mixedTeamStore.save(mixed);
                throw new TeamCommandException("TEAM_NOT_READY", "远程Team成员正在等待恢复");
            }
            Map<String, String> result = timeoutMillis > 0L
                    ? invoke(registration, command, payload, timeoutMillis)
                    : invoke(registration, command, payload);
            JSONObject data = JSON.parseObject(requiredData(result));
            reconcileMixedMemberSession(mixed, teamMemberId, data);
            return data;
        }
        DiscoveryRegistration registration = resolveRegistration(ownerChatterId, false, true);
        Map<String, String> result = timeoutMillis > 0L
                ? invoke(registration, command, payload, timeoutMillis)
                : invoke(registration, command, payload);
        return JSON.parseObject(requiredData(result));
    }

    private TeamDTO deleteMixed(MixedTeamRecord record, String requestId) {
        boolean cleanupFailed = false;
        for (MixedTeamRecord.Participant participant : record.getParticipants()) {
            if ("DELETED".equals(participant.getState())) {
                continue;
            }
            JSONObject payload = new JSONObject();
            payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
            payload.put("requestId", stableUuid("delete:" + requestId
                    + ":" + participant.getInstanceId()));
            payload.put("ownerChatterId", record.getOwnerChatterId());
            payload.put("teamId", record.getTeamId());
            DiscoveryRegistration registration = discoveries.get(participant.getInstanceId());
            String nextState;
            String lastError = null;
            try {
                if (registration == null || !isActive(registration)
                        || !registration.transportReachable) {
                    throw new TeamCommandException("TEAM_NOT_READY", "participant当前不可达");
                }
                invoke(registration, "acpTeamDelete", payload);
                nextState = "DELETING";
            } catch (TeamCommandException failure) {
                if ("NOT_FOUND".equals(failure.getCode())) {
                    nextState = "DELETED";
                } else {
                    nextState = "DELETING";
                    lastError = failure.getMessage();
                    cleanupFailed = true;
                }
            }
            synchronized (ownerResolutionLocks.computeIfAbsent(
                    record.getOwnerChatterId(), key -> new Object())) {
                MixedTeamRecord latest = mixedTeamStore.find(record.getTeamId());
                if (latest != null) {
                    MixedTeamRecord.Participant current = findParticipant(
                            latest, participant.getInstanceId());
                    // TEAM_DELETED may arrive before the delete RPC response.
                    // Keep the terminal callback from being regressed to DELETING.
                    if (current != null && !"DELETED".equals(current.getState())) {
                        current.setState(nextState);
                        current.setLastError(lastError);
                    }
                    mixedTeamStore.save(latest);
                }
            }
        }
        MixedTeamRecord latest;
        synchronized (ownerResolutionLocks.computeIfAbsent(
                record.getOwnerChatterId(), key -> new Object())) {
            latest = mixedTeamStore.find(record.getTeamId());
            if (latest == null) {
                TeamDTO deleted = toTeam(record);
                deleted.setState("DELETED");
                deleted.setStatus("DELETED");
                return deleted;
            }
            if (latest.getParticipants().stream()
                    .allMatch(participant -> "DELETED".equals(participant.getState()))) {
                latest.setState("DELETED");
                TeamDTO deleted = toTeam(latest);
                mixedTeamStore.remove(latest.getTeamId());
                teamSnapshotSolution.delete(latest.getTeamId());
                teamRobotProjectionSolution.delete(latest.getTeamId());
                return deleted;
            }
            latest.setState(cleanupFailed ? "PENDING_CLEANUP" : "DELETING");
            mixedTeamStore.save(latest);
        }
        TeamDTO deleting = toTeam(latest);
        teamSnapshotSolution.upsert(deleting);
        return deleting;
    }

    private void requireMixedOwner(MixedTeamRecord record, String ownerChatterId) {
        if (!ownerChatterId.equals(record.getOwnerChatterId())) {
            throw new TeamCommandException("UNAUTHORIZED", "无权访问该Team");
        }
    }

    private TeamDTO toTeam(MixedTeamRecord record) {
        TeamDTO team = new TeamDTO();
        team.setTeamId(record.getTeamId());
        team.setOwnerChatterId(record.getOwnerChatterId());
        team.setName(record.getName());
        team.setMode(effectiveMode(record.getMode()));
        team.setCaptainTeamMemberId(record.getCaptainTeamMemberId());
        team.setState(record.getState());
        team.setStatus(record.getState());
        team.setVersion(record.getUpdatedAt());
        team.setLastError(record.getLastError());
        List<TeamMemberDTO> members = new ArrayList<>();
        for (MixedTeamRecord.Member source : record.getMembers()) {
            TeamMemberDTO member = new TeamMemberDTO();
            member.setCmdProxyInstanceId(source.getParticipantInstanceId());
            member.setTransportGroup(source.getTransportGroup());
            member.setOwnerChatterId(record.getOwnerChatterId());
            member.setTeamMemberId(source.getTeamMemberId());
            member.setAcpClientId(source.getAcpClientId());
            member.setRobotId(source.getAcpClientId());
            member.setSourceRobotId(source.getSourceRobotId());
            member.setSourceGroupId(source.getSourceGroupId());
            member.setDisplayName(source.getDisplayName());
            member.setAvatar(source.getAvatar());
            member.setRemark(source.getRemark());
            member.setOrder(source.getOrder());
            member.setState(source.getState());
            member.setStatus(source.getState());
            member.setSessionId(source.getSessionId());
            member.setSourceType(source.getParticipantInstanceId()
                    .equals(record.getHomeInstanceId()) ? "HOME" : "REMOTE");
            members.add(member);
        }
        team.setMembers(members);
        return team;
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

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256不可用", impossible);
        }
    }

    private void registerEventCallback(TeamDiscoveryDTO discovery) {
        if (StringUtils.isBlank(discovery.getEventCommand())) {
            return;
        }
        if (registeredEventGroups.add(discovery.getTransportGroup())) {
            teamCommandTransport.registerEventCallback(
                    discovery.getEventCommand(), discovery.getTransportGroup(),
                    resultMap -> handleEvent(discovery, resultMap));
            teamCommandTransport.registerEventCallback(
                    "starweaveTeamGateway", discovery.getTransportGroup(),
                    this::starweaveCallbackLane,
                    resultMap -> handleStarweaveGateway(discovery, resultMap));
        }
        JSONObject ready = new JSONObject(true);
        ready.put("instanceId", discovery.getCmdProxyInstanceId());
        ready.put("ownerChatterId", "starweave-" + discovery.getCmdProxyInstanceId());
        try {
            teamCommandTransport.send("starweaveTeamGatewayReady",
                    discovery.getTransportGroup(), ready.toJSONString());
        } catch (RuntimeException unsupported) {
            log.debug("cmd-proxy尚未启用Starweave Team协调桥, instanceId={}",
                    discovery.getCmdProxyInstanceId());
        }
    }

    TeamCommandTransport.CallbackLane starweaveCallbackLane(
            Map<String, String> request) {
        if (request == null) {
            return TeamCommandTransport.CallbackLane.GATEWAY_MUTATION;
        }
        String operation = request.get("operation");
        if ("list".equals(operation) || "sources".equals(operation)) {
            return TeamCommandTransport.CallbackLane.GATEWAY_QUERY;
        }
        if ("member".equals(operation)) {
            try {
                JSONObject payload = JSON.parseObject(request.get("payload"));
                String action = payload == null ? null : payload.getString("action");
                if ("status".equals(action) || "context".equals(action)
                        || "history".equals(action) || "listSessions".equals(action)) {
                    return TeamCommandTransport.CallbackLane.GATEWAY_QUERY;
                }
            } catch (RuntimeException malformed) {
                log.debug("Starweave gateway payload lane detection failed, requestId={}",
                        request.get("requestId"), malformed);
            }
        }
        return TeamCommandTransport.CallbackLane.GATEWAY_MUTATION;
    }

    private void handleStarweaveGateway(TeamDiscoveryDTO callbackDiscovery,
                                        Map<String, String> request) {
        JSONObject response = new JSONObject(true);
        String requestId = request == null ? null : request.get("requestId");
        response.put("requestId", requestId);
        try {
            if (request == null || StringUtils.isBlank(requestId)) {
                throw new TeamCommandException("VALIDATION_ERROR", "requestId不能为空");
            }
            String instanceId = request.get("instanceId");
            String ownerChatterId = request.get("ownerChatterId");
            DiscoveryRegistration authenticated = discoveries.get(
                    callbackDiscovery.getCmdProxyInstanceId());
            if (authenticated == null || !isActive(authenticated)
                    || !authenticated.transportReachable
                    || !callbackDiscovery.getTransportGroup().equals(
                    authenticated.discovery.getTransportGroup())
                    || !callbackDiscovery.getCmdProxyInstanceId().equals(instanceId)
                    || !isStarweaveHome(authenticated.discovery, ownerChatterId)) {
                throw new TeamCommandException("UNAUTHORIZED",
                        "Starweave owner与实际callback transport不匹配");
            }
            synchronized (ownerResolutionLocks.computeIfAbsent(
                    ownerChatterId, key -> new Object())) {
                String bound = loadBinding(ownerChatterId);
                if (StringUtils.isNotBlank(bound) && !instanceId.equals(bound)) {
                    throw new TeamCommandException("HOME_INSTANCE_CONFLICT",
                            "Starweave owner已绑定其它cmd-proxy实例");
                }
                bind(ownerChatterId, authenticated);
            }
            JSONObject payload = JSON.parseObject(request.get("payload"));
            JSONObject data = executeStarweaveOperation(ownerChatterId,
                    request.get("operation"), payload == null
                    ? new JSONObject(true) : payload);
            response.put("accepted", true);
            response.put("code", "OK");
            response.put("message", "OK");
            response.put("data", data);
        } catch (TeamCommandException rejected) {
            response.put("accepted", false);
            response.put("code", rejected.getCode());
            response.put("message", rejected.getMessage());
        } catch (RuntimeException failure) {
            response.put("accepted", false);
            response.put("code", "INTERNAL_ERROR");
            response.put("message", StringUtils.defaultIfBlank(
                    failure.getMessage(), failure.getClass().getSimpleName()));
        }
        try {
            teamCommandTransport.send("starweaveTeamGatewayResult",
                    callbackDiscovery.getTransportGroup(), response.toJSONString());
        } catch (RuntimeException failure) {
            log.warn("Starweave Team协调结果回传失败, instanceId={}, requestId={}",
                    callbackDiscovery.getCmdProxyInstanceId(), requestId, failure);
        }
    }

    private JSONObject executeStarweaveOperation(String ownerChatterId,
                                                  String operation,
                                                  JSONObject payload) {
        JSONObject data = new JSONObject(true);
        if ("sources".equals(operation)) {
            JSONArray sources = new JSONArray();
            for (TeamMemberDTO candidate : listCandidates(ownerChatterId)) {
                if (!"AVAILABLE".equals(candidate.getStatus())) continue;
                JSONObject source = (JSONObject) JSON.toJSON(candidate);
                boolean shared = candidate.getSourceGroupId() != null
                        && candidate.getSourceGroupId().startsWith("team-shared-");
                source.put("sourceLabel", "HOME".equals(candidate.getSourceType())
                        ? (shared ? "本环境 · MolaChat/共享" : "本环境 · Starweave")
                        : "远程环境 · 共享 Agent");
                source.put("coordinated", true);
                sources.add(source);
            }
            data.put("sources", sources);
            return data;
        }
        if ("list".equals(operation)) {
            data.put("teams", listMixedForStarweave(ownerChatterId));
            return data;
        }
        if ("create".equals(operation)) {
            TeamCreateRequest createRequest = payload.toJavaObject(TeamCreateRequest.class);
            createRequest.setChatterId(ownerChatterId);
            data.put("team", create(createRequest));
            return data;
        }
        if ("delete".equals(operation)) {
            data.put("team", delete(ownerChatterId, payload.getString("teamId"),
                    payload.getString("requestId"), payload.getLong("expectedVersion")));
            return data;
        }
        if (!"member".equals(operation)) {
            throw new TeamCommandException("VALIDATION_ERROR",
                    "不支持的Starweave Team操作");
        }
        String teamId = payload.getString("teamId");
        String memberId = payload.getString("teamMemberId");
        String clientId = payload.getString("acpClientId");
        String action = payload.getString("action");
        if ("send".equals(action)) {
            List<Map<String, String>> files = new ArrayList<>();
            JSONArray values = payload.getJSONArray("files");
            if (values != null) {
                for (int i = 0; i < values.size(); i++) {
                    JSONObject value = values.getJSONObject(i);
                    Map<String, String> file = new HashMap<>();
                    for (String key : value.keySet()) file.put(key, value.getString(key));
                    files.add(file);
                }
            }
            return send(ownerChatterId, teamId, memberId, clientId,
                    payload.getString("message"), files, payload.getString("requestId"));
        }
        if ("cancel".equals(action)) return cancel(
                ownerChatterId, teamId, memberId, clientId);
        if ("newSession".equals(action)) return newSession(
                ownerChatterId, teamId, memberId, clientId);
        if ("memoryDream".equals(action)) return memoryDream(
                ownerChatterId, teamId, memberId, clientId);
        if ("listSessions".equals(action)) return listSessions(
                ownerChatterId, teamId, memberId, clientId, payload.getInteger("limit"));
        if ("history".equals(action)) return getSessionHistory(
                ownerChatterId, teamId, memberId, clientId);
        if ("restore".equals(action)) return restoreSession(
                ownerChatterId, teamId, memberId, clientId, payload.getString("sessionId"));
        if ("status".equals(action)) return getStatus(
                ownerChatterId, teamId, memberId, clientId);
        if ("context".equals(action)) return getContextUsage(
                ownerChatterId, teamId, memberId, clientId);
        throw new TeamCommandException("VALIDATION_ERROR",
                "不支持的Starweave Team成员操作");
    }

    @PreDestroy
    public void closeStarweaveProjection() {
        starweaveProjectionExecutor.shutdownNow();
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
        MixedTeamRecord mixed = mixedTeamStore.find(teamId);
        if (mixed != null) {
            MixedTeamRecord.Participant source = findParticipant(
                    mixed, discovery.getCmdProxyInstanceId());
            if (source == null || !discovery.getTransportGroup()
                    .equals(source.getTransportGroup())) {
                log.warn("忽略未知Mixed Team participant事件, teamId={}, instanceId={}",
                        mixed.getTeamId(), discovery.getCmdProxyInstanceId());
                return;
            }
            forwardStarweaveEvent(mixed, resultMap);
            if ("TALK_TO_ROUTE_REQUEST".equals(resultMap.get("type"))) {
                routeMixedTalkTo(mixed, source, resultMap);
                return;
            }
            if ("TEAM_CREATE_FAILED".equals(resultMap.get("type"))) {
                handleMixedCreateFailed(discovery, resultMap, mixed);
                return;
            }
            synchronized (ownerResolutionLocks.computeIfAbsent(
                    mixed.getOwnerChatterId(), key -> new Object())) {
                MixedTeamRecord latest = mixedTeamStore.find(teamId);
                if (latest != null) {
                    handleMixedEvent(discovery, resultMap, latest);
                }
            }
            return;
        }
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

    private void forwardStarweaveEvent(MixedTeamRecord record,
                                       Map<String, String> resultMap) {
        if (record == null || record.getOwnerChatterId() == null
                || !record.getOwnerChatterId().equals(
                "starweave-" + record.getHomeInstanceId())) {
            return;
        }
        MixedTeamRecord.Participant home = findParticipant(
                record, record.getHomeInstanceId());
        if (home == null) return;
        JSONObject event = new JSONObject(true);
        event.putAll(resultMap);
        if (StringUtils.isNotBlank(resultMap.get("data"))) {
            try {
                event.put("data", JSON.parse(resultMap.get("data")));
            } catch (RuntimeException ignored) {
                event.put("data", resultMap.get("data"));
            }
        }
        JSONObject wrapper = new JSONObject(true);
        wrapper.put("ownerChatterId", record.getOwnerChatterId());
        wrapper.put("event", event);
        try {
            starweaveProjectionExecutor.execute(() -> {
                try {
                    teamCommandTransport.send("starweaveTeamGatewayEvent",
                            home.getTransportGroup(), wrapper.toJSONString());
                } catch (RuntimeException failure) {
                    log.warn("Starweave Team事件投影失败, teamId={}, homeInstanceId={}",
                            record.getTeamId(), record.getHomeInstanceId(), failure);
                }
            });
        } catch (RejectedExecutionException rejected) {
            log.warn("Starweave Team事件投影队列已满, teamId={}, homeInstanceId={}",
                    record.getTeamId(), record.getHomeInstanceId());
        }
    }

    private void handleMixedEvent(TeamDiscoveryDTO discovery, Map<String, String> resultMap,
                                  MixedTeamRecord record) {
        MixedTeamRecord.Participant participant = record.getParticipants().stream()
                .filter(candidate -> discovery.getCmdProxyInstanceId()
                        .equals(candidate.getInstanceId())
                        && discovery.getTransportGroup().equals(candidate.getTransportGroup()))
                .findFirst()
                .orElse(null);
        if (participant == null) {
            log.warn("忽略未知Mixed Team participant事件, teamId={}, instanceId={}",
                    record.getTeamId(), discovery.getCmdProxyInstanceId());
            return;
        }
        String type = resultMap.get("type");
        if ("TEAM_READY".equals(type) || "TEAM_RECOVERED".equals(type)) {
            participant.setState("READY");
            updateMixedMembers(record, participant, resultMap.get("data"));
            if (record.getParticipants().stream()
                    .allMatch(item -> "READY".equals(item.getState()))) {
                record.setState("READY");
            }
            persistMixedProjection(record);
            return;
        }
        if ("MEMBER_STATE_CHANGED".equals(type)) {
            updateMixedMembers(record, participant, resultMap.get("data"));
            persistMixedProjection(record);
            return;
        }
        if ("MEMBER_SESSION_CHANGED".equals(type)) {
            updateMixedMemberSession(record, participant,
                    resultMap.get("teamMemberId"), resultMap.get("data"));
            persistMixedProjection(record);
            return;
        }
        if ("TEAM_DELETED".equals(type)) {
            participant.setState("DELETED");
            if (record.getParticipants().stream()
                    .allMatch(item -> "DELETED".equals(item.getState()))) {
                mixedTeamStore.remove(record.getTeamId());
                teamSnapshotSolution.delete(record.getTeamId());
                teamRobotProjectionSolution.delete(record.getTeamId());
            } else if (StringUtils.isNotBlank(record.getLastError())
                    && record.getParticipants().stream().allMatch(item ->
                    "DELETED".equals(item.getState()) || "FAILED".equals(item.getState()))) {
                record.setState("FAILED");
                persistMixedProjection(record);
            } else {
                record.setState("PENDING_CLEANUP");
                persistMixedProjection(record);
            }
            return;
        }
        teamEventSolution.handle(resultMap);
    }

    private void handleMixedCreateFailed(TeamDiscoveryDTO discovery,
                                         Map<String, String> resultMap,
                                         MixedTeamRecord snapshot) {
        Map<String, DiscoveryRegistration> registrations = new LinkedHashMap<>();
        List<MixedTeamRecord.Participant> accepted;
        MixedTeamRecord failedRecord;
        synchronized (ownerResolutionLocks.computeIfAbsent(
                snapshot.getOwnerChatterId(), key -> new Object())) {
            failedRecord = mixedTeamStore.find(snapshot.getTeamId());
            if (failedRecord == null) {
                return;
            }
            MixedTeamRecord.Participant failed = findParticipant(
                    failedRecord, discovery.getCmdProxyInstanceId());
            if (failed == null) {
                return;
            }
            failed.setState("FAILED");
            failed.setLastError(resultMap.get("message"));
            failedRecord.setLastError(resultMap.get("message"));
            for (MixedTeamRecord.Participant item : failedRecord.getParticipants()) {
                DiscoveryRegistration registration = discoveries.get(item.getInstanceId());
                if (registration != null) {
                    registrations.put(item.getInstanceId(), registration);
                }
            }
            accepted = failedRecord.getParticipants().stream()
                    .filter(item -> item != failed && !"DELETED".equals(item.getState()))
                    .collect(Collectors.toList());
            mixedTeamStore.save(failedRecord);
        }

        boolean pending = compensateCreate(failedRecord, registrations, accepted);
        synchronized (ownerResolutionLocks.computeIfAbsent(
                snapshot.getOwnerChatterId(), key -> new Object())) {
            MixedTeamRecord latest = mixedTeamStore.find(snapshot.getTeamId());
            if (latest != null) {
                latest.setState(pending ? "PENDING_CLEANUP" : "FAILED");
                persistMixedProjection(latest);
            }
        }
    }

    private void updateMixedMembers(MixedTeamRecord record,
                                    MixedTeamRecord.Participant participant,
                                    String data) {
        JSONObject wrapper = StringUtils.isBlank(data) ? null : JSON.parseObject(data);
        JSONObject team = wrapper == null ? null : wrapper.getJSONObject("team");
        JSONArray members = team == null ? null : team.getJSONArray("members");
        if (members == null) {
            return;
        }
        for (Object value : members) {
            JSONObject memberState = (JSONObject) value;
            MixedTeamRecord.Member member = record.getMembers().stream()
                    .filter(candidate -> participant.getInstanceId().equals(
                            candidate.getParticipantInstanceId())
                            && candidate.getTeamMemberId().equals(
                            memberState.getString("teamMemberId")))
                    .findFirst().orElse(null);
            if (member != null) {
                member.setState(StringUtils.defaultIfBlank(
                        memberState.getString("status"), memberState.getString("state")));
                if (StringUtils.isBlank(member.getState())) {
                    member.setState("READY");
                }
                if (StringUtils.isNotBlank(memberState.getString("sessionId"))) {
                    member.setSessionId(memberState.getString("sessionId"));
                }
            }
        }
    }

    private void updateMixedMemberSession(MixedTeamRecord record,
                                          MixedTeamRecord.Participant participant,
                                          String teamMemberId, String data) {
        if (StringUtils.isBlank(teamMemberId) || StringUtils.isBlank(data)) {
            return;
        }
        JSONObject value = JSON.parseObject(data);
        String sessionId = value == null ? null : value.getString("newSessionId");
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        record.getMembers().stream()
                .filter(member -> participant.getInstanceId().equals(
                        member.getParticipantInstanceId())
                        && teamMemberId.equals(member.getTeamMemberId()))
                .findFirst()
                .ifPresent(member -> member.setSessionId(sessionId));
    }

    private void reconcileMixedMemberSession(MixedTeamRecord snapshot,
                                             String teamMemberId, JSONObject data) {
        String sessionId = data == null ? null : data.getString("sessionId");
        if (StringUtils.isBlank(sessionId)) {
            return;
        }
        synchronized (ownerResolutionLocks.computeIfAbsent(
                snapshot.getOwnerChatterId(), key -> new Object())) {
            MixedTeamRecord latest = mixedTeamStore.find(snapshot.getTeamId());
            if (latest == null || !snapshot.getOwnerChatterId().equals(
                    latest.getOwnerChatterId())) {
                return;
            }
            latest.getMembers().stream()
                    .filter(member -> teamMemberId.equals(member.getTeamMemberId()))
                    .findFirst()
                    .ifPresent(member -> member.setSessionId(sessionId));
            persistMixedProjection(latest);
        }
    }

    private void persistMixedProjection(MixedTeamRecord record) {
        mixedTeamStore.save(record);
        TeamDTO team = toTeam(record);
        teamSnapshotSolution.upsert(team);
        teamRobotProjectionSolution.sync(team);
    }

    private void routeMixedTalkTo(MixedTeamRecord record,
                                  MixedTeamRecord.Participant senderParticipant,
                                  Map<String, String> resultMap) {
        if (!"READY".equals(record.getState())) {
            log.warn("拒绝非READY Mixed Team talkTo, teamId={}, state={}",
                    record.getTeamId(), record.getState());
            return;
        }
        JSONObject data = JSON.parseObject(resultMap.get("data"));
        if (data == null) {
            return;
        }
        String senderId = data.getString("senderTeamMemberId");
        String targetId = data.getString("targetTeamMemberId");
        Integer depth = data.getInteger("depth");
        Long createdAt = data.getLong("createdAt");
        Long expiresAt = data.getLong("expiresAt");
        long now = System.currentTimeMillis();
        if (StringUtils.isBlank(senderId) || StringUtils.isBlank(targetId)
                || StringUtils.isBlank(data.getString("messageId"))
                || depth == null || depth < 1 || depth > TALK_TO_MAX_DEPTH
                || createdAt == null || expiresAt == null || createdAt <= 0L
                || expiresAt <= createdAt
                || expiresAt - createdAt > TALK_TO_MAX_TTL_MILLIS
                || createdAt > now + TALK_TO_MAX_FUTURE_SKEW_MILLIS
                || now > expiresAt) {
            log.warn("拒绝字段不完整的Mixed Team talkTo, teamId={}", record.getTeamId());
            return;
        }
        MixedTeamRecord.Member sender = record.getMembers().stream()
                .filter(member -> senderId.equals(member.getTeamMemberId())
                        && senderParticipant.getInstanceId().equals(
                        member.getParticipantInstanceId()))
                .findFirst().orElse(null);
        MixedTeamRecord.Member target = record.getMembers().stream()
                .filter(member -> targetId.equals(member.getTeamMemberId()))
                .findFirst().orElse(null);
        MixedTeamRecord.Participant targetParticipant = target == null ? null
                : findParticipant(record, target.getParticipantInstanceId());
        boolean senderPlacementMatches = sender != null
                && senderParticipant.getTransportGroup().equals(sender.getTransportGroup())
                && senderParticipant.getMemberIds() != null
                && senderParticipant.getMemberIds().contains(senderId);
        boolean targetPlacementMatches = target != null && targetParticipant != null
                && targetParticipant.getTransportGroup().equals(target.getTransportGroup())
                && targetParticipant.getMemberIds() != null
                && targetParticipant.getMemberIds().contains(targetId);
        if (!senderPlacementMatches || !targetPlacementMatches) {
            log.warn("拒绝placement不匹配的Mixed Team talkTo, teamId={}, sender={}, target={}",
                    record.getTeamId(), senderId, targetId);
            return;
        }
        String mode;
        try {
            mode = effectiveMode(record.getMode());
        } catch (TeamCommandException invalidMode) {
            log.warn("拒绝模式无效的Mixed Team talkTo, teamId={}, mode={}",
                    record.getTeamId(), record.getMode());
            return;
        }
        if (senderId.equals(targetId)) {
            log.warn("拒绝Mixed Team成员向自己talkTo, teamId={}, member={}",
                    record.getTeamId(), senderId);
            return;
        }
        if (TEAM_MODE_CAPTAIN.equals(mode)) {
            String captainId = StringUtils.trimToNull(record.getCaptainTeamMemberId());
            boolean captainIsAuthoritative = captainId != null && record.getMembers().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(member -> captainId.equals(member.getTeamMemberId()));
            if (!captainIsAuthoritative
                    || (!captainId.equals(senderId) && !captainId.equals(targetId))) {
                log.warn("拒绝违反队长拓扑的Mixed Team talkTo, teamId={}, sender={}, target={}",
                        record.getTeamId(), senderId, targetId);
                return;
            }
        }
        DiscoveryRegistration targetRegistration = discoveries.get(
                target.getParticipantInstanceId());
        if (targetRegistration == null || !isActive(targetRegistration)
                || !targetRegistration.transportReachable
                || !targetParticipant.getTransportGroup().equals(
                targetRegistration.discovery.getTransportGroup())) {
            synchronized (ownerResolutionLocks.computeIfAbsent(
                    record.getOwnerChatterId(), key -> new Object())) {
                MixedTeamRecord latest = mixedTeamStore.find(record.getTeamId());
                if (latest != null) {
                    latest.setState("RECOVERING");
                    persistMixedProjection(latest);
                }
            }
            return;
        }
        JSONObject payload = new JSONObject();
        payload.put("schemaVersion", SUPPORTED_SCHEMA_VERSION);
        payload.put("requestId", stableUuid("talk-to:" + data.getString("messageId")));
        payload.put("messageId", data.getString("messageId"));
        payload.put("ownerChatterId", record.getOwnerChatterId());
        payload.put("teamId", record.getTeamId());
        payload.put("senderTeamMemberId", senderId);
        payload.put("targetTeamMemberId", targetId);
        payload.put("content", data.getString("content"));
        payload.put("depth", depth);
        payload.put("createdAt", createdAt);
        payload.put("expiresAt", expiresAt);
        try {
            invoke(targetRegistration, "acpTeamTalkToDeliver", payload);
        } catch (TeamCommandException failure) {
            log.warn("Mixed Team talkTo投递失败, teamId={}, target={}, code={}",
                    record.getTeamId(), targetId, failure.getCode());
        }
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
