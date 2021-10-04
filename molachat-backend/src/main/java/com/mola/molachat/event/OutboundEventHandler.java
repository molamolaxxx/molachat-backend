package com.mola.molachat.event;

import com.mola.molachat.event.event.OutboundEvent;

public interface OutboundEventHandler<T extends OutboundEvent> {

    /**
     * 处理入站总线生成的出站事件，调用service
     * @param outboundEvent
     */
    void handle(T outboundEvent);
}
