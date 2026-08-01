package com.mola.molachat.team.solution;

import com.mola.molachat.team.dto.TeamDTO;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class TeamSnapshotSolutionTest {

    private final TeamSnapshotSolution solution = new TeamSnapshotSolution();

    @Test
    public void liveSnapshotCanBeReadWithoutCallingCmdproxyAgain() {
        TeamDTO creating = team("team-1", "owner", "CREATING");
        solution.replaceAll("owner", Collections.singletonList(creating));

        assertEquals(1, solution.list("owner").size());
        assertEquals("CREATING", solution.list("owner").get(0).getStatus());

        TeamDTO ready = team("team-1", "owner", "READY");
        solution.upsert(ready);

        assertEquals("READY", solution.list("owner").get(0).getStatus());
    }

    @Test
    public void replaceAndDeleteStayOwnerScoped() {
        solution.replaceAll("owner-1", Arrays.asList(
                team("team-1", "owner-1", "READY"),
                team("foreign", "owner-2", "READY")));
        solution.replaceAll("owner-2", Collections.singletonList(
                team("team-2", "owner-2", "READY")));

        solution.delete("team-1");

        assertEquals(0, solution.list("owner-1").size());
        assertEquals(1, solution.list("owner-2").size());
    }

    private TeamDTO team(String teamId, String owner, String state) {
        TeamDTO team = new TeamDTO();
        team.setTeamId(teamId);
        team.setOwnerChatterId(owner);
        team.setState(state);
        return team;
    }
}
