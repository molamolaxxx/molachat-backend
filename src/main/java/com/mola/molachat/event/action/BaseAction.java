package com.mola.molachat.event.action;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:22
 **/
@Data
public class BaseAction {

    /**
     * 事件唯一id
     */
    private String eventId;

    /**
     * 事件名
     */
    private String eventName;

    /**
     * 优先级，返回最大的action
     */
    private Integer order = Integer.MIN_VALUE;

    /**
     * 结束时间
     */
    private long finishTime = System.currentTimeMillis();

    /**
     * 携带参数
     */
    private Map<String, String> feature = new HashMap<>();
}
