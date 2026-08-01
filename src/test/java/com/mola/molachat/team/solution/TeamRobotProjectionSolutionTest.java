package com.mola.molachat.team.solution;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.team.dto.TeamDTO;
import com.mola.molachat.team.dto.TeamMemberDTO;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class TeamRobotProjectionSolutionTest {

    @Mock
    private ChatterFactoryInterface chatterFactory;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private TeamRobotProjectionSolution projectionSolution;

    @Test
    public void syncCreatesStableTeamScopedRobotProjection() {
        when(chatterFactory.list()).thenReturn(Collections.emptyList());
        TeamDTO team = team("team-1", member("member-1", "READY"));

        projectionSolution.sync(team);

        ArgumentCaptor<Chatter> captor = ArgumentCaptor.forClass(Chatter.class);
        verify(chatterFactory).create(captor.capture());
        RobotChatter robot = (RobotChatter) captor.getValue();
        assertEquals("team-acp-member-1", robot.getId());
        assertEquals("team-acp", robot.getRobotGroup());
        assertEquals("team-1", robot.getTeamId());
        assertEquals("member-1", robot.getTeamMemberId());
        assertEquals(Collections.singleton("owner"), robot.getVisibleChatterIds());
    }

    @Test
    public void syncOnlyDeletesStaleRobotsFromTheSameTeam() {
        RobotChatter stale = projectedRobot("team-1", "stale");
        RobotChatter otherTeam = projectedRobot("team-2", "other");
        RobotChatter ordinaryAcp = projectedRobot(null, null);
        ordinaryAcp.setRobotGroup("acp");
        when(chatterFactory.list()).thenReturn(Arrays.asList(stale, otherTeam, ordinaryAcp));

        projectionSolution.sync(team("team-1", member("member-1", "READY")));

        verify(sessionService).closeSessions(stale.getId());
        verify(chatterFactory).remove(stale);
        verify(chatterFactory, never()).remove(otherTeam);
        verify(chatterFactory, never()).remove(ordinaryAcp);
        verify(chatterFactory).create(any(RobotChatter.class));
    }

    @Test
    public void syncAllRestoresReadyProjectionAndDeletesStaleOwnerProjection() {
        RobotChatter stale = projectedRobot("team-stale", "stale");
        stale.setVisibleChatterIds(Collections.singleton("owner"));
        RobotChatter anotherOwner = projectedRobot("team-other", "other");
        anotherOwner.setVisibleChatterIds(Collections.singleton("another-owner"));
        when(chatterFactory.list())
                .thenReturn(Arrays.asList(stale, anotherOwner))
                .thenReturn(Arrays.asList(stale, anotherOwner));
        TeamDTO ready = team("team-ready", member("member-1", "READY"));
        ready.setStatus("READY");

        projectionSolution.syncAll("owner", Collections.singletonList(ready));

        verify(sessionService).closeSessions(stale.getId());
        verify(chatterFactory).remove(stale);
        verify(chatterFactory, never()).remove(anotherOwner);
        verify(chatterFactory).create(any(RobotChatter.class));
    }

    @Test
    public void syncAllDoesNotExposeCreatingMembers() {
        when(chatterFactory.list()).thenReturn(Collections.emptyList());
        TeamDTO creating = team("team-creating", member("member-1", "CREATING"));
        creating.setStatus("CREATING");

        projectionSolution.syncAll("owner", Collections.singletonList(creating));

        verify(chatterFactory, never()).create(any(RobotChatter.class));
    }

    private TeamDTO team(String teamId, TeamMemberDTO member) {
        TeamDTO team = new TeamDTO();
        team.setTeamId(teamId);
        team.setOwnerChatterId("owner");
        team.setName("Fast");
        team.setMembers(Collections.singletonList(member));
        return team;
    }

    private TeamMemberDTO member(String memberId, String status) {
        TeamMemberDTO member = new TeamMemberDTO();
        member.setTeamMemberId(memberId);
        member.setDisplayName("Member");
        member.setStatus(status);
        return member;
    }

    private RobotChatter projectedRobot(String teamId, String memberId) {
        RobotChatter robot = new RobotChatter();
        robot.setId("team-acp-" + memberId);
        robot.setRobotGroup("team-acp");
        robot.setTeamId(teamId);
        robot.setTeamMemberId(memberId);
        return robot;
    }
}
