package com.mola.molachat.event;

import com.mola.molachat.event.action.BaseAction;
import com.mola.molachat.event.event.BaseEvent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 总线管理器
 * @date : 2022-09-12 02:17
 **/
@Component
public class EventBusRegistry<E extends BaseEvent, A extends BaseAction> {

    @Resource
    private Map<String, EventBus<E, A>> eventBusMap;

    /**
     * 获取eventbus
     * @param name
     * @return
     */
    public EventBus<E, A> getEventBus(String name) {
        return eventBusMap.get(name);
    }
}
