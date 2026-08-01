package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TeamEventSolution {

    private static final int EVENT_DEDUP_CAPACITY = 2048;

    @Resource
    private TeamRobotProjectionSolution projectionSolution;

    @Resource
    private TeamMessageEventSolution messageEventSolution;

    @Resource
    private TeamTalkToEventSolution talkToEventSolution;

    @Resource
    private TeamSnapshotSolution teamSnapshotSolution;

    private final Map<String, Boolean> seenEventIds = Collections.synchronizedMap(
            new LinkedHashMap<String, Boolean>(EVENT_DEDUP_CAPACITY + 1, 1F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > EVENT_DEDUP_CAPACITY;
                }
            });
    private final Map<String, Long> latestTeamVersions = new ConcurrentHashMap<>();

    public void handle(Map<String, String> resultMap) {
        TeamEventDTO event = toEvent(resultMap);
        if (event == null || isDuplicate(event) || isStaleStateEvent(event)) {
            return;
        }
        if ("TEAM_READY".equals(event.getType())) {
            TeamDTO team = parseTeam(event.getData());
            teamSnapshotSolution.upsert(team);
            projectionSolution.sync(team);
        } else if ("TEAM_DELETE_ACCEPTED".equals(event.getType())
                || "TEAM_DELETE_FAILED".equals(event.getType())) {
            TeamDTO team = parseTeam(event.getData());
            teamSnapshotSolution.upsert(team);
            projectionSolution.sync(team);
        } else if ("MEMBER_STATE_CHANGED".equals(event.getType())) {
            TeamDTO team = parseTeam(event.getData());
            teamSnapshotSolution.upsert(team);
            projectionSolution.sync(team);
        } else if ("TEAM_CREATE_FAILED".equals(event.getType())) {
            TeamDTO team = parseTeam(event.getData());
            teamSnapshotSolution.upsert(team);
            log.warn("Fast Team创建失败, teamId={}, error={}",
                    team.getTeamId(), JSON.toJSONString(team.getLastError()));
        } else if ("TEAM_DELETED".equals(event.getType())) {
            teamSnapshotSolution.delete(event.getTeamId());
            projectionSolution.delete(event.getTeamId());
        } else if ("MESSAGE_CHUNK".equals(event.getType())
                || "MESSAGE_COMPLETE".equals(event.getType())
                || "MESSAGE_ERROR".equals(event.getType())) {
            messageEventSolution.handle(event);
        } else if ("TALK_TO_SEND".equals(event.getType())
                || "TALK_TO_RECEIVE".equals(event.getType())
                || "TALK_TO_QUEUED".equals(event.getType())
                || "TALK_TO_REJECTED".equals(event.getType())) {
            talkToEventSolution.handle(event);
        }
        log.info("Fast Team event received, teamId={}, memberId={}, type={}, eventSeq={}",
                event.getTeamId(), event.getTeamMemberId(), event.getType(), event.getEventSeq());
    }

    private TeamDTO parseTeam(String data) {
        JSONObject wrapper = JSON.parseObject(data);
        if (wrapper == null || wrapper.getJSONObject("team") == null) {
            throw new IllegalArgumentException("Fast Team event缺少team数据");
        }
        TeamDTO team = wrapper.getJSONObject("team").toJavaObject(TeamDTO.class);
        if (team.getLastError() == null && wrapper.get("error") != null) {
            team.setLastError(wrapper.get("error"));
        }
        return team;
    }

    private boolean isDuplicate(TeamEventDTO event) {
        synchronized (seenEventIds) {
            return seenEventIds.put(event.getEventId(), Boolean.TRUE) != null;
        }
    }

    private boolean isStaleStateEvent(TeamEventDTO event) {
        if (event.getTeamVersion() == null || !isStateEvent(event.getType())) {
            return false;
        }
        Long latest = latestTeamVersions.get(event.getTeamId());
        if (latest != null && event.getTeamVersion() < latest) {
            log.warn("忽略旧版本Fast Team状态事件, teamId={}, eventVersion={}, latestVersion={}",
                    event.getTeamId(), event.getTeamVersion(), latest);
            return true;
        }
        latestTeamVersions.merge(event.getTeamId(), event.getTeamVersion(), Math::max);
        return false;
    }

    private boolean isStateEvent(String type) {
        return type != null && (type.startsWith("TEAM_")
                || "MEMBER_STATE_CHANGED".equals(type));
    }

    private TeamEventDTO toEvent(Map<String, String> resultMap) {
        if (resultMap == null || !"1".equals(resultMap.get("schemaVersion"))
                || resultMap.get("eventId") == null || resultMap.get("teamId") == null
                || resultMap.get("type") == null) {
            log.warn("忽略字段不完整的Fast Team event");
            return null;
        }
        TeamEventDTO event = new TeamEventDTO();
        event.setSchemaVersion(resultMap.get("schemaVersion"));
        event.setEventId(resultMap.get("eventId"));
        event.setEventSeq(parseLong(resultMap.get("eventSeq")));
        event.setTransportGroup(resultMap.get("transportGroup"));
        event.setTeamId(resultMap.get("teamId"));
        event.setTeamMemberId(resultMap.get("teamMemberId"));
        event.setAcpClientId(resultMap.get("acpClientId"));
        event.setType(resultMap.get("type"));
        event.setTeamVersion(parseLong(resultMap.get("teamVersion")));
        event.setTimestamp(parseLong(resultMap.get("timestamp")));
        event.setData(resultMap.get("data"));
        return event;
    }

    private Long parseLong(String value) {
        return value == null ? null : Long.valueOf(value);
    }
}
