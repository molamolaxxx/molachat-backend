package com.mola.molachat.robot.bus;


import com.mola.molachat.MolachatApplicationTests;
import com.mola.molachat.robot.event.inbound.SingleChatterInboundEvent;
import com.mola.molachat.robot.event.outbound.SingleChatterOutboundEvent;
import com.mola.molachat.robot.handler.RobotSingleChatterHandler;
import com.mola.molachat.robot.handler.RobotTestHandler;
import com.mola.molachat.robot.slot.RobotSingleChatterSlot;
import com.mola.molachat.robot.slot.RobotTestSlot;
import org.junit.Test;

import javax.annotation.Resource;

public class RobotEventBusTest extends MolachatApplicationTests {

    @Resource
    private RobotTestSlot robotTestSlot;

    @Resource
    private RobotSingleChatterSlot robotSingleChatterSlot;

    @Resource
    private RobotSingleChatterHandler robotSingleChatterHandler;

    @Resource
    private RobotTestHandler robotTestHandler;

    @Test
    public void test(){
        RobotEventBus robotEventBus = new RobotEventBus();
        // slot
        robotEventBus.addSlot(robotTestSlot);
        robotEventBus.addSlot(robotSingleChatterSlot);
        // handler
        robotEventBus.addHandler(robotTestHandler);
        robotEventBus.addHandler(robotSingleChatterHandler);
        // test
        robotEventBus.handleInbound(new SingleChatterInboundEvent());
        robotEventBus.handleOutbound(new SingleChatterOutboundEvent());
    }
}