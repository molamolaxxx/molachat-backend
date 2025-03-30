package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.session.model.StreamMessageConnect;
import com.mola.molachat.session.solution.MessageSolution;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-03-27 21:06
 **/
@Component
public class StopChatStreamRobotHandler extends BaseCmdRobotHandler {

    @Resource
    private MessageSolution messageSolution;

    @Override
    public String getCommand() {
        return "#stop-stream#";
    }

    @Override
    public String getDesc() {
        return "";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        MessageReceiveEvent messageReceiveEvent = baseEvent.getMessageReceiveEvent();
        StreamMessageConnect streamConnect = messageSolution.findStreamConnect(
                messageReceiveEvent.getRobotChatter().getId(),
                messageReceiveEvent.getSessionId());
        if (streamConnect != null) {
            streamConnect.setClosed(true);
        }
        return "";
    }

    @Override
    public CmdDescription cmdDescription(String robotId, String sessionId) {
        StreamMessageConnect streamConnect = messageSolution.findStreamConnect(robotId, sessionId);
        if (streamConnect != null) {
            return CmdDescription.builder()
                    .cmdName("#stop-stream#")
                    .cmdDesc("终止当前对话")
                    .executeScript("sendMessageInner('#stop-stream#')")
                    .build();
        }
        return CmdDescription.NOT_SUPPORT;
    }
}
