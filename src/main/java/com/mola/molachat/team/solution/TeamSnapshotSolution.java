package com.mola.molachat.team.solution;

import com.mola.molachat.team.dto.TeamDTO;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MolaChat侧可重建Team快照。权威数据仍来自cmdproxy。
 */
@Service
public class TeamSnapshotSolution {

    private final Map<String, Map<String, TeamDTO>> teamsByOwner = new ConcurrentHashMap<>();
    private final Map<String, String> ownersByTeam = new ConcurrentHashMap<>();

    public void replaceAll(String ownerChatterId, List<TeamDTO> teams) {
        if (StringUtils.isBlank(ownerChatterId)) {
            return;
        }
        Map<String, TeamDTO> replacement = new ConcurrentHashMap<>();
        if (teams != null) {
            for (TeamDTO team : teams) {
                if (team != null && StringUtils.isNotBlank(team.getTeamId())
                        && ownerChatterId.equals(team.getOwnerChatterId())) {
                    replacement.put(team.getTeamId(), team);
                    ownersByTeam.put(team.getTeamId(), ownerChatterId);
                }
            }
        }
        Map<String, TeamDTO> previous = teamsByOwner.put(ownerChatterId, replacement);
        if (previous != null) {
            previous.keySet().stream()
                    .filter(teamId -> !replacement.containsKey(teamId))
                    .forEach(ownersByTeam::remove);
        }
    }

    public void upsert(TeamDTO team) {
        if (team == null || StringUtils.isBlank(team.getTeamId())
                || StringUtils.isBlank(team.getOwnerChatterId())) {
            return;
        }
        teamsByOwner.computeIfAbsent(
                team.getOwnerChatterId(), ignored -> new ConcurrentHashMap<>())
                .put(team.getTeamId(), team);
        ownersByTeam.put(team.getTeamId(), team.getOwnerChatterId());
    }

    public void delete(String teamId) {
        String ownerChatterId = ownersByTeam.remove(teamId);
        if (ownerChatterId != null && teamsByOwner.containsKey(ownerChatterId)) {
            teamsByOwner.get(ownerChatterId).remove(teamId);
        }
    }

    public List<TeamDTO> list(String ownerChatterId) {
        Map<String, TeamDTO> teams = teamsByOwner.get(ownerChatterId);
        return teams == null
                ? Collections.emptyList()
                : new ArrayList<>(teams.values());
    }
}
