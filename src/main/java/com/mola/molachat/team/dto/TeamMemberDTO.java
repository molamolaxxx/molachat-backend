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

    /** HOME 或 REMOTE，仅用于产品展示和后端二次校验。 */
    private String sourceType;

    /** 对用户友好的分组名；不包含 raw instanceId/transportGroup。 */
    private String sourceLabel;

    private Boolean homeSelectionRequired;

    private Boolean mixedSupported;

    private String sessionId;

    public String getStatus() {
        return status == null ? state : status;
    }
}
