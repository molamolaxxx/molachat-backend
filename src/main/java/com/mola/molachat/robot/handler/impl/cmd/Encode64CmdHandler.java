package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: base64解码
 * @date : 2022-09-12 16:26
 **/
@Component
public class Encode64CmdHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "enc64";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            String encodedStr = java.util.Base64.getEncoder().encodeToString(baseEvent.getCommandInput().getBytes());
            return encodedStr;
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
        return "base64编码";
    }
}
