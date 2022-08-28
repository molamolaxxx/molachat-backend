package com.mola.molachat.service.impl;

import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.FileMessage;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.entity.dto.SessionDTO;
import com.mola.molachat.event.action.BaseAction;
import com.mola.molachat.robot.bus.RobotEventBus;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.RobotService;
import com.mola.molachat.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:58
 **/
@Service
@Slf4j
public class RobotServiceImpl implements RobotService {

    @Resource
    private ChatterFactoryInterface chatterFactory;
    
    @Resource
    private SessionService sessionService;

    @Resource
    private RobotService robotService;

    @Resource
    private ChatterService chatterService;

    @Resource
    private RobotEventBus robotEventBus;

    @Override
    public void onReceiveMessage(Message message, String sessionId, RobotChatter robot) {
        if (null != message && message.getChatterId().equals(robot.getId())) {
            return;
        }
        if ("common-session".equals(sessionId)) {
            return;
        }
        if (message instanceof FileMessage) {
            return;
        }
        MessageReceiveEvent messageReceiveEvent = new MessageReceiveEvent();
        messageReceiveEvent.setMessage(message);
        messageReceiveEvent.setRobotChatter(robot);
        messageReceiveEvent.setSessionId(sessionId);
        BaseAction action = robotEventBus.handler(messageReceiveEvent);
        if (!(action instanceof MessageSendAction)) {
            return;
        }
        MessageSendAction messageSendAction = (MessageSendAction) action;
        if (messageSendAction.getSkip() || StringUtils.isEmpty(messageSendAction.getResponsesText())) {
            log.error("机器人跳过回复，内容 = {}", messageSendAction.getResponsesText());
            return;
        }
        // 消息构建
        Message msg = new Message();
        msg.setContent(messageSendAction.getResponsesText());
        msg.setChatterId(robot.getId());

        // 1、查询session，没有则创建
        SessionDTO session = sessionService.findSession(sessionId);
        // 2、向session发送消息
        sessionService.insertMessage(session.getSessionId(), msg);
    }

    @Override
    public Boolean isRobot(String chatterId) {
        if (StringUtils.isEmpty(chatterId)) {
            return false;
        }
        final Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter) {
            return false;
        }
        return chatter instanceof RobotChatter;
    }

    @Override
    public RobotChatter getRobot(String appKey) {
        if (StringUtils.isEmpty(appKey)) {
            return null;
        }
        final Chatter chatter = chatterFactory.select(appKey);
        if (null == chatter || !(chatter instanceof RobotChatter)) {
            return null;
        }
        return (RobotChatter) chatter;
    }


    @Override
    public void pushMessage(String appKey, String toChatterId, String content) {
        // 查询发送方
        RobotChatter robot = robotService.getRobot(appKey);
        Assert.notNull(robot, "sender is not exist");
        // 查询接收方
        ChatterDTO receiver = chatterService.selectById(toChatterId);
        Assert.notNull(receiver, "receiver is not exist");
        // 消息构建
        Message msg = new Message();
        msg.setContent(content);
        msg.setChatterId(robot.getId());

        // 1、查询session，没有则创建
        SessionDTO session = sessionService.findOrCreateSession(appKey, toChatterId);
        // 2、向session发送消息
        sessionService.insertMessage(session.getSessionId(), msg);
    }
}
