package com.mola.molachat.robot.event;

import com.mola.molachat.event.action.BaseAction;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:32
 **/
@Data
public class MessageSendAction extends BaseAction {

    /**
     * 优先级，返回最大的action
     */
    private Integer order = Integer.MIN_VALUE;
}
