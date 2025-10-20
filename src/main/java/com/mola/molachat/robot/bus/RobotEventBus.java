package com.mola.molachat.robot.bus;

import com.google.common.collect.Lists;
import com.mola.molachat.common.event.EventBus;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.handler.impl.ChatGptRobotHandler;
import com.mola.molachat.robot.handler.impl.ImageGenerateChatHandler;
import com.mola.molachat.robot.handler.impl.cmd.StopChatStreamRobotHandler;
import com.mola.molachat.robot.handler.impl.mcp.McpExecHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.session.model.Message;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:23
 **/
@Component
public class RobotEventBus implements EventBus<BaseRobotEvent, BaseAction>, InitializingBean {

    @Resource
    private List<IRobotEventHandler> robotEventHandlers;

    @Override
    public BaseAction handler(BaseRobotEvent baseEvent) {
        BaseAction finalAction = BaseAction.empty();
        IRobotEventHandler defaultEventHandler = null;
        for (IRobotEventHandler robotEventHandler : getRobotEventHandlers()) {
            if (null == robotEventHandler.acceptEvent()) {
                continue;
            }
            if (robotEventHandler.acceptEvent().equals(baseEvent.getClass())) {
                if (robotEventHandler.isDefaultHandler()) {
                    defaultEventHandler = robotEventHandler;
                }
                BaseAction action = robotEventHandler.handler(baseEvent);
                if (action.getSkip()) {
                    continue;
                }
                finalAction = action;
                if (action.getFinalExec()) {
                    break;
                }
            }
        }
        if (finalAction.getFinalExec()) {
            return finalAction;
        }
        if (baseEvent instanceof MessageReceiveEvent && defaultEventHandler instanceof BaseCmdRobotHandler) {
            MessageReceiveEvent event = (MessageReceiveEvent) baseEvent;
            BaseCmdRobotHandler cmdRobotHandler = (BaseCmdRobotHandler) defaultEventHandler;
            Message message = event.getMessage();
            message.setContent(cmdRobotHandler.getCommand() + " " + message.getContent());
            finalAction = cmdRobotHandler.handler(event);
        }
        return finalAction;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        List<IRobotEventHandler> robotEventHandlers = new ArrayList<>();
        for (IRobotEventHandler robotEventHandler : this.robotEventHandlers) {
            if (robotEventHandler instanceof ChatGptRobotHandler
                    || robotEventHandler instanceof ImageGenerateChatHandler
                    || robotEventHandler instanceof StopChatStreamRobotHandler
                    || robotEventHandler instanceof McpExecHandler) {
                continue;
            }
            robotEventHandlers.add(robotEventHandler);
        }
        robotEventHandlers.sort(Comparator.comparing(IRobotEventHandler::order, Comparator.reverseOrder()));
        this.robotEventHandlers = robotEventHandlers;
    }

    protected List<IRobotEventHandler> getRobotEventHandlers() {
        return robotEventHandlers;
    }

    public List<CmdDescription> getAllCmdDescriptions(String robotId, String sessionId) {
        List<CmdDescription> resultList = Lists.newArrayList();
        getRobotEventHandlers().forEach(handler -> {
            resultList.addAll(handler.cmdDescriptions(robotId, sessionId));
        });
        return resultList.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }
}
