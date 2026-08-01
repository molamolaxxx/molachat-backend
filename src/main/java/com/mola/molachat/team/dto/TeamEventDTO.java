package com.mola.molachat.team.dto;

import lombok.Data;

@Data
public class TeamEventDTO {

    private String schemaVersion;

    private String eventId;

    private Long eventSeq;

    private String transportGroup;

    private String teamId;

    private String teamMemberId;

    private String acpClientId;

    private String type;

    private Long teamVersion;

    private Long timestamp;

    private String data;
}
