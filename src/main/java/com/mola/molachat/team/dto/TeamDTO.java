package com.mola.molachat.team.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TeamDTO {

    private String teamId;

    private String ownerChatterId;

    private String name;

    private String mode = "NORMAL";

    private String captainTeamMemberId;

    private String status;

    private String state;

    private Long version;

    private List<TeamMemberDTO> members = new ArrayList<>();

    private Object lastError;
    /** Projection only; the registry center owns coordinated teams. */
    private boolean coordinated;

    public String getStatus() {
        return status == null ? state : status;
    }

    public String getMode() {
        return mode == null || mode.trim().isEmpty()
                ? "NORMAL" : mode.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
