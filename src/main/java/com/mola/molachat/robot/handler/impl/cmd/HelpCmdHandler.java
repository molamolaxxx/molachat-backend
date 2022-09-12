package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: base64解码
 * @date : 2022-09-12 16:26
 **/
@Component
public class HelpCmdHandler extends BaseCmdRobotHandler {

    @Resource
    private List<BaseCmdRobotHandler> cmdRobotHandlers;

    @Override
    public String getCommand() {
        return "help";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < cmdRobotHandlers.size(); i++) {
                BaseCmdRobotHandler cmdRobotHandler = cmdRobotHandlers.get(i);
                stringBuilder.append(String.format("【%s】 : %s", cmdRobotHandler.getCommand(), cmdRobotHandler.getDesc()));
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getDesc() {
        return "帮助";
    }
}
