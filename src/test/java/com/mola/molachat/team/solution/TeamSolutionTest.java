package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.enums.ChatterStatusEnum;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.team.dto.TeamMemberDTO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TeamSolutionTest {

    @Mock
    private ChatterService chatterService;

    @InjectMocks
    private TeamSolution teamSolution;

    private ChatterDTO visibleAcp;

    @Before
    public void setUp() {
        visibleAcp = robot("acp-code", "Code", "acp", ChatterStatusEnum.ONLINE.getCode());
        visibleAcp.setVisibleChatterIds(new HashSet<>(Collections.singletonList("owner")));
    }

    @Test
    public void listCandidatesOnlyReturnsVisibleOrdinaryAcpRobots() {
        ChatterDTO otherOwnerAcp = robot("acp-other", "Other", "acp", ChatterStatusEnum.ONLINE.getCode());
        otherOwnerAcp.setVisibleChatterIds(new HashSet<>(Collections.singletonList("someone-else")));
        ChatterDTO teamAcp = robot("team-acp-member", "Team", "team-acp", ChatterStatusEnum.ONLINE.getCode());
        ChatterDTO user = new ChatterDTO();
        user.setId("user");

        when(chatterService.list()).thenReturn(Arrays.asList(visibleAcp, otherOwnerAcp, teamAcp, user));

        List<TeamMemberDTO> result = teamSolution.listCandidates("owner");

        assertEquals(1, result.size());
        assertEquals("acp-code", result.get(0).getSourceRobotId());
        assertEquals("acp-codeowner", result.get(0).getSourceGroupId());
        assertEquals("AVAILABLE", result.get(0).getStatus());
    }

    @Test
    public void disconnectedAcpCannotBeSelectedAsTeamSource() {
        visibleAcp.setStatus(ChatterStatusEnum.DISCONNECT.getCode());
        when(chatterService.list()).thenReturn(Collections.singletonList(visibleAcp));

        List<TeamMemberDTO> result = teamSolution.listCandidates("owner");

        assertEquals(0, result.size());
    }

    private ChatterDTO robot(String id, String name, String robotGroup, Integer status) {
        ChatterDTO chatter = new ChatterDTO();
        chatter.setId(id);
        chatter.setName(name);
        chatter.setRobot(true);
        chatter.setRobotGroup(robotGroup);
        chatter.setStatus(status);
        return chatter;
    }
}
