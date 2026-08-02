package com.mola.molachat.team.dto;

import lombok.Data;

@Data
public class TeamMemberDTO {

    private String cmdProxyInstanceId;

    private String transportGroup;

    private String ownerChatterId;

    private String teamMemberId;

    private String acpClientId;

    private String robotId;

    private String sourceRobotId;

    private String sourceGroupId;

    private String displayName;

    private String avatar;

    private String status;

    private String state;

    private Integer order;

    private String remark;

    private Boolean onlyTeamMember;

    private Boolean discoverySupported;

    private String sessionId;

    public String getStatus() {
        return status == null ? state : status;
    }
}
