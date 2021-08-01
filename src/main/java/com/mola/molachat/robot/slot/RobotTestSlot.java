package com.mola.molachat.robot.slot;

import com.mola.molachat.event.EventSlot;
import com.mola.molachat.event.event.OutboundEvent;
import com.mola.molachat.robot.event.inbound.SingleChatterInboundEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-05 20:40
 **/
@Component
@Slf4j
public class RobotTestSlot implements EventSlot<SingleChatterInboundEvent> {

    @Override
    public OutboundEvent intercept(SingleChatterInboundEvent inboundEvent) {
        log.info("机器人测试插槽");
        return null;
    }
}
