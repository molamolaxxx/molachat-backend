package com.mola.molachat.event.base;

import com.mola.molachat.event.EventBus;
import com.mola.molachat.event.EventSlot;
import com.mola.molachat.event.OutboundEventHandler;
import com.mola.molachat.event.event.InboundEvent;
import com.mola.molachat.event.event.OutboundEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-05 22:33
 **/
@Slf4j
public class BaseEventBus implements EventBus {

    private static final String SLOT_METHOD_NAME = "intercept";
    private static final String HANDLER_METHOD_NAME = "handle";
    private static final Set<String> BASE_EVENT_CLASS_NAME = new HashSet(){
        {
            add("InboundEvent");
            add("OutboundEvent");
        }
    };
    /**
     * 事件插槽，负责处理入站事件，转化成出站事件
     * @Key 入站事件全限定名
     * @Value 处理对应事件的插槽对象
     */
    private Map<String, Set<EventSlot>> slotChainMap = new ConcurrentHashMap<>();

    /**
     * 负责处理出站事件的handler
     * @Key 出站事件全限定名
     * @Value 处理对应事件的handler
     */
    private Map<String, Set<OutboundEventHandler>> handlerChainMap = new ConcurrentHashMap<>();

    /**
     * @param inboundEvent
     * @return
     */
    @Override
    public List<OutboundEvent> handleInbound(InboundEvent inboundEvent) {
        Set<EventSlot> eventSlots = slotChainMap.get(inboundEvent.getClass().getName());
        List<OutboundEvent> result = new ArrayList<>();
        for (EventSlot eventSlot : eventSlots) {
            result.add(eventSlot.intercept(inboundEvent));
        }
        return result;
    }

    @Override
    public void handleOutbound(OutboundEvent outboundEvent) {
        Set<OutboundEventHandler> handlers = handlerChainMap.get(outboundEvent.getClass().getName());
        for (OutboundEventHandler handler : handlers) {
            handler.handle(outboundEvent);
        }
    }

    @Override
    public void addSlot(EventSlot slot) {
        if (null == slot) {
            log.warn("当前slot为空");
            return;
        }
        // 获取插槽对应的事件名
        List<String> eventNames = getEventNames(slot.getClass(), SLOT_METHOD_NAME);
        // 注册
        for (String eventName : eventNames) {
            slotChainMap.putIfAbsent(eventName, new HashSet<>());
            Set<EventSlot> eventSlots = slotChainMap.get(eventName);
            eventSlots.add(slot);
        }
    }

    @Override
    public void addHandler(OutboundEventHandler handler) {
        if (null == handler) {
            log.warn("当前handler为空");
            return;
        }
        // 获取处理器对应的事件名
        List<String> eventNames = getEventNames(handler.getClass(), HANDLER_METHOD_NAME);
        // 注册
        for (String eventName : eventNames) {
            handlerChainMap.putIfAbsent(eventName, new HashSet<>());
            Set<OutboundEventHandler> outboundEventHandlers = handlerChainMap.get(eventName);
            outboundEventHandlers.add(handler);
        }
    }

    /**
     * 获取handler与slot对应事件的类型
     * @param clazz
     * @return
     */
    private List<String> getEventNames(Class clazz, String methodName) {
        List<String> result = new ArrayList<>();
        for (Method declaredMethod : clazz.getDeclaredMethods()) {
            if (!methodName.equals(declaredMethod.getName())) {
                continue;
            }
            List<String> eventNames = Arrays.stream(declaredMethod.getParameterTypes())
                    .map(e -> e.getName()).collect(Collectors.toList());
            Assert.isTrue(eventNames.size() == 1, "参数列表长度不唯一");
            String eventName = eventNames.get(0);
            if (BASE_EVENT_CLASS_NAME.contains(eventName.substring(eventName.lastIndexOf('.')+1))) {
                continue;
            }
            result.add(eventName);
        }
        return result;
    }

    public static void main(String[] args) {

    }
}
