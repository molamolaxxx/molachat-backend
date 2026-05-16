package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.model.CmdDescription;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 获取当前用户的chatterId
 * @date : 2026-05-03 09:27
 **/
@Component
public class ChatterIdCmdHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "chatterid";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            return baseEvent.getMessageReceiveEvent().getMessage().getChatterId();
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
        return "获取当前用户的chatterId";
    }

    @Override
    public List<CmdDescription> cmdDescriptions(String robotId, String sessionId) {
        return CmdDescription.builder()
                .cmdName("chatterid")
                .cmdDesc("获取当前用户的chatterId")
                .executeScript("sendMessageInner('chatterid')")
                .buildSingleton();
    }
}
