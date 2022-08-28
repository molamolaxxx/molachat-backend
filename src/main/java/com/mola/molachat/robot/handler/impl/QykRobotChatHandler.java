package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 青云客远程聊天处理器，调用httpClient
 * @date : 2022-08-27 11:47
 **/
@Slf4j
public class QykRobotChatHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            Assert.notNull(robotChatter, "robotChatter is null");
            String url = String.format("http://api.qingyunke.com/api.php?key=free&appid=0&msg=%s", messageReceiveEvent.getMessage());
            String res = HttpService.INSTANCE.get(url,10000);
            JSONObject jsonObject = JSONObject.parseObject(res);
            messageSendAction.setResponsesText(jsonObject.getString("content"));
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler error " + JSONObject.toJSONString(messageReceiveEvent), e);
            messageSendAction.setSkip(Boolean.TRUE);
        }
        return messageSendAction;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }
}
