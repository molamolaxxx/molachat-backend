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

    /** null 表示旧 cmd-proxy 尚未实现 Team 来源发现。 */
    private List<TeamMemberSourceDTO> teamMemberSources;

    /** B 侧 standing grant 发布的 instance-scoped remote Team 来源。 */
    private List<RemoteTeamMemberSourceDTO> remoteTeamMemberSources = new ArrayList<>();

    private TeamTransportCapabilitiesDTO capabilities;
}
