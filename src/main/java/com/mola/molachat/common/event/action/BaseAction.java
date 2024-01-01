package com.mola.molachat.common.event.action;

import com.mola.molachat.robot.action.EmptyAction;
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
     * 结束时间
     */
    private long finishTime = System.currentTimeMillis();

    /**
     * 最后一个执行
     */
    private Boolean finalExec = Boolean.FALSE;

    /**
     * 跳过
     */
    private Boolean skip = Boolean.FALSE;

    /**
     * 携带参数
     */
    private Map<String, String> feature = new HashMap<>();

    public static BaseAction empty() {
        return new EmptyAction();
    }
}
