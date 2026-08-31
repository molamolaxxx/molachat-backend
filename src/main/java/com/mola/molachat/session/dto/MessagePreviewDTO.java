package com.mola.molachat.session.dto;

import lombok.Data;

import java.util.Date;
import java.util.Map;

/**
 * Lightweight message projection used when opening an existing session.
 */
@Data
public class MessagePreviewDTO {

    private String id;

    private String chatterId;

    private String content;

    private boolean contentTruncated;

    private Date createTime;

    private boolean common;

    private String sessionId;

    private Map<String, String> showStyleProps;

    private String url;

    private String snapshotUrl;

    private String fileName;

    private String fileStorage;
}
