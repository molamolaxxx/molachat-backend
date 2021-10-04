package com.mola.molachat.robot.slot;

import com.mola.molachat.event.EventSlot;
import com.mola.molachat.event.event.OutboundEvent;
import com.mola.molachat.robot.event.inbound.SingleChatterInboundEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 单聊插槽，用于将单聊事件转换成出栈事件
 * @date : 2020-12-14 14:44
 **/
@Component
@Slf4j
public class RobotSingleChatterSlot implements EventSlot<SingleChatterInboundEvent> {
    @Override
    public OutboundEvent intercept(SingleChatterInboundEvent inboundEvent) {
        log.info("机器人单聊插槽");
        return null;
    }
}
