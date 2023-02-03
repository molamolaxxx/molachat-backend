package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.entity.FileMessage;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.entity.dto.SessionDTO;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.GptRobotEventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.event.MessageSendEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.service.http.HttpService;
import com.mola.molachat.utils.PatternUtils;
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
    private GptRobotEventBus gptRobotEventBus;

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            // headers
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("Content-Type", "application/json"));
            headers.add(new BasicHeader("Accept-Encoding", "gzip,deflate"));
//            headers.add(new BasicHeader("Content-Length", "1024"));
//            headers.add(new BasicHeader("Transfer-Encoding", "chunked"));
            headers.add(new BasicHeader("Authorization", "Bearer " + robotChatter.getApiKey()));
            // prompt 拼接最近20条历史记录
            JSONObject body = new JSONObject();
            body.put("model", "text-davinci-003");
            body.put("max_tokens", 2048);
            body.put("temperature", 1);
            body.put("top_p", 1);
            body.put("frequency_penalty", 0);
            body.put("presence_penalty", 0.6);
            body.put("stop", JSONObject.parseArray("[\" Human:\", \" AI:\"]"));
            body.put("prompt", getPrompt(messageReceiveEvent));
            String res = HttpService.INSTANCE.post("https://api.openai.com/v1/completions", body, 120000, headers.toArray(new Header[]{}));
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
                // markdown图片格式解析
//                List<MessageSendEvent>  messageSendEvents = getFileSendAction(text, messageReceiveEvent);
//                if (!CollectionUtils.isEmpty(messageSendEvents)) {
//                    for (MessageSendEvent MessageSendEvent : messageSendEvents) {
//                        gptRobotEventBus.handler(MessageSendEvent);
//                    }
//                }
                messageSendAction.setResponsesText(text);
            }
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler Gpt3RobotHandler error " + JSONObject.toJSONString(messageReceiveEvent), e);
            messageSendAction.setSkip(Boolean.TRUE);
        }
        return messageSendAction;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    /**
     * chatgpt会以markdown的形式发送图片，所以需要解析
     * @param text
     * @return
     */
    private List<MessageSendEvent> getFileSendAction(String text, MessageReceiveEvent messageReceiveEvent) {
        try {
            List<String> patternAll = PatternUtils.matchAll(text, PatternUtils.patternAll);
            if (CollectionUtils.isEmpty(patternAll)) {
                return null;
            }
            String sendText = text;
            List<MessageSendEvent> events = new ArrayList<>();
            for (String mdImgUrl : patternAll) {
                // 处理text
                sendText = StringUtils.remove(sendText, mdImgUrl);
                sendText = StringUtils.replace(sendText, "\n", " ");
                // 提取fileName
                String fileName = PatternUtils.match(mdImgUrl, PatternUtils.patternFileName);
                if (StringUtils.isBlank(fileName)) {
                    continue;
                }
                fileName = fileName.substring(2, fileName.length() -1);
                System.out.println(fileName);
                String patternUrl = PatternUtils.match(mdImgUrl, PatternUtils.patternUrl);
                if (StringUtils.isBlank(patternUrl)) {
                    continue;
                }
                //创建message
                FileMessage fileMessage = new FileMessage();
                fileMessage.setFileName(fileName);
                fileMessage.setFileStorage("1024");
                fileMessage.setUrl(patternUrl);
                fileMessage.setSnapshotUrl(patternUrl);
                fileMessage.setChatterId(messageReceiveEvent.getRobotChatter().getId());
                MessageSendEvent messageSendEvent = new MessageSendEvent();
                messageSendEvent.setMessage(fileMessage);
                messageSendEvent.setRobotChatter(messageReceiveEvent.getRobotChatter());
                messageSendEvent.setSessionId(messageReceiveEvent.getSessionId());
                messageSendEvent.setDelayTime(1000L + (long) (Math.random() * 1000));
                // 判断是否是群聊
//                if (sessionId.equals("common-session")) {
//                    fileMessage.setCommon(true);
//                }
                events.add(messageSendEvent);
            }
            return events;
        }catch (Exception e) {
            log.error("getFileSendAction failed , " + text);
            return null;
        }

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
                    content +=  "...";
                }
                prompt.append(content);
                prompt.append("\n");
            }
        }
        return prompt.toString();
    }
}
