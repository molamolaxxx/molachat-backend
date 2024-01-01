package com.mola.molachat.robot.handler;

import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.event.BaseRobotEvent;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 执行器
 * @date : 2022-08-27 11:29
 **/
public interface IRobotEventHandler<E extends BaseRobotEvent, A extends BaseAction> {

    /**
     * 执行并返回action
     * @param baseEvent
     * @return
     */
    A handler(E baseEvent);

    /**
     * 支持的event
     * @return
     */
    Class<? extends BaseRobotEvent> acceptEvent();

    /**
     * 执行顺序
     * @return
     */
    default Integer order() {
        return Integer.MIN_VALUE;
    }
}
