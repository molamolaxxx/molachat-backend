package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.enums.ChatterTagEnum;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.common.annotation.RefreshChatterList;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamMemberDTO;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TeamRobotProjectionSolution {

    public static final String TEAM_ACP_GROUP = "team-acp";
    private static final String TEAM_ACP_EVENT_BUS = "acpEventBus";

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private SessionService sessionService;

    /**
     * 以cmd-proxy返回的单个Team快照对账MolaChat机器人投影。
     */
    @RefreshChatterList
    public void sync(TeamDTO team) {
        validateTeam(team);
        List<RobotChatter> existing = findTeamRobots(team.getTeamId());
        Map<String, RobotChatter> existingByMemberId = existing.stream()
                .collect(Collectors.toMap(RobotChatter::getTeamMemberId, Function.identity()));
        Set<String> targetMemberIds = team.getMembers().stream()
                .map(TeamMemberDTO::getTeamMemberId)
                .collect(Collectors.toSet());

        existing.stream()
                .filter(robot -> !targetMemberIds.contains(robot.getTeamMemberId()))
                .forEach(this::deleteRobotAndSessions);

        for (TeamMemberDTO member : team.getMembers()) {
            RobotChatter robot = existingByMemberId.get(member.getTeamMemberId());
            if (robot == null) {
                chatterFactory.create(toRobot(team, member));
            } else {
                updateRobot(robot, team, member);
            }
        }
    }

    /**
     * 以cmd-proxy的owner全量快照恢复Team联系人投影，并清理已不在权威快照中的投影。
     */
    @RefreshChatterList
    public void syncAll(String ownerChatterId, List<TeamDTO> teams) {
        if (StringUtils.isBlank(ownerChatterId)) {
            return;
        }
        List<TeamDTO> ownerTeams = teams == null ? Collections.emptyList() : teams.stream()
                .filter(Objects::nonNull)
                .filter(team -> ownerChatterId.equals(team.getOwnerChatterId()))
                .collect(Collectors.toList());
        Set<String> projectedTeamIds = ownerTeams.stream()
                .filter(this::shouldProject)
                .map(TeamDTO::getTeamId)
                .collect(Collectors.toCollection(HashSet::new));

        findOwnerTeamRobots(ownerChatterId).stream()
                .filter(robot -> !projectedTeamIds.contains(robot.getTeamId()))
                .forEach(this::deleteRobotAndSessions);
        ownerTeams.stream()
                .filter(this::shouldProject)
                .forEach(this::sync);
    }

    @RefreshChatterList
    public void delete(String teamId) {
        if (StringUtils.isBlank(teamId)) {
            return;
        }
        findTeamRobots(teamId).forEach(this::deleteRobotAndSessions);
    }

    private List<RobotChatter> findTeamRobots(String teamId) {
        return chatterFactory.list().stream()
                .filter(chatter -> chatter instanceof RobotChatter)
                .map(chatter -> (RobotChatter) chatter)
                .filter(robot -> TEAM_ACP_GROUP.equals(robot.getRobotGroup()))
                .filter(robot -> teamId.equals(robot.getTeamId()))
                .collect(Collectors.toList());
    }

    private List<RobotChatter> findOwnerTeamRobots(String ownerChatterId) {
        return chatterFactory.list().stream()
                .filter(chatter -> chatter instanceof RobotChatter)
                .map(chatter -> (RobotChatter) chatter)
                .filter(robot -> TEAM_ACP_GROUP.equals(robot.getRobotGroup()))
                .filter(robot -> robot.getVisibleChatterIds() != null
                        && robot.getVisibleChatterIds().contains(ownerChatterId))
                .collect(Collectors.toList());
    }

    private boolean shouldProject(TeamDTO team) {
        return Objects.equals("READY", team.getStatus())
                || Objects.equals("RECOVERING", team.getStatus())
                || Objects.equals("DELETING", team.getStatus());
    }

    private RobotChatter toRobot(TeamDTO team, TeamMemberDTO member) {
        RobotChatter robot = new RobotChatter();
        robot.setId(memberRobotId(member));
        robot.setAppKey(memberRobotId(member));
        robot.setName(member.getDisplayName());
        robot.setSignature("Fast Team · " + team.getName());
        robot.setImgUrl(StringUtils.defaultIfBlank(member.getAvatar(), "img/kiro.png"));
        robot.setIp("127.0.0.1");
        robot.setStatus(memberStatus(member));
        robot.setTag(ChatterTagEnum.ROBOT.getCode());
        robot.setEventBusBeanName(TEAM_ACP_EVENT_BUS);
        robot.setRobotGroup(TEAM_ACP_GROUP);
        robot.setVisibleChatterIds(Collections.singleton(team.getOwnerChatterId()));
        robot.setTeamId(team.getTeamId());
        robot.setTeamMemberId(member.getTeamMemberId());
        return robot;
    }

    private void updateRobot(RobotChatter robot, TeamDTO team, TeamMemberDTO member) {
        robot.setName(member.getDisplayName());
        robot.setSignature("Fast Team · " + team.getName());
        robot.setImgUrl(StringUtils.defaultIfBlank(member.getAvatar(), "img/kiro.png"));
        robot.setStatus(memberStatus(member));
        robot.setVisibleChatterIds(Collections.singleton(team.getOwnerChatterId()));
        chatterFactory.update(robot);
    }

    private int memberStatus(TeamMemberDTO member) {
        return Objects.equals("READY", member.getStatus()) || Objects.equals("BUSY", member.getStatus())
                ? ChatterStatusEnum.ONLINE.getCode()
                : ChatterStatusEnum.DISCONNECT.getCode();
    }

    private String memberRobotId(TeamMemberDTO member) {
        return StringUtils.defaultIfBlank(member.getRobotId(), "team-acp-" + member.getTeamMemberId());
    }

    private void deleteRobotAndSessions(RobotChatter robot) {
        sessionService.closeSessions(robot.getId());
        chatterFactory.remove(robot);
    }

    private void validateTeam(TeamDTO team) {
        if (team == null || StringUtils.isBlank(team.getTeamId())
                || StringUtils.isBlank(team.getOwnerChatterId())) {
            throw new IllegalArgumentException("teamId和ownerChatterId不能为空");
        }
        if (team.getMembers() == null) {
            team.setMembers(Collections.emptyList());
        }
        for (TeamMemberDTO member : team.getMembers()) {
            if (member == null || StringUtils.isBlank(member.getTeamMemberId())) {
                throw new IllegalArgumentException("teamMemberId不能为空");
            }
        }
    }
}
