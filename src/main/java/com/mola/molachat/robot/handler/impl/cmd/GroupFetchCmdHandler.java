package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.model.CmdDescription;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-08-27 19:59
 **/
@Component
public class GroupFetchCmdHandler extends BaseCmdRobotHandler {


    @Override
    public String getCommand() {
        return "fetch";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            return String.valueOf(baseEvent.getMessageReceiveEvent().getMessage().getSessionId());
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
        return "获取远程代理命令的group";
    }

    @Override
    public List<CmdDescription> cmdDescriptions(String robotId, String sessionId) {
        return CmdDescription.builder()
                .cmdName("fetch")
                .cmdDesc("获取远程代理命令的group")
                .executeScript("sendMessageInner('fetch')")
                .buildSingleton();
    }
}
