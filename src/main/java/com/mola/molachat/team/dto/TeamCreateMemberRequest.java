package com.mola.molachat.team.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TeamCreateMemberRequest {

    @NotBlank
    private String cmdProxyInstanceId;

    @NotBlank
    private String transportGroup;

    @NotBlank
    private String sourceRobotId;

    @NotBlank
    private String sourceGroupId;
}
