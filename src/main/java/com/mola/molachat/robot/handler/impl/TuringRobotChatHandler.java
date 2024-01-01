package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.common.utils.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 图灵远程聊天处理器，调用httpClient
 * @date : 2022-08-27 11:47
 **/
@Slf4j
public class TuringRobotChatHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    private final static String USER_ID = "287686";

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            Assert.notNull(messageReceiveEvent.getMessage(), "message is null");
            Assert.notNull(robotChatter, "robotChatter is null");
            JSONObject body = assembleBody(messageReceiveEvent.getMessage().getContent(), robotChatter.getApiKey());
            String res = HttpUtil.INSTANCE.post("http://openapi.turingapi.com/openapi/api/v2",
                    body, 1000);
            JSONObject jsonObject = JSONObject.parseObject(res);
            String text = jsonObject.getJSONArray("results")
                    .getJSONObject(0)
                    .getJSONObject("values")
                    .getString("text");
            messageSendAction.setResponsesText(text);
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler error " + JSONObject.toJSONString(messageReceiveEvent), e);
            messageSendAction.setSkip(Boolean.TRUE);
        }
        return messageSendAction;
    }

    private JSONObject assembleBody(String text, String apiKey) {
        JSONObject body = new JSONObject();
        body.put("reqType", 0);
        JSONObject perception = new JSONObject();
        JSONObject inputText = new JSONObject();
        inputText.put("text", text);
        perception.put("inputText", inputText);
        JSONObject userInfo = new JSONObject();
        userInfo.put("apiKey",apiKey);
        userInfo.put("userId",USER_ID);
        body.put("perception", perception);
        body.put("userInfo", userInfo);
        return body;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }
}
