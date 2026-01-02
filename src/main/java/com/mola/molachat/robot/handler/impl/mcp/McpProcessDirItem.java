package com.mola.molachat.robot.handler.impl.mcp;

import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-10-19 10:47
 **/
@Data
public class McpProcessDirItem {

    private String processId;

    private String todoListPath;

    private String resourcePath;

    private String questionPath;

    private String userRequest;

    private String projectFilePath;

    public String buildPreFilePath() {
        return String.format("\n%s\n%s\n", todoListPath, resourcePath);
    }

    public boolean matchTodoDir() {
        return processId.startsWith("TODO");
    }

    public boolean matchQuestionDir() {
        return processId.startsWith("QUESTION");
    }
}
