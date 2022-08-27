package com.mola.molachat.robot.handler.impl;

import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.event.MessageSendAction;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 远程聊天处理器，调用httpClient
 * @date : 2022-08-27 11:47
 **/
@Component
public class RemoteRobotChatHandler implements IRobotEventHandler<MessageReceiveEvent> {

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        return null;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }
}
