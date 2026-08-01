package com.mola.molachat.team.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TeamDiscoveryDTO {

    private String schemaVersion;

    private String cmdProxyInstanceId;

    private String transportGroup;

    private String robotGroup;

    private String describeCommand;

    private String eventCommand;

    private boolean businessCommandsReady;

    private List<String> commands = new ArrayList<>();
}
