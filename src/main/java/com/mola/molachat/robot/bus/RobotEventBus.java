package com.mola.molachat.robot.bus;

import com.mola.molachat.event.EventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageSendAction;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:23
 **/
@Component
public class RobotEventBus implements EventBus<BaseRobotEvent, MessageSendAction> {

    @Resource
    private List<IRobotEventHandler> robotEventHandlers;

    @Override
    public MessageSendAction handler(BaseRobotEvent baseEvent) {
        MessageSendAction finalAction = null;
        for (IRobotEventHandler robotEventHandler : robotEventHandlers) {
            if (null == robotEventHandler.acceptEvent()) {
                continue;
            }
            if (robotEventHandler.acceptEvent().equals(baseEvent.getClass())) {
                MessageSendAction action = robotEventHandler.handler(baseEvent);
                if (null == finalAction || finalAction.getOrder() < action.getOrder()) {
                    finalAction = action;
                }
            }
        }
        return finalAction;
    }
}
