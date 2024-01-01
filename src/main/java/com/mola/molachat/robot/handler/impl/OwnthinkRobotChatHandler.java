package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.RobotEventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.event.MessageSendEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.common.utils.HttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 思知远程聊天处理器，调用httpClient
 * @date : 2022-08-27 11:47
 **/
@Component
@Slf4j
public class OwnthinkRobotChatHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    @Resource
    private RobotEventBus robotEventBus;

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            Assert.notNull(messageReceiveEvent.getMessage(), "message is null");
            Message message = messageReceiveEvent.getMessage();
            Assert.notNull(robotChatter, "robotChatter is null");
            JSONObject body = new JSONObject();
            body.put("spoken", message.getContent());
            body.put("appid", robotChatter.getApiKey());
            body.put("userid", message.getChatterId());
            String res = HttpUtil.INSTANCE.post("https://api.ownthink.com/bot", body ,10000);
            JSONObject jsonObject = JSONObject.parseObject(res);
            JSONObject info = jsonObject.getJSONObject("data")
                    .getJSONObject("info");
            String text = info.getString("text");

            text = StringUtils.replace(text, "小思机器人", robotChatter.getName());
            text = StringUtils.replace(text, "机器人小思", robotChatter.getName());
            text = StringUtils.replace(text, "小思", robotChatter.getName());
            messageSendAction.setResponsesText(text);

            //启发功能
            JSONArray heuristic = info.getJSONArray("heuristic");
            if (null != heuristic) {
                log.info("启发文案：" + heuristic.toJSONString());
                for (int i = 0; i < heuristic.size(); i++) {
                    String heuristicText = heuristic.getString(i);
                    Message msg = new Message();
                    msg.setContent(heuristicText);
                    msg.setChatterId(robotChatter.getId());
                    msg.setSessionId(messageReceiveEvent.getSessionId());
                    MessageSendEvent messageSendEvent = new MessageSendEvent();
                    messageSendEvent.setMessage(msg);
                    messageSendEvent.setRobotChatter(messageReceiveEvent.getRobotChatter());
                    messageSendEvent.setSessionId(messageReceiveEvent.getSessionId());
                    messageSendEvent.setDelayTime(1000L + 100 * i);
                    robotEventBus.handler(messageSendEvent);
                }
            }
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
