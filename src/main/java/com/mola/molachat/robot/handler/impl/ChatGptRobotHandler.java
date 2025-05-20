package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.config.AppConfig;
import com.mola.molachat.common.utils.HttpUtil;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.GptRobotEventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.robot.solution.CmdProxyInvokeSolution;
import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.model.StreamMessageConnect;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private MessageSolution messageSolution;

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

    public static final String MODEL_URL = "https://api.sambanova.ai/v1/chat/completions";

    public static final String ALERT_TEXT = "账户已失效";

    public static final String STREAM_FORCE_STOP = "访问异常，输出流自动关闭";

    public static final String PROXY_ERROR = "代理异常, 请重试";

    public static final String CLEAR_CMD = "#clear#";

    public static final String STOP_STEAM_CMD = "#stop-stream#";

    private static final int RETRY_TIME = 12;

    private static final int CHANGE_API_KEY_TIME = 8;

    private static final String THINK_START = "[开始思考]";

    private static final String THINK_END = "[思考结束]";

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
        Message message = messageReceiveEvent.getMessage();
        // 默认主账号
        String usedApiKey = robotChatter.getApiKey();
        try {
            if (CLEAR_CMD.equals(messageReceiveEvent.getMessage().getContent())
             || STOP_STEAM_CMD.equals(messageReceiveEvent.getMessage().getContent())) {
                messageSendAction.setSkip(true);
                return messageSendAction;
            }
            // headers
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("Content-Type", "application/json"));
            headers.add(new BasicHeader("Authorization", "Bearer " + usedApiKey));
            // prompt 拼接最近20条历史记录
            JSONObject body = new JSONObject();
            String modelName = kvUtils.getStringOrDefault("chatGptModelName_" + robotChatter.getId(), "Llama-3.2-90B-Vision-Instruct");
            body.put("model", modelName);
            List<Map<String, String>> prompt = getPrompt(messageReceiveEvent);
            log.info(JSONObject.toJSONString(prompt));
            body.put("messages", prompt);
            // 是否使用流输出
            String useSteam = kvUtils.getStringOrDefault("useSteam", "Y");

            // 模型地址
            String modelUrl = kvUtils.getStringOrDefault("modelUrl_" + robotChatter.getId(), MODEL_URL);
            if (Objects.equals(useSteam, "Y")) {
                return processWithStream(body, headers, modelUrl, messageSendAction, messageReceiveEvent);
            } else {
                return processWithoutStream(body, headers, modelUrl, messageSendAction);
            }
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler ChatGptRobotHandler error event:" + JSONObject.toJSONString(messageReceiveEvent), e);
            // 强制关闭流
            messageSolution.stopStream(messageReceiveEvent.getRobotChatter().getId(),
                    messageReceiveEvent.getSessionId());
            if (StringUtils.containsIgnoreCase(e.getMessage(), "You exceeded your current quota")) {
                chatGptSolution.removeApiKey(usedApiKey);
                // 不可用告警
                messageSendAction.setResponsesText(ALERT_TEXT);
                return messageSendAction;
            }
            // 网络失败
            if ((StringUtils.containsIgnoreCase(e.getMessage(), "Network is unreachable")
                    || StringUtils.containsIgnoreCase(e.getMessage(), "Bad Gateway")
                    || StringUtils.containsIgnoreCase(e.getMessage(), "Connection refused"))) {
                // 不可用告警
                messageSendAction.setResponsesText(ALERT_TEXT);
                return messageSendAction;
            }
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

    private MessageSendAction processWithoutStream(JSONObject body, List<Header> headers, String modelUrl, MessageSendAction messageSendAction) throws Exception {
        body.put("stream", false);
        String res = HttpUtil.INSTANCE.post(MODEL_URL, body, 300000, headers.toArray(new Header[]{}));
        messageSendAction.setResponsesText(ChatGptSolution.parseResult(res));

        log.info("RemoteRobotChatHandler processWithoutStream success, action:" + JSONObject.toJSONString(messageSendAction));
        return messageSendAction;
    }

    private MessageSendAction processWithStream(JSONObject body, List<Header> headers, String modelUrl,
                                                MessageSendAction messageSendAction, MessageReceiveEvent messageReceiveEvent) throws Exception {
        body.put("stream", true);
        AtomicBoolean outputReasoningContent = new AtomicBoolean(false);
        StreamMessageConnect streamConnect = messageSolution.findStreamConnect(messageReceiveEvent.getRobotChatter().getId(),
                messageReceiveEvent.getSessionId());
        if (streamConnect != null) {
            log.warn("processWithStream 存在进行中的stream，忽略, messageReceiveEvent = {}", messageReceiveEvent);
            messageSendAction.setSkip(true);
            return messageSendAction;
        }
        HttpUtil.INSTANCE.postWithStreamRes(modelUrl, body, 300000, headers.toArray(new Header[]{}),
                part -> {
                    try {
                        Thread.sleep(new Random().nextInt(50) + 50);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    // 思维链样式
                    Map<String, String> showStyleProps = Maps.newHashMap();
                    showStyleProps.put("color", "#8b8b8b");
                    showStyleProps.put("font-size", "1.25rem");

                    StreamMessageConnect existStreamConnect =
                            messageSolution.findStreamConnect(messageReceiveEvent.getRobotChatter().getId(),
                            messageReceiveEvent.getSessionId());
                    boolean stop = ChatGptSolution.isStreamResultStop(part)
                            || (existStreamConnect != null && existStreamConnect.isClosed());

                    // 思维链
                    String reasoningContent = ChatGptSolution.parseStreamContent(part, "reasoning_content");
                    if (reasoningContent != null) {
                        if (!outputReasoningContent.get()) {
                            outputReasoningContent.set(true);
                            sendStreamMessage(THINK_START + "\n", messageReceiveEvent, stop, showStyleProps);
                        }
                        if (stop) {
                            sendStreamMessage(reasoningContent + "\n" + THINK_END, messageReceiveEvent, true
                                    , showStyleProps);
                        } else {
                            sendStreamMessage(reasoningContent, messageReceiveEvent, false, null);
                        }
                    }

                    // 内容
                    String content = ChatGptSolution.parseStreamContent(part, "content");
                    if (content != null) {
                        if (outputReasoningContent.get() && StringUtils.isNotEmpty(content)) {
                            outputReasoningContent.set(false);
                            sendStreamMessage("\n" + THINK_END, messageReceiveEvent, true, showStyleProps);
                        }
                        sendStreamMessage(content, messageReceiveEvent, stop, null);
                    }
                    return !stop;
                });

        // 如果stream未结束，则强制结束
        streamConnect = messageSolution.findStreamConnect(messageReceiveEvent.getRobotChatter().getId(),
                messageReceiveEvent.getSessionId());
        if (streamConnect != null) {
            messageSolution.stopStream(messageReceiveEvent.getRobotChatter().getId(),
                    messageReceiveEvent.getSessionId());
            log.error("streamConnect is not stop, force stop {}", streamConnect);
            messageSendAction.setResponsesText(STREAM_FORCE_STOP);
            return messageSendAction;
        }

        log.info("RemoteRobotChatHandler processWithStream success, action:" + JSONObject.toJSONString(messageSendAction));
        messageSendAction.setSkip(true);
        return messageSendAction;
    }

    private void sendStreamMessage(String content, MessageReceiveEvent messageReceiveEvent,
                                   boolean end, Map<String, String> showStyleProps) {
        // 发送流式消息
        StreamMessage msg = new StreamMessage();
        msg.setContent(content);
        msg.setChatterId(messageReceiveEvent.getRobotChatter().getId());
        msg.setSessionId(messageReceiveEvent.getSessionId());
        msg.setCreateTime(new Date());
        msg.setEnd(end);
        msg.setShowStyleProps(showStyleProps);
        messageSolution.sendStreamMessage(messageReceiveEvent.getSessionId(), msg);
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

        RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
        messageInput.add(getLine("system",
                "你是一个专业的女程序员，名字叫做" + robotChatter.getName() +
                "；你的语言柔和，逻辑严谨，你的个性签名是:" + robotChatter.getSignature() +
                "，与你对话的人名字叫做:" + chatterDTO.getName()));
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

        List<Map<String, String>> contentLines = Lists.newArrayList();
        for (int i = start; i < messageList.size(); i++) {
            Message message = messageList.get(i);
            if (message instanceof FileMessage) {
                FileMessage fileMessage = (FileMessage) message;
                if (StringUtils.isNotBlank(fileMessage.getOcrResultCache())) {
                    contentLines.add(getLine("user",
                            String.format("用户发来一张包含文字的图片，图片的名称是：%s，" +
                                    "图片的内容是：%s",fileMessage.getFileName(),
                                    fileMessage.getOcrResultCache())));
                }
            } else if (StringUtils.isNotBlank(message.getContent())) {
                String content = message.getContent();
                if (content.length() > maxPromptMsgSize && i != messageList.size() - 1) {
                    content = content.substring(0, maxPromptMsgSize);
                }
                if (ALERT_TEXT.equals(content) || PROXY_ERROR.equals(content)
                        || STOP_STEAM_CMD.equals(content) || STREAM_FORCE_STOP.equals(content)) {
                    continue;
                }
                if (content.contains(THINK_START) && content.contains(THINK_END)) {
                    continue;
                }
                if (CLEAR_CMD.equals(content)) {
                    contentLines.clear();
                    continue;
                }
                if (message.getChatterId().equals(messageReceiveEvent.getRobotChatter().getId())) {
                    contentLines.add(getLine("assistant", content));
                } else {
                    contentLines.add(getLine("user", content));
                }
            }
        }
        messageInput.addAll(contentLines);
        return messageInput;
    }

    private Map<String, String> getLine(String role, String message) {
        Map<String, String> line = Maps.newHashMap();
        line.put("role", role);
        line.put("content", message);
        return line;
    }

    @Override
    public CmdDescription cmdDescription(String robotId, String sessionId) {
        StreamMessageConnect streamConnect = messageSolution.findStreamConnect(robotId, sessionId);
        if (streamConnect == null) {
            return CmdDescription.builder()
                    .cmdName("#clear#")
                    .cmdDesc("清空对话上下文")
                    .executeScript("sendMessageInner('#clear#')")
                    .build();
        }
        return CmdDescription.NOT_SUPPORT;
    }
}
