package com.mola.molachat.robot.handler;

import com.mola.molachat.event.OutboundEventHandler;
import com.mola.molachat.robot.event.outbound.SingleChatterOutboundEvent;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-14 14:16
 **/
@Component
public class RobotTestHandler implements OutboundEventHandler<SingleChatterOutboundEvent> {

    @Override
    public void handle(SingleChatterOutboundEvent outboundEvent) {

    }
}
