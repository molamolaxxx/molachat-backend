package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.common.utils.OperatorUtils;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: base64解码
 * @date : 2022-09-12 16:26
 **/
@Component
public class EvalCmdHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "eval";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            return String.valueOf(OperatorUtils.operate(baseEvent.getCommandInput()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer order() {
        return 0;
    }

    @Override
    public String getDesc() {
        return "计算表达式";
    }
}
