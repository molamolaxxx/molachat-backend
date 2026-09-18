package com.mola.molachat.team.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class TeamCreateMemberRequest {

    /** 新客户端预分配的稳定成员 ID；旧客户端缺省时由服务端确定性生成。 */
    private String teamMemberId;

    @NotBlank
    private String cmdProxyInstanceId;

    @NotBlank
    private String transportGroup;

    @NotBlank
    private String sourceRobotId;

    @NotBlank
    private String sourceGroupId;

    /** 用户为该成员填写的 Team 专属备注；为空时由创建协议按场景回退。 */
    private String remark;
}
