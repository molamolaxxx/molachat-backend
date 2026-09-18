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

    /** NORMAL（缺省兼容旧请求）或 CAPTAIN。 */
    private String mode = "NORMAL";

    /** CAPTAIN 模式下必须引用 members 中唯一、稳定的 teamMemberId。 */
    private String captainTeamMemberId;

    @Valid
    @Size(min = 1, max = 6)
    private List<TeamCreateMemberRequest> members = new ArrayList<>();
}
