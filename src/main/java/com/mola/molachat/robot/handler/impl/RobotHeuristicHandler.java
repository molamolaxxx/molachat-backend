package com.mola.molachat.robot.handler.impl;

import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.robot.action.EmptyAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageSendEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.SessionSolution;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 机器人主动回答
 * @date : 2022-08-28 19:27
 **/
@Component
public class RobotHeuristicHandler implements IRobotEventHandler<MessageSendEvent, EmptyAction> {

    private ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(10);

    @Resource
    private SessionSolution sessionSolution;


    @Resource
    private SessionService sessionService;

    @Override
    public EmptyAction handler(MessageSendEvent messageSendEvent) {
        // 消息构建，发送延时任务
        scheduledExecutorService.schedule(() -> {
            // 1、查询session，没有则创建
            SessionDTO session = sessionService.findSession(messageSendEvent.getSessionId());
            // 2、向session发送消息
            sessionSolution.insertMessage(session.getSessionId(), messageSendEvent.getMessage());
        }, messageSendEvent.getDelayTime(), TimeUnit.MILLISECONDS);
        return new EmptyAction();
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageSendEvent.class;
    }


    public static MessageSendEvent getHeuristicEvent(String content, RobotChatter robotChatter, String sessionId) {
        Message msg = new Message();
        msg.setContent(content);
        msg.setSessionId(sessionId);
        msg.setChatterId(robotChatter.getId());
        MessageSendEvent messageSendEvent = new MessageSendEvent();
        messageSendEvent.setMessage(msg);
        messageSendEvent.setRobotChatter(robotChatter);
        messageSendEvent.setSessionId(sessionId);
        messageSendEvent.setDelayTime(1000L);
        return messageSendEvent;
    }
}
