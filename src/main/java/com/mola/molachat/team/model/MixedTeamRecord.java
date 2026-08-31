package com.mola.molachat.team.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * MolaChat 持久化的 Mixed Team 全局 placement 与补偿进度。
 * 不保存 participant 的凭据、workDir 或完整 ACP 配置。
 */
@Data
public class MixedTeamRecord {

    private String schemaVersion = "mixed-1";
    private String teamId;
    private String ownerChatterId;
    private String name;
    private String state;
    private String requestId;
    private String payloadHash;
    private String homeInstanceId;
    private String lastError;
    private Long createdAt;
    private Long updatedAt;
    private List<Participant> participants = new ArrayList<>();
    private List<Member> members = new ArrayList<>();

    @Data
    public static class Participant {
        private String instanceId;
        private String transportGroup;
        private String state;
        private String requestId;
        private String lastError;
        private List<String> memberIds = new ArrayList<>();
    }

    @Data
    public static class Member {
        private String teamMemberId;
        private String acpClientId;
        private String participantInstanceId;
        private String transportGroup;
        private String sourceRobotId;
        private String sourceGroupId;
        private String displayName;
        private String avatar;
        private String remark;
        private Integer order;
        private String state;
    }
}
