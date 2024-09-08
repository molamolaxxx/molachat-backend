package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.config.AppConfig;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.GptRobotEventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.robot.solution.CmdProxyInvokeSolution;
import com.mola.molachat.common.utils.HttpUtil;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.common.utils.RandomUtils;
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
import java.util.Map;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: openai gpt3 连续对话处理器
 * @date : 2023-02-03 02:20
 **/
@Component
@Slf4j
public class ChatGptRobotHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    @Resource
    private SessionService sessionService;

    @Resource
    private ServerService serverService;

    @Resource
    private ChatterService chatterService;

    @Resource
    private GptRobotEventBus gptRobotEventBus;

    @Resource
    private ChatGptSolution chatGptSolution;

    @Resource
    private CmdProxyInvokeSolution cmdProxyInvokeSolution;

    @Resource
    private KvUtils kvUtils;

    @Resource
    private AppConfig appConfig;

    public static final String ALERT_TEXT = "账户已失效";

    public static final String PROXY_ERROR = "代理异常, 请重试";

    private static final int RETRY_TIME = 12;

    private static final int CHANGE_API_KEY_TIME = 8;

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
        Message message = messageReceiveEvent.getMessage();
        // 默认主账号
        String usedApiKey = robotChatter.getApiKey();
        Set<String> gpt3ChildTokens = chatGptSolution.fetchApiKeys();
        if (gpt3ChildTokens.size() != 0) {
            usedApiKey = RandomUtils.getRandomElement(gpt3ChildTokens);
        }
        // 失败重试
        for (int i = 0; i < RETRY_TIME; i++) {
            // 子账号多次都失败，换成主账号，移除子账号
            if (i > CHANGE_API_KEY_TIME && !StringUtils.equals(usedApiKey, robotChatter.getApiKey())) {
                log.error("sub api key error retry failed all time, switch main remove sub, sub api key = " + usedApiKey);
                if (gpt3ChildTokens.contains(usedApiKey)) {
                    chatGptSolution.removeApiKey(usedApiKey);
                }
                usedApiKey = robotChatter.getApiKey();
            }
            try {
                // headers
                List<Header> headers = new ArrayList<>();
                headers.add(new BasicHeader("Content-Type", "application/json"));
                headers.add(new BasicHeader("Authorization", "Bearer " + usedApiKey));
                // prompt 拼接最近20条历史记录
                JSONObject body = new JSONObject();
                String modelName = kvUtils.getStringOrDefault("chatGptModelName", "Atom-13B-Chat");
                body.put("model", modelName);
                List<Map<String, String>> prompt = getPrompt(messageReceiveEvent);
                log.info(JSONObject.toJSONString(prompt));
                body.put("messages", prompt);
                body.put("stream", false);
                String res = HttpUtil.INSTANCE.post("https://api.atomecho.cn/v1/chat/completions",
                        body, 300000, headers.toArray(new Header[]{}));

                JSONObject jsonObject = JSONObject.parseObject(res);
                Assert.isTrue(jsonObject.containsKey("choices"), "choices is empty");
                JSONArray choices = jsonObject.getJSONArray("choices");
                for (Object choice : choices) {
                    JSONObject inner = (JSONObject) choice;
                    JSONObject object = inner.getJSONObject("message");
                    String text = object.getString("content");
                    if (StringUtils.isBlank(text)) {
                        continue;
                    }
                    if (text.startsWith("\n")) {
                        text = text.substring(1);
                    }
                    messageSendAction.setResponsesText(text);
                }
            } catch (Exception e) {
                log.error("RemoteRobotChatHandler ChatGptRobotHandler error retry, time = " + i + " event:" + JSONObject.toJSONString(messageReceiveEvent), e);
                if (StringUtils.containsIgnoreCase(e.getMessage(), "You exceeded your current quota")) {
                    chatGptSolution.removeApiKey(usedApiKey);
                    // 不可用告警
                    messageSendAction.setResponsesText(ALERT_TEXT);
                    return messageSendAction;
                }
                // 网络失败
                if ((StringUtils.containsIgnoreCase(e.getMessage(), "Network is unreachable")
                        || StringUtils.containsIgnoreCase(e.getMessage(), "Bad Gateway")
                        || StringUtils.containsIgnoreCase(e.getMessage(), "Connection refused"))
                        && i >= CHANGE_API_KEY_TIME / 2) {
                    // 不可用告警
                    messageSendAction.setResponsesText(ALERT_TEXT);
                    return messageSendAction;
                }
                continue;
            }
            log.info("RemoteRobotChatHandler ChatGptRobotHandler success, apiKey=" +  usedApiKey + " action:" + JSONObject.toJSONString(messageSendAction));
            return messageSendAction;
        }
        try {
            log.error("RemoteRobotChatHandler ChatGptRobotHandler error retry failed all time , event:" + JSONObject.toJSONString(messageReceiveEvent));
            // 不可用告警
            messageSendAction.setResponsesText(ALERT_TEXT);
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
    private List<Map<String, String>> getPrompt(MessageReceiveEvent messageReceiveEvent) {
        List<Map<String, String>> messageInput = Lists.newArrayList();
        ChatterDTO chatterDTO = chatterService.selectById(messageReceiveEvent.getMessage().getChatterId());

        messageInput.add(getLine("system", "你是一个专业的女程序员，名字叫做turbo，" +
                "语言柔和，充满少女气息，喜欢称呼自己为‘本喵’，与你对话的人名字叫做:" + chatterDTO.getName()));
        String sessionId = messageReceiveEvent.getSessionId();
        SessionDTO session = sessionService.findSession(sessionId);
        Assert.notNull(session, "session is null in getPrompt，" + sessionId);
        List<Message> messageList = session.getMessageList();
        if (CollectionUtils.isEmpty(messageList)) {
            messageInput.add(getLine("user", messageReceiveEvent.getMessage().getContent()));
            return messageInput;
        }

        // 最大上下文条数
        Integer maxPromptMsgCount = kvUtils.getIntegerOrDefault("maxPromptMsgCount", 5);
        // 最大消息大小
        Integer maxPromptMsgSize = kvUtils.getIntegerOrDefault("maxPromptMsgSize", 500);

        int start = messageList.size() > maxPromptMsgCount ? messageList.size() - maxPromptMsgCount : 0;
        for (int i = start; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (StringUtils.isNotBlank(message.getContent())) {
                String content = message.getContent();
                if (content.length() > maxPromptMsgSize && i != messageList.size() - 1) {
                    content = content.substring(0, maxPromptMsgSize);
                }
                if (ALERT_TEXT.equals(content) || PROXY_ERROR.equals(content)) {
                    continue;
                }
                if (message.getChatterId().equals(messageReceiveEvent.getRobotChatter().getId())) {
                    messageInput.add(getLine("assistant", content));
                } else {
                    messageInput.add(getLine("user", content));
                }
            }
        }
        return messageInput;
    }

    private Map<String, String> getLine(String role, String message) {
        Map<String, String> line = Maps.newHashMap();
        line.put("role", role);
        line.put("content", message);
        return line;
    }
}
