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

    private static final String TURING_API_URL = "http://openapi.turingapi.com/openapi/api/v2";
    private static final String REQ_TYPE_KEY = "reqType";
    private static final String PERCEPTION_KEY = "perception";
    private static final String INPUT_TEXT_KEY = "inputText";
    private static final String TEXT_KEY = "text";
    private static final String USER_INFO_KEY = "userInfo";
    private static final String API_KEY = "apiKey";
    private static final String USER_ID_KEY = "userId";
    private static final String RESULTS_KEY = "results";
    private static final String VALUES_KEY = "values";
    private static final String MESSAGE_NULL_MSG = "message is null";
    private static final String ROBOT_CHATTER_NULL_MSG = "robotChatter is null";
    private static final String REMOTE_ROBOT_ERROR_MSG = "RemoteRobotChatHandler error ";

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            Assert.notNull(messageReceiveEvent.getMessage(), MESSAGE_NULL_MSG);
            Assert.notNull(robotChatter, ROBOT_CHATTER_NULL_MSG);
            JSONObject body = assembleBody(messageReceiveEvent.getMessage().getContent(), robotChatter.getApiKey());
            String res = HttpUtil.INSTANCE.post(TURING_API_URL,
                    body, 1000);
            JSONObject jsonObject = JSONObject.parseObject(res);
            String text = jsonObject.getJSONArray(RESULTS_KEY)
                    .getJSONObject(0)
                    .getJSONObject(VALUES_KEY)
                    .getString(TEXT_KEY);
            messageSendAction.setResponsesText(text);
        } catch (Exception e) {
            log.error(REMOTE_ROBOT_ERROR_MSG + JSONObject.toJSONString(messageReceiveEvent), e);
            messageSendAction.setSkip(Boolean.TRUE);
        }
        return messageSendAction;
    }

    private JSONObject assembleBody(String text, String apiKey) {
        JSONObject body = new JSONObject();
        body.put(REQ_TYPE_KEY, 0);
        JSONObject perception = new JSONObject();
        JSONObject inputText = new JSONObject();
        inputText.put(TEXT_KEY, text);
        perception.put(INPUT_TEXT_KEY, inputText);
        JSONObject userInfo = new JSONObject();
        userInfo.put(API_KEY,apiKey);
        userInfo.put(USER_ID_KEY,USER_ID);
        body.put(PERCEPTION_KEY, perception);
        body.put(USER_INFO_KEY, userInfo);
        return body;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }
}
