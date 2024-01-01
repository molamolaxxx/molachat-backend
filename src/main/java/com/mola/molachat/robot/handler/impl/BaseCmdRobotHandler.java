package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 翻译
 * @date : 2022-09-12 15:57
 **/
@Slf4j
public abstract class BaseCmdRobotHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    @Override
    public MessageSendAction handler(MessageReceiveEvent baseEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            Message message = baseEvent.getMessage();
            String content = message.getContent();
            String[] splitRes = StringUtils.split(content, " ");
            if (null == splitRes || splitRes.length < 1 || !getCommand().equals(splitRes[0])) {
                messageSendAction.setSkip(Boolean.TRUE);
                return messageSendAction;
            }
            StringBuilder stringBuilder = new StringBuilder(content);
            CommandInputEvent commandInputEvent = new CommandInputEvent(baseEvent);
            commandInputEvent.setCommandInput(StringUtils.trim(stringBuilder.substring(getCommand().length())));
            messageSendAction.setResponsesText(executeCommand(commandInputEvent));
            messageSendAction.setFinalExec(Boolean.TRUE);
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler error " + JSONObject.toJSONString(baseEvent), e);
            messageSendAction.setSkip(Boolean.TRUE);
        }
        return messageSendAction;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    /**
     * 获取命令
     * @return
     */
    public abstract String getCommand();

    /**
     * 获取描述
     * @return
     */
    public abstract String getDesc();

    /**
     * 执行命令
     * @param baseEvent
     * @return
     */
    protected abstract String executeCommand(CommandInputEvent baseEvent);
}
