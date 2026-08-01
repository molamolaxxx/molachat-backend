package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.team.dto.TeamMemberDTO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamSolution {

    private static final String ACP_GROUP = "acp";

    @Resource
    private ChatterService chatterService;

    /**
     * V1候选成员来自当前用户可见的普通ACP robot。
     * sourceGroupId沿用当前一对一会话的确定性生成规则。
     */
    public List<TeamMemberDTO> listCandidates(String ownerChatterId) {
        return chatterService.list().stream()
                .filter(ChatterDTO::isRobot)
                .filter(chatter -> ACP_GROUP.equals(chatter.getRobotGroup()))
                .filter(chatter -> chatter.getVisibleChatterIds() == null
                        || chatter.getVisibleChatterIds().isEmpty()
                        || chatter.getVisibleChatterIds().contains(ownerChatterId))
                .filter(chatter -> ChatterStatusEnum.ONLINE.getCode().equals(chatter.getStatus()))
                .map(chatter -> toCandidate(ownerChatterId, chatter))
                .collect(Collectors.toList());
    }

    private TeamMemberDTO toCandidate(String ownerChatterId, ChatterDTO chatter) {
        TeamMemberDTO candidate = new TeamMemberDTO();
        candidate.setSourceRobotId(chatter.getId());
        candidate.setSourceGroupId(computeSourceGroupId(ownerChatterId, chatter.getId()));
        candidate.setDisplayName(chatter.getName());
        candidate.setAvatar(chatter.getImgUrl());
        candidate.setStatus(toCandidateStatus(chatter.getStatus()));
        return candidate;
    }

    private String toCandidateStatus(Integer status) {
        if (ChatterStatusEnum.ONLINE.getCode().equals(status)) {
            return "AVAILABLE";
        }
        if (ChatterStatusEnum.DISCONNECT.getCode().equals(status)) {
            return "DISCONNECTED";
        }
        return "UNAVAILABLE";
    }

    private String computeSourceGroupId(String ownerChatterId, String robotId) {
        List<String> ids = Arrays.asList(ownerChatterId, robotId);
        Collections.sort(ids);
        return ids.get(0) + ids.get(1);
    }
}
