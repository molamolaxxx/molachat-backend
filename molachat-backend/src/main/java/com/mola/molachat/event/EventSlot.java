package com.mola.molachat.event;

import com.mola.molachat.event.event.InboundEvent;
import com.mola.molachat.event.event.OutboundEvent;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 入站插槽
 * @date : 2020-12-05 19:07
 **/
public interface EventSlot<T extends InboundEvent> {

    /**
     * 插槽，用于拦截输入
     * @param inboundEvent
     * @return
     */
    OutboundEvent intercept(T inboundEvent);
}
