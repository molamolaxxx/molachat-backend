package com.mola.molachat.common.event.base;

import com.mola.molachat.common.event.EventBus;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:28
 **/
@Slf4j
public class BaseEventBusRegistry<T extends EventBus>{

    /**
     * @Key id
     * @Value EventBus
     */
    private Map<String, T> chatterId2EventBusMap = new ConcurrentHashMap<>();

    public void register(String id, T eventBus) {
        if (chatterId2EventBusMap.keySet().contains(id)) {
            log.warn("总线注册表中已经存在id = {}", id);
            return;
        }
        chatterId2EventBusMap.putIfAbsent(id, eventBus);
    }

    public T getEventBus(String id) {
        return chatterId2EventBusMap.get(id);
    }
}
