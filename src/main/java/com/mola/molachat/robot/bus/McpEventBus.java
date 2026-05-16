package com.mola.molachat.robot.bus;

import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.handler.impl.cmd.ChatterIdCmdHandler;
import com.mola.molachat.robot.handler.impl.cmd.GroupFetchCmdHandler;
import com.mola.molachat.robot.handler.impl.mcp.McpExecHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:openai chatgpt
 * @date : 2020-12-07 15:23
 **/
@Component
public class McpEventBus extends RobotEventBus {

    @Resource
    private McpExecHandler mcpExecHandler;

    @Resource
    private GroupFetchCmdHandler groupFetchCmdHandler;

    @Resource
    private ChatterIdCmdHandler chatterIdCmdHandler;

    protected List<IRobotEventHandler> getRobotEventHandlers() {
        return Arrays.asList(groupFetchCmdHandler, chatterIdCmdHandler, mcpExecHandler);
    }
}
