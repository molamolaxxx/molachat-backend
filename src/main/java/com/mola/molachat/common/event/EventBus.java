package com.mola.molachat.common.event;

import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.common.event.event.BaseEvent;

public interface EventBus<E extends BaseEvent, A extends BaseAction> {

    /**
     * 处理事件并返回动作
     * @param baseEvent
     * @return
     */
    A handler(E baseEvent);
}
