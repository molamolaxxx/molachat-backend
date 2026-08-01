package com.mola.molachat.team.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TeamDTO {

    private String teamId;

    private String ownerChatterId;

    private String name;

    private String status;

    private String state;

    private Long version;

    private List<TeamMemberDTO> members = new ArrayList<>();

    private Object lastError;

    public String getStatus() {
        return status == null ? state : status;
    }
}
