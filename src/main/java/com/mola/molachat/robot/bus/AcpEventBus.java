package com.mola.molachat.robot.bus;

import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.handler.impl.acp.AcpExecHandler;
import com.mola.molachat.robot.handler.impl.cmd.GroupFetchCmdHandler;
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
public class AcpEventBus extends RobotEventBus {

    @Resource
    private AcpExecHandler acpExecHandler;

    @Resource
    private GroupFetchCmdHandler groupFetchCmdHandler;

    protected List<IRobotEventHandler> getRobotEventHandlers() {
        return Arrays.asList(groupFetchCmdHandler, acpExecHandler);
    }
}
