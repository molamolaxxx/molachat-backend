package com.mola.molachat.robot.event.inbound;

import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.Session;
import com.mola.molachat.event.event.InboundEvent;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 单聊event
 * @date : 2020-12-05 19:20
 **/
@Data
public class MutiChatterInboundEvent extends InboundEvent {

    /**
     * 发送者的id
     */
    private String senderId;

    /**
     * 包含的消息
     */
    private Message message;

    /**
     * 机器人与发送者的session
     */
    private Session session;
}
