package com.mola.molachat.robot.dto;

import lombok.Data;

@Data
public class FilePreviewDTO {

    private String source;
    private String displayName;
    private String content;
    private String mediaType;
    private String charset;
    private String renderMode;
    private String language;
    private Integer requestedLine;
    private Integer lineCount;
    private Long size;
    private Boolean truncated;
    private String baseUrl;
}
