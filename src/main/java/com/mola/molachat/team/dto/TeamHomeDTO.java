package com.mola.molachat.team.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class TeamHomeDTO {

    private String homeCmdProxyInstanceId;
    private boolean selectionRequired;
    private List<Device> devices = new ArrayList<>();

    @Data
    public static class Device {
        private String cmdProxyInstanceId;
        private String status;
        private boolean selected;
    }
}
