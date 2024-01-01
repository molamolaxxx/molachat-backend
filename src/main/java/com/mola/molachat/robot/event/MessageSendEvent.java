package com.mola.molachat.robot.event;

import com.mola.molachat.session.model.Message;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 主动发起的聊天
 * @date : 2022-08-28 19:20
 **/
@Data
public class MessageSendEvent extends BaseRobotEvent{

    /**
     * 触发的消息
     */
    private Message message;

    /**
     * 延迟触发时间
     */
    private long delayTime = 1000L;
}
