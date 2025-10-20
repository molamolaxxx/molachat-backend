package com.mola.molachat.robot.handler.impl.mcp;

import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-10-19 10:47
 **/
@Data
public class McpProcessTodoItem {

    private String processId;

    private String todoListPath;

    private String resourcePath;

    private String userRequest;

    public String buildPreFilePath() {
        return String.format("\n%s\n%s\n", todoListPath, resourcePath);
    }
}
