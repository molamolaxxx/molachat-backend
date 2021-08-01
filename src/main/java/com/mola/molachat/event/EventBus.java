package com.mola.molachat.event;

import com.mola.molachat.event.event.InboundEvent;
import com.mola.molachat.event.event.OutboundEvent;

import java.util.List;

public interface EventBus {

    /**
     * 处理入站事件
     * @param inboundEvent
     * @return
     */
    List<OutboundEvent> handleInbound(InboundEvent inboundEvent);

    /**
     * 处理出站事件
     * @param outboundEvent
     */
    void handleOutbound(OutboundEvent outboundEvent);

    /**
     * 添加事件处理的插槽
     * @param eventSlot
     */
    void addSlot(EventSlot eventSlot);

    /**
     * 添加出站事件的处理器
     * @param outboundEventHandler
     */
    void addHandler(OutboundEventHandler outboundEventHandler);
}
