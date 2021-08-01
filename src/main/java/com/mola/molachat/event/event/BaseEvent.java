package com.mola.molachat.event.event;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 基准事件
 * @date : 2020-12-05 19:08
 **/
@Data
public class BaseEvent {

    /**
     * 事件唯一id
     */
    private String eventId;

    /**
     * 事件名
     */
    private String eventName;

    /**
     * 事件描述
     */
    private String eventDesc;

    /**
     * 开始时间
     */
    private long beginTime = System.currentTimeMillis();

    /**
     * 携带参数
     */
    private Map<String, String> feature = new HashMap<>();
}
