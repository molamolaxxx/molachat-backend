package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.utils.MapUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-05-18 10:59
 **/
@Component
@Slf4j
public class McpExecHandler implements IRobotEventHandler<MessageReceiveEvent, BaseAction> {

    private Map<String, McpProcess> processMap = Maps.newConcurrentMap();

    @Resource
    private ChatGptSolution chatGptSolution;

    @Resource
    private KvUtils kvUtils;

    @Resource
    private MessageSolution messageSolution;

    @Resource
    private SessionService sessionService;

    @Override
    public BaseAction handler(MessageReceiveEvent messageReceiveEvent) {
        String robotId = messageReceiveEvent.getRobotChatter().getId();
        String sessionId = messageReceiveEvent.getSessionId();
        String processUniKey = String.format("%s_%s", robotId, sessionId);
        String userRequest = messageReceiveEvent.getMessage().getContent();
        if (Objects.equals(userRequest, "#stop-mcp#")) {
            McpProcess mcpProcess = processMap.get(processUniKey);
            if (mcpProcess != null) {
                mcpProcess.terminate();
            }
            return MessageSendAction.skip();
        }
        if (Objects.equals(userRequest, "#memory-open#")) {
            kvUtils.set("mcpHistoryProcess_" + sessionId, "[]", robotId);
            kvUtils.set("mcpMemory_" + sessionId, "Y", robotId);
            return MessageSendAction.skip();
        } else if (Objects.equals(userRequest, "#memory-close#")) {
            kvUtils.set("mcpHistoryProcess_" + sessionId, "[]", robotId);
            kvUtils.set("mcpMemory_" + sessionId, "N", robotId);
            return MessageSendAction.skip();
        } else if (Objects.equals(userRequest, "#clear-context#")) {
            kvUtils.set("mcpHistoryProcess_" + sessionId, "[]", robotId);
            return MessageSendAction.skip();
        }

        if (userRequest.startsWith("#settings#")) {
            userRequest = userRequest.replace("#settings# ", "");
            kvUtils.set("mcpUserConfig_" + sessionId, userRequest,
                    messageReceiveEvent.getMessage().getChatterId());
            return MessageSendAction.withResp("用户设置成功");
        }

        if (processMap.containsKey(processUniKey)) {
            return MessageSendAction.withResp("当前Mcp流程正在进行中，请稍后提交");
        }

        // ocr图片
        String lastOcrMessageContent = findLastOcrMessageContent(sessionId);
        if (StringUtils.isNotBlank(lastOcrMessageContent)) {
            userRequest = userRequest + lastOcrMessageContent;
        }

        try {
            // 查询可用命令
            Map<String, String> remoteCmdDescMap = CmdSender.INSTANCE.fetchDescriptionMap(sessionId);
            if (MapUtils.isEmpty(remoteCmdDescMap)) {
                return MessageSendAction.withResp("Mcp流程无可用远程命令");
            }
            List<String> cmdDescList = Lists.newArrayList();
            List<String> cmdList = Lists.newArrayList();
            if (MapUtils.isNotEmpty(remoteCmdDescMap)) {
                remoteCmdDescMap.forEach((cmd, desc) -> {
                    if (!desc.startsWith("#mcp:")) {
                        return;
                    }
                    desc = desc.replace("#mcp:", "");
                    cmdDescList.add(desc);
                    cmdList.add(cmd);
                });
            }

            List<McpProcess> processList = JSON.parseObject(
                    kvUtils.getStringOrDefault("mcpHistoryProcess_" + sessionId, "[]")
                    , new TypeReference<List<McpProcess>>(){});


            boolean useMemory = Objects.equals(
                            kvUtils.getStringOrDefault("mcpMemory_" + sessionId, "N"),"Y");

            McpProcess mcpProcess = new McpProcess(
                    robotId,
                    sessionId,
                    Lists.newArrayList(),
                    userRequest,
                    cmdDescList,
                    cmdList,
                    false,
                    chatGptSolution,
                    kvUtils,
                    messageSolution,0,0,
                    processList,
                    useMemory
            );
            processMap.put(processUniKey, mcpProcess);

            // 开启
            mcpProcess.start();

            String modelName = kvUtils.getStringOrDefault("chatGptModelName_chatGpt",
                    "Llama-3.2-90B-Vision-Instruct");
            String costStr = kvUtils.getString("chatGptModelCost_" + modelName);
            if (StringUtils.isNotBlank(costStr) && costStr.contains("#")) {
                String[] split = costStr.split("#");
                BigDecimal inputCost = new BigDecimal(mcpProcess.getUsedInputToken())
                        .multiply(new BigDecimal(split[0]))
                        .divide(BigDecimal.valueOf(1000000), 2, RoundingMode.HALF_UP);
                BigDecimal outputCost = new BigDecimal(mcpProcess.getUsedOutputToken())
                        .multiply(new BigDecimal(split[1]))
                        .divide(BigDecimal.valueOf(1000000), 2, RoundingMode.HALF_UP);

                return MessageSendAction.withResp(
                        String.format("Mcp流程执行完成\n输入token：%s\n输出token：%s\n本次开销：%s",
                                mcpProcess.getUsedInputToken(), mcpProcess.getUsedOutputToken(), inputCost.add(outputCost).toPlainString()));
            } else {
                return MessageSendAction.withResp(
                        String.format("Mcp流程执行完成\n输入token：%s\n输出token：%s",
                                mcpProcess.getUsedInputToken(), mcpProcess.getUsedOutputToken()));
            }
        } catch (Exception e) {
            log.error("McpExecHandler error", e);
            return MessageSendAction.withResp("Mcp流程执行失败，原因：" + e.getMessage());
        } finally {
            processMap.remove(processUniKey);
        }
    }

    private String findLastOcrMessageContent(String sessionId) {
        SessionDTO session = sessionService.findSession(sessionId);
        Assert.notNull(session, "session is null in getPrompt，" + sessionId);
        List<Message> messageList = session.getMessageList();
        Message message = messageList.get(messageList.size() - 2);
        if (!(message instanceof FileMessage)) {
            return null;
        }
        FileMessage fileMessage = (FileMessage) message;
        if (StringUtils.isNotBlank(fileMessage.getOcrResultCache())) {
            return String.format("\n用户请求中的附件：\n" +
                            "图片名称：%s\n" +
                            "图片内容：%s\n",fileMessage.getFileName(),
                    fileMessage.getOcrResultCache());
        }
        return null;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    @Override
    public List<CmdDescription> cmdDescriptions(String robotId, String sessionId) {
        String processUniKey = String.format("%s_%s", robotId, sessionId);
        McpProcess mcpProcess = processMap.get(processUniKey);
        if (mcpProcess != null) {
            return CmdDescription.builder()
                    .cmdName("#stop-mcp#")
                    .cmdDesc("停止mcp流程")
                    .executeScript("sendMessageInner('#stop-mcp#')")
                    .buildSingleton();
        }
        String userSetting = kvUtils.getStringOrDefault("mcpUserConfig_" + sessionId, "无");
        CmdDescription setting = CmdDescription.builder()
                .cmdName("#settings#")
                .cmdDesc("Mcp用户设置")
                .executeScript(String.format("popupAndSendCmd('#settings#','%s')", Base64Util.encodeBase64(userSetting)))
                .build();


        String memory = kvUtils.getStringOrDefault("mcpMemory_" + sessionId, "N");
        CmdDescription openMemory = null;
        if (Objects.equals(memory, "N")) {
            openMemory = CmdDescription.builder()
                    .cmdName("#memory-open#")
                    .cmdDesc("开启记忆模式")
                    .executeScript("sendMessageInner('#memory-open#')")
                    .build();
        } else {
            openMemory = CmdDescription.builder()
                    .cmdName("#memory-close#")
                    .cmdDesc("关闭记忆模式")
                    .executeScript("sendMessageInner('#memory-close#')")
                    .build();
        }

        CmdDescription clearContext = CmdDescription.builder()
                .cmdName("#clear-context#")
                .cmdDesc("清除上下文")
                .executeScript("sendMessageInner('#clear-context#')")
                .build();

        return Lists.newArrayList(setting, openMemory, clearContext);
    }
}
