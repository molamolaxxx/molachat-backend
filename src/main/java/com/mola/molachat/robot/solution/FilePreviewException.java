package com.mola.molachat.robot.solution;

import lombok.Getter;

@Getter
public class FilePreviewException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public FilePreviewException(String code, int httpStatus, String message) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public FilePreviewException(String code, int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
