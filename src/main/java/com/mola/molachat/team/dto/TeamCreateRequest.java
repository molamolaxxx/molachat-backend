package com.mola.molachat.team.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;

@Data
public class TeamCreateRequest {

    @NotBlank
    private String chatterId;

    @NotBlank
    private String token;

    @NotBlank
    private String requestId;

    @NotBlank
    @Size(max = 40)
    private String name;

    @Valid
    @Size(min = 2, max = 6)
    private List<TeamCreateMemberRequest> members = new ArrayList<>();
}
