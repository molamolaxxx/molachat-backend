package com.mola.molachat.team.dto;

import lombok.Data;

@Data
public class TeamMemberSourceDTO {
    private String ownerChatterId;
    private String sourceGroupId;
    private String sourceRobotId;
    private String robotName;
    private String displayName;
    private String avatar;
    private String remark;
    private boolean onlyTeamMember;
}
