package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.data.OtherDataInterface;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.entity.dto.SessionDTO;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.GptRobotEventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.service.ServerService;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.service.http.HttpService;
import com.mola.molachat.utils.RandomUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: openai gpt3 连续对话处理器
 * @date : 2023-02-03 02:20
 **/
@Component
@Slf4j
public class Gpt3RobotHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    @Resource
    private SessionService sessionService;

    @Resource
    private ServerService serverService;

    @Resource
    private GptRobotEventBus gptRobotEventBus;

    @Resource
    private OtherDataInterface otherDataInterface;

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
        // 默认主账号
        String usedAppKey = robotChatter.getApiKey();

        Set<String> gpt3ChildTokens = otherDataInterface.getGpt3ChildTokens();
        if (gpt3ChildTokens.size() != 0) {
            usedAppKey = RandomUtils.getRandomElement(gpt3ChildTokens);
        }
        // 失败重试
        for (int i = 0; i < 12; i++) {
            // 子账号多次都失败，换成主账号，移除子账号
            if (i > 8 && !StringUtils.equals(usedAppKey, robotChatter.getApiKey())) {
                log.error("sub api key error retry failed all time, switch main remove sub, sub api key = " + usedAppKey);
                if (gpt3ChildTokens.contains(usedAppKey)) {
                    final String usedAppKeyFinal = usedAppKey;
                    otherDataInterface.operateGpt3ChildTokens((tokens) -> tokens.remove(usedAppKeyFinal));
                }
                usedAppKey = robotChatter.getApiKey();
            }
            try {
                // headers
                List<Header> headers = new ArrayList<>();
                headers.add(new BasicHeader("Content-Type", "application/json"));
                headers.add(new BasicHeader("Accept-Encoding", "gzip,deflate"));
                headers.add(new BasicHeader("Authorization", "Bearer " + usedAppKey));
                // prompt 拼接最近20条历史记录
                JSONObject body = new JSONObject();
                body.put("model", "text-davinci-003");
                body.put("max_tokens", 1920);
                body.put("temperature", 0.9);
                body.put("top_p", 1);
                body.put("frequency_penalty", 0);
                body.put("presence_penalty", 0.6);
                body.put("stop", JSONObject.parseArray("[\" Human:\", \" AI:\"]"));
                body.put("prompt", getPrompt(messageReceiveEvent));
                String res = HttpService.INSTANCE.post("https://api.openai.com/v1/completions", body, 300000, headers.toArray(new Header[]{}));
                JSONObject jsonObject = JSONObject.parseObject(res);
                Assert.isTrue(jsonObject.containsKey("choices"), "choices is empty");
                JSONArray choices = jsonObject.getJSONArray("choices");
                for (Object choice : choices) {
                    JSONObject inner = (JSONObject) choice;
                    String text = inner.getString("text");
                    if (StringUtils.isBlank(text)) {
                        continue;
                    }
                    if (text.startsWith("\n")) {
                        text = text.substring(1);
                    }
                    messageSendAction.setResponsesText(text);
                }
            } catch (Exception e) {
                log.error("RemoteRobotChatHandler Gpt3RobotHandler error retry, time = " + i + " event:" + JSONObject.toJSONString(messageReceiveEvent), e);
                continue;
            }
            log.info("RemoteRobotChatHandler Gpt3RobotHandler success, apiKey=" +  usedAppKey + " action:" + JSONObject.toJSONString(messageSendAction));
            return messageSendAction;
        }
        try {
            log.error("RemoteRobotChatHandler Gpt3RobotHandler error retry failed all time , event:" + JSONObject.toJSONString(messageReceiveEvent));
            ChatServer server = serverService.selectByChatterId(messageReceiveEvent.getMessage().getChatterId());
            // 不可用告警
            String alertText = "刚刚开小差了, 请重试";
            messageSendAction.setResponsesText(alertText);
        } catch (Exception exception) {
            // ignore exception
        }
        return messageSendAction;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    /**
     * 为了使ai理解上下文，需要将历史对话拼接，传递给openai
     * @param messageReceiveEvent
     * @return
     */
    private String getPrompt(MessageReceiveEvent messageReceiveEvent) {
        String sessionId = messageReceiveEvent.getSessionId();
        SessionDTO session = sessionService.findSession(sessionId);
        Assert.notNull(session, "session is null in getPrompt，" + sessionId);
        List<Message> messageList = session.getMessageList();
        if (CollectionUtils.isEmpty(messageList)) {
            return messageReceiveEvent.getMessage().getContent();
        }
        StringBuilder prompt = new StringBuilder();
        int start = messageList.size() > 20 ? messageList.size() - 20 : 0;
        for (int i = start; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (StringUtils.isNotBlank(message.getContent())) {
                String content = message.getContent();
                if (content.length() > 200) {
                    content = content.substring(0, 200);
                    content +=  "\n";
                }
                prompt.append(content);
                prompt.append("\n");
            }
        }
        return prompt.toString();
    }
}
