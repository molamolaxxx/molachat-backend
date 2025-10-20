package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.utils.MapUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.IdUtils;
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
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-05-18 10:59
 **/
@Component
@Slf4j
public class McpExecHandler implements IRobotEventHandler<MessageReceiveEvent, BaseAction> {

    private static final String PROCESS_UNI_KEY_FORMAT = "%s_%s";
    private static final String MCP_USER_CONFIG_PREFIX = "mcpUserConfig_";
    private static final String MCP_HISTORY_PROCESS_PREFIX = "mcpHistoryProcess_";
    private static final String MCP_MEMORY_PREFIX = "mcpMemory_";
    private static final String CHAT_GPT_MODEL_NAME_KEY = "chatGptModelName_chatGpt";
    private static final String DEFAULT_MODEL_NAME = "Llama-3.2-90B-Vision-Instruct";
    private static final String CHAT_GPT_MODEL_COST_PREFIX = "chatGptModelCost_";
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
        String processUniKey = String.format(PROCESS_UNI_KEY_FORMAT, robotId, sessionId);
        String userRequest = messageReceiveEvent.getMessage().getContent();
        if (Objects.equals(userRequest, "#stop-mcp#")) {
            McpProcess mcpProcess = processMap.get(processUniKey);
            if (mcpProcess != null) {
                mcpProcess.terminate(null);
            }
            return MessageSendAction.skip();
        }

        List<McpProcess> processList = JSON.parseObject(
                kvUtils.getStringOrDefault(MCP_HISTORY_PROCESS_PREFIX + sessionId, "[]")
                , new TypeReference<List<McpProcess>>(){});

        if (Objects.equals(userRequest, "#memory-open#")) {
            kvUtils.set(MCP_HISTORY_PROCESS_PREFIX + sessionId, "[]", robotId);
            kvUtils.set(MCP_MEMORY_PREFIX + sessionId, "Y", robotId);
            return MessageSendAction.skip();
        } else if (Objects.equals(userRequest, "#memory-close#")) {
            kvUtils.set(MCP_HISTORY_PROCESS_PREFIX + sessionId, "[]", robotId);
            kvUtils.set(MCP_MEMORY_PREFIX + sessionId, "N", robotId);
            return MessageSendAction.skip();
        } else if (Objects.equals(userRequest, "#clear-context#")) {
            kvUtils.set(MCP_HISTORY_PROCESS_PREFIX + sessionId, "[]", robotId);
            return MessageSendAction.skip();
        } else if (Objects.equals(userRequest, "#open-todo#")) {
            McpProcessTodoItem todoItem = queryTodoItem(sessionId);
            if (todoItem == null) {
                return MessageSendAction.withResp("流程无可用代办");
            }
            CmdSender.INSTANCE.send("openUrl", sessionId, new String[]{
                    String.format("{'url':'%s'}", todoItem.getTodoListPath()), todoItem.getProcessId()});
            return MessageSendAction.skip();
        }
        if (userRequest.startsWith("#settings#")) {
            userRequest = userRequest.replace("#settings# ", "");
            kvUtils.set(MCP_USER_CONFIG_PREFIX + sessionId, userRequest,
                    messageReceiveEvent.getMessage().getChatterId());
            return MessageSendAction.withResp("用户设置成功");
        }

        if (processMap.containsKey(processUniKey)) {
            return MessageSendAction.withResp("当前流程正在进行中，请稍后提交");
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
                return MessageSendAction.withResp("流程无可用远程命令");
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

            boolean useMemory = Objects.equals(
                            kvUtils.getStringOrDefault(MCP_MEMORY_PREFIX + sessionId, "N"),"Y");
            McpProcess mcpProcess;
            if (userRequest.startsWith("#make-todo#")) {
                mcpProcess = new McpProcess(
                        McpProcess.ProcessType.TODO,
                        "MCP"+ System.currentTimeMillis() % 1000 + IdUtils.getRandomString(5),
                        robotId,
                        sessionId,
                        Lists.newArrayList(), userRequest.replace("#make-todo# ", ""), cmdDescList, cmdList,
                        false,
                        chatGptSolution, kvUtils, messageSolution,
                        0,0,0,
                        Lists.newArrayList(),
                        useMemory,
                        null,
                        0,
                        systemSettings(sessionId),
                        null
                );
            } else if (userRequest.equals("#process-todo#")) {
                McpProcessTodoItem todoItem = queryTodoItem(sessionId);
                if (todoItem == null) {
                    return MessageSendAction.withResp("流程无可用代办");
                }
                mcpProcess = new McpProcess(
                        McpProcess.ProcessType.PROCESS_TODO,
                        "MCP"+ System.currentTimeMillis() % 1000 + IdUtils.getRandomString(5),
                        robotId,
                        sessionId,
                        Lists.newArrayList(), todoItem.getUserRequest(), cmdDescList, cmdList,
                        false,
                        chatGptSolution, kvUtils, messageSolution,
                        0,0,0,
                        Lists.newArrayList(),
                        useMemory,
                        null,
                        0,
                        systemSettings(sessionId),
                        todoItem
                );
            } else {
                mcpProcess = new McpProcess(
                        McpProcess.ProcessType.TASK,
                        "MCP"+ System.currentTimeMillis() % 1000 + IdUtils.getRandomString(5),
                        robotId,
                        sessionId,
                        Lists.newArrayList(), userRequest, cmdDescList, cmdList,
                        false,
                        chatGptSolution, kvUtils, messageSolution,
                        0,0,0,
                        processList,
                        useMemory,
                        null,
                        0,
                        systemSettings(sessionId),
                        null
                );
            }

            if (Objects.equals(userRequest, "#show-context#")) {
                return MessageSendAction.withResp(mcpProcess.buildRequest());
            }
            if (Objects.equals(userRequest, "#context-length#")) {
                if (CollectionUtils.isEmpty(processList)) {
                    return MessageSendAction.withResp("上下文长度:0");
                }
                return MessageSendAction.withResp("上下文长度:"+
                        McpProcess.estimateTokens(mcpProcess.buildRequest()));
            }

            if (userRequest.startsWith("#hidden-cmd-result#")) {
                return MessageSendAction.withResp(
                        hiddenCmd(userRequest.replace("#hidden-cmd-result# ", ""),
                                processList, true, sessionId, robotId));
            }
            if (userRequest.startsWith("#hidden-cmd#")) {
                return MessageSendAction.withResp(
                        hiddenCmd(userRequest.replace("#hidden-cmd# ", ""),
                                processList, false, sessionId, robotId));
            }
            processMap.put(processUniKey, mcpProcess);

            // 开启
            mcpProcess.start();

            String modelName = kvUtils.getStringOrDefault(CHAT_GPT_MODEL_NAME_KEY,
                    DEFAULT_MODEL_NAME);
            String costStr = kvUtils.getString(CHAT_GPT_MODEL_COST_PREFIX + modelName);
            if (StringUtils.isNotBlank(costStr) && costStr.contains("#")) {
                String[] split = costStr.split("#");
                BigDecimal inputCost = new BigDecimal(mcpProcess.getUsedInputToken() - mcpProcess.getTotalCachedTokens())
                        .multiply(new BigDecimal(split[0]))
                        .divide(BigDecimal.valueOf(1000000), 2, RoundingMode.HALF_UP);
                BigDecimal outputCost = new BigDecimal(mcpProcess.getUsedOutputToken())
                        .multiply(new BigDecimal(split[1]))
                        .divide(BigDecimal.valueOf(1000000), 2, RoundingMode.HALF_UP);
                if (split.length == 3 && mcpProcess.getTotalCachedTokens() != 0) {
                    inputCost.add(new BigDecimal(mcpProcess.getTotalCachedTokens())
                            .multiply(new BigDecimal(split[2]))
                            .divide(BigDecimal.valueOf(1000000), 2, RoundingMode.HALF_UP));
                }

                return MessageSendAction.withResp(
                        String.format("流程执行完成\n编号：%s\n输入token：%s\n命中缓存token：%s\n输出token：%s\n本次开销：%s\n上下文长度：%s",
                                mcpProcess.getProcessId(), mcpProcess.getUsedInputToken(),
                                mcpProcess.getTotalCachedTokens(), mcpProcess.getUsedOutputToken(),
                                inputCost.add(outputCost).toPlainString(), mcpProcess.getContextLength()));
            } else {
                return MessageSendAction.withResp(
                        String.format("流程执行完成\n编号：%s\n输入token：%s\n命中缓存token：%s\n输出token：%s\n上下文长度：%s",mcpProcess.getProcessId(),
                                mcpProcess.getUsedInputToken(), mcpProcess.getTotalCachedTokens(),
                                mcpProcess.getUsedOutputToken(), mcpProcess.getContextLength()));
            }
        } catch (Exception e) {
            log.error("McpExecHandler error", e);
            return MessageSendAction.withResp("流程执行失败，原因：" + e.getMessage());
        } finally {
            processMap.remove(processUniKey);
        }
    }

    private McpProcessTodoItem queryTodoItem(String sessionId) {
        try {
            // 执行命令
            CmdInvokeResponse<CmdResponseContent> cmdResp = CmdSender.INSTANCE
                    .send("queryLastProcessDir", sessionId, new String[]{"{}"});
            Map<String, String> resultMap = cmdResp.getData().getResultMap();
            String processId = resultMap.get("result");
            if (StringUtils.isBlank(processId)) {
                return null;
            }
            McpProcessTodoItem todoItem = new McpProcessTodoItem();
            todoItem.setProcessId(processId);
            todoItem.setResourcePath("./.process/" + processId + "/resource.md");
            todoItem.setTodoListPath("./.process/" + processId + "/todoList.md");

            // 执行命令
            cmdResp = CmdSender.INSTANCE
                    .send("readFile", sessionId, new String[]{
                            String.format("{'path':'%s'}", "./.process/" + processId + "/request.txt"), processId});
            resultMap = cmdResp.getData().getResultMap();
            if (resultMap.get("result").contains("文件不存在")) {
                return null;
            }
            todoItem.setUserRequest(resultMap.get("result"));
            return todoItem;
        } catch (Exception e) {
            log.warn("queryTodoItem query error, sessionId = " + sessionId + ":" + e.getMessage());
            return null;
        }
    }

    private String systemSettings(String sessionId) {
        try {
            // 执行命令
            CmdInvokeResponse<CmdResponseContent> cmdResp = CmdSender.INSTANCE
                    .send("systemSettings", sessionId, new String[]{"{}"});
            Map<String, String> resultMap = cmdResp.getData().getResultMap();
            return resultMap.get("result");
        } catch (Exception e) {
            log.warn("systemSettings query error, sessionId = " + sessionId + ":" + e.getMessage());
        }
        return null;
    }

    private String hiddenCmd(String target, List<McpProcess> processList, boolean onlyHiddenResult,
                             String sessionId, String robotId) {
        List<CmdHistoryItem> items = processList.stream().map(McpProcess::getCmdHistory)
                .flatMap(Collection::stream)
                .filter(e -> Objects.equals(e.getTarget(), target))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(items)) {
            return "未匹配指令执行记录";
        }
        if (items.size() > 1) {
            return "匹配了多个指令执行记录";
        }
        items.forEach(e -> {
            if (onlyHiddenResult) {
                e.setHiddenResult(true);
            } else {
                e.setHiddenItem(true);
            }
        });
        kvUtils.set(MCP_HISTORY_PROCESS_PREFIX + sessionId, JSON.toJSONString(processList), robotId);
        return "隐藏成功";
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
        String processUniKey = String.format(PROCESS_UNI_KEY_FORMAT, robotId, sessionId);
        McpProcess mcpProcess = processMap.get(processUniKey);
        if (mcpProcess != null) {
            return CmdDescription.builder()
                    .cmdName("#stop-mcp#")
                    .cmdDesc("停止流程")
                    .executeScript("sendMessageInner('#stop-mcp#')")
                    .buildSingleton();
        }
        String userSetting = kvUtils.getStringOrDefault(MCP_USER_CONFIG_PREFIX + sessionId, "无");
        CmdDescription setting = CmdDescription.builder()
                .cmdName("#settings#")
                .cmdDesc("Mcp用户设置")
                .executeScript(String.format("popupAndSendCmd('#settings#','%s')", Base64Util.encodeBase64(userSetting)))
                .build();


        String memory = kvUtils.getStringOrDefault(MCP_MEMORY_PREFIX + sessionId, "N");
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

        CmdDescription queryContextLength = CmdDescription.builder()
                .cmdName("#context-length#")
                .cmdDesc("查询上下文长度")
                .executeScript("sendMessageInner('#context-length#')")
                .build();

        CmdDescription showContext = CmdDescription.builder()
                .cmdName("#show-context#")
                .cmdDesc("查询上下文")
                .executeScript("sendMessageInner('#show-context#')")
                .build();

        CmdDescription hiddenResult = CmdDescription.builder()
                .cmdName("#hidden-cmd-result#")
                .cmdDesc("隐藏指令执行结果")
                .executeScript(String.format("popupAndSendCmd('#hidden-cmd-result#','%s')",
                        Base64Util.encodeBase64("{输入目的匹配}")))
                .build();

        CmdDescription hiddenItem = CmdDescription.builder()
                .cmdName("#hidden-cmd#")
                .cmdDesc("隐藏指令执行记录")
                .executeScript(String.format("popupAndSendCmd('#hidden-cmd#','%s')",
                        Base64Util.encodeBase64("{输入目的匹配}")))
                .build();

        CmdDescription todo = CmdDescription.builder()
                .cmdName("#make-todo#")
                .cmdDesc("生成计划")
                .executeScript(String.format("popupAndSendCmd('#make-todo#','%s')",
                        Base64Util.encodeBase64("{输入需求}")))
                .build();

        CmdDescription processTodo = null;
        CmdDescription openTodo = null;
        McpProcessTodoItem todoItem = queryTodoItem(sessionId);
        if (todoItem != null) {
            processTodo = CmdDescription.builder()
                    .cmdName("#process-todo#")
                    .cmdDesc("执行计划")
                    .executeScript("sendMessageInner('#process-todo#')")
                    .build();
            openTodo = CmdDescription.builder()
                    .cmdName("#open-todo#")
                    .cmdDesc("打开执行计划")
                    .executeScript("sendMessageInner('#open-todo#')")
                    .build();
        }

        return Lists.newArrayList(clearContext,showContext,
                queryContextLength, todo, processTodo, openTodo,  hiddenResult, hiddenItem, setting, openMemory);
    }
}
