package com.mola.molachat.robot.handler;

import com.mola.molachat.event.OutboundEventHandler;
import com.mola.molachat.robot.event.outbound.SingleChatterOutboundEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 用于指定机器人向指定用户发送单聊信息
 * @date : 2020-12-14 14:44
 **/
@Component
@Slf4j
public class RobotSingleChatterHandler implements OutboundEventHandler<SingleChatterOutboundEvent> {
    @Override
    public void handle(SingleChatterOutboundEvent outboundEvent) {
        log.info("机器人单聊出栈处理器");
    }
}
