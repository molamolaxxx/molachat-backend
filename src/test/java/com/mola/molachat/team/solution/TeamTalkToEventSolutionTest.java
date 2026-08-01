package com.mola.molachat.team.solution;

import com.mola.molachat.team.dto.TeamEventDTO;
import org.junit.Test;

public class TeamTalkToEventSolutionTest {

    private final TeamTalkToEventSolution solution = new TeamTalkToEventSolution();

    @Test
    public void acceptsDeliveredQueuedAndRejectedEvents() {
        solution.handle(event("TALK_TO_SEND", "DELIVERED", null));
        solution.handle(event("TALK_TO_RECEIVE", "DELIVERED_FROM_INBOX", null));
        solution.handle(event("TALK_TO_QUEUED", "QUEUED", null));
        solution.handle(event("TALK_TO_REJECTED", "REJECTED", "INBOX_FULL"));
    }

    @Test
    public void incompleteDataIsIgnored() {
        TeamEventDTO event = new TeamEventDTO();
        event.setTeamId("team-1");
        event.setType("TALK_TO_REJECTED");
        event.setData("{}");

        solution.handle(event);
    }

    private TeamEventDTO event(String type, String delivery, String reason) {
        TeamEventDTO event = new TeamEventDTO();
        event.setTeamId("team-1");
        event.setType(type);
        event.setData("{\"messageId\":\"message-1\",\"senderTeamMemberId\":\"member-1\","
                + "\"targetTeamMemberId\":\"member-2\",\"delivery\":\"" + delivery + "\""
                + (reason == null ? "" : ",\"reason\":\"" + reason + "\"") + "}");
        return event;
    }
}
