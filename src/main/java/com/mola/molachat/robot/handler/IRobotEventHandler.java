package com.mola.molachat.robot.handler;

import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageSendAction;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 执行器
 * @date : 2022-08-27 11:29
 **/
public interface IRobotEventHandler<E extends BaseRobotEvent> {

    /**
     * 执行并返回action
     * @param baseEvent
     * @return
     */
    MessageSendAction handler(E baseEvent);

    /**
     * 支持的event
     * @return
     */
    Class<? extends BaseRobotEvent> acceptEvent();
}
