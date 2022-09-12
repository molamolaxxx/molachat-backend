package com.mola.molachat.robot.event;

import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:29
 **/
@Data
public class CommandInputEvent extends BaseRobotEvent{

    private String commandInput;

    private MessageReceiveEvent messageReceiveEvent;

    public CommandInputEvent(MessageReceiveEvent messageReceiveEvent) {
        this.messageReceiveEvent = messageReceiveEvent;
    }
}
