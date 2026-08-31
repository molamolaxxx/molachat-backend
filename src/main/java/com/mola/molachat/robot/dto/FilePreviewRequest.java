package com.mola.molachat.robot.dto;

import lombok.Data;

@Data
public class FilePreviewRequest {

    private String chatterId;
    private String token;
    private String sessionId;
    private String target;
    private Integer requestedLine;
}
