package com.mola.molachat.team.dto;

import lombok.Data;

@Data
public class RemoteTeamMemberSourceDTO {
    private String granteeOwnerChatterId;
    private String participantInstanceId;
    private String sourceGroupId;
    private String sourceRobotId;
    private String robotName;
    private String displayName;
    private String avatar;
    private String remark;
}
