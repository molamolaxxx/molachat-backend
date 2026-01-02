package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.constant.CmdProxyConstant;
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
* @Project: molachat
* @Description:
* @author : molamola
* @date : 2025-10-04 12:27
**/
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
@Data
public class McpProcess  {

    /**
     * 可使用的命令列表
     */
    private static String CMD_LIST_PLACE_HOLDER = "%CMD_LIST%";

    /**
     * 用户配置
     */
    private static String USER_CONFIG_PLACE_HOLDER = "%USER_CONFIG%";

    /**
     * 执行历史
     */
    private static String CMD_HISTORY_PLACE_HOLDER = "%CMD_HISTORY%";

    /**
     * 用户需求
     */
    private static String USER_REQUEST_PLACE_HOLDER = "%USER_REQUEST%";

    /**
     * 流程编号
     */
    private static String PROCESS_ID_HOLDER = "%PROCESS_ID%";

    /**
     * 预处理文件
     */
    private static String PRE_FILE = "%PRE_FILE%";

    private String processType;

    private String processId;

    private String robotId;

    private String sessionId;

    private List<CmdHistoryItem> cmdHistory;

    private String userRequest;

    private List<String> cmdDescList;

    private List<String> cmdList;

    private transient volatile boolean terminate;

    private transient ChatGptSolution chatGptSolution;

    private transient KvUtils kvUtils;

    private transient MessageSolution messageSolution;

    private int usedInputToken;

    private int usedOutputToken;

    private int totalCachedTokens;

    private transient List<McpProcess> historyProcess;

    private transient boolean useMemory;

    private transient volatile String terminalMessage;

    private int contextLength;

    private String systemSettings;

    private McpProcessDirItem todoItem;

    private McpProcessDirItem projectFiles;

    public void start() {
        if (Objects.equals("Y", kvUtils.getString("logMcpRequest"))) {
            sendNotifyImmediately(buildRequest());
        }
        try {
            loop();
        } catch (Exception e) {
            log.error("McpProcess process error", e);
            terminate("流程执行异常，异常原因:" + e.getMessage());
        }
        Validate.isTrue(terminate, "确认流程终止");
        // 终止当前输出流对话
        stopStream();
        if (StringUtils.isNotBlank(terminalMessage)) {
            sendNotifyImmediately(terminalMessage);
        }
        if (Objects.equals("Y", kvUtils.getString("logMcpRequest"))) {
            sendNotifyImmediately(buildRequest());
        }
        if (useMemory && (ProcessType.TASK.equals(processType) || ProcessType.PROCESS_TODO.equals(processType))) {
            String mcpHistoryProcessStr = kvUtils.getString("mcpHistoryProcess_" + sessionId);
            List<McpProcess> processList = Lists.newArrayList();
            processList.addAll(historyProcess);
            processList.add(this);
            // 保存上下文
            kvUtils.set("mcpHistoryProcess_" + sessionId, JSON.toJSONString(processList), robotId);
        }
        if (ProcessType.TODO.equals(processType) || ProcessType.QUESTION.equals(processType) || ProcessType.TODO_WITH_QUESTION.equals(processType)) {
            // 创建resource文件
            createResource();
        }
    }

    private void createResource() {
        StringBuilder stringBuilder = new StringBuilder();

        int idx = 1;
        for (CmdHistoryItem cmdHistoryItem : cmdHistory) {
            Map.Entry<String, String[]> entry = buildParam(cmdHistoryItem.getCmdAndParam());
            String cmdName = entry.getKey();
            String cmdParam = entry.getValue()[0];
            String result = cmdHistoryItem.getResult();
            JSONObject jsonObject = JSON.parseObject(cmdParam);
            if (cmdName.startsWith("readFile") && result.length() > 100) {
                String path = jsonObject.getString("path");
                if (path.contains("resource.md")) {
                    stringBuilder.append(cmdHistoryItem.getResult()).append("\n");
                } else {
                    stringBuilder.append("------------------读取文件:").append(path).append("------------------").append("\n");
                    stringBuilder.append(cmdHistoryItem.getResult()).append("\n\n\n");
                }
            } else if (cmdName.startsWith("treeFile") && result.length() > 100) {
                stringBuilder.append("------------------读取文件夹结构:").append(jsonObject.getString("path")).append("------------------").append("\n");
                stringBuilder.append(cmdHistoryItem.getResult()).append("\n\n");
            }
            idx++;
        }

        String param;
        if (stringBuilder.toString().length() > 0) {
            param = String.format("{\"path\":\"%s\", \"base64\":\"%s\"}",
                    "./.process/" + processId + "/resource.md", Base64Util.encodeBase64(stringBuilder.toString()));
            // 执行命令
            CmdSender.INSTANCE.send("createFile64", sessionId, new String[]{param});
        }

        stringBuilder = new StringBuilder();
        stringBuilder.append(userRequest);
        param = String.format("{\"path\":\"%s\", \"base64\":\"%s\"}",
                "./.process/" + processId + "/request.txt", Base64Util.encodeBase64(stringBuilder.toString()));
        CmdSender.INSTANCE.send("createFile64", sessionId, new String[]{param});
    }

    private void loop() {
        AtomicInteger errorRepeatCnt = new AtomicInteger();
        sendNotify(buildLogPrefix());

        // 强制让模型读取todo
        List<String> nextCmdList = Lists.newArrayList();
        List<String> targets = Lists.newArrayList();
        if (projectFiles != null) {
            if (StringUtils.isNotBlank(projectFiles.getProjectFilePath())) {
                nextCmdList.add("readFile {'path':'" + projectFiles.getProjectFilePath() + "'}");
                targets.add("读取当前路径下的项目说明，确认项目结构和项目规范");
            }
        }
        if (todoItem != null) {
            if (StringUtils.isNotBlank(todoItem.getTodoListPath())) {
                nextCmdList.add("readFile {'path':'" + todoItem.getTodoListPath() + "'}");
                targets.add("读取代办列表，确认当前任务进度与下一步计划");
            }
            if (StringUtils.isNotBlank(todoItem.getQuestionPath())) {
                nextCmdList.add("readFile {'path':'" + todoItem.getQuestionPath() + "'}");
                targets.add("读取当前需求的疑问和回答");
            }
            if (StringUtils.isNotBlank(todoItem.getResourcePath())) {
                nextCmdList.add("readFile {'path':'" + todoItem.getResourcePath() + "'}");
                targets.add("读取本次任务依赖的资源文件");
            }
        }
        if (CollectionUtils.isNotEmpty(nextCmdList)) {
            executeCmd(nextCmdList, targets, errorRepeatCnt);
        }
        while (!terminate) {
            calculateImportRate();
            String request = buildRequest();
            this.contextLength = estimateTokens(request);
            if (contextLength > Integer.parseInt(kvUtils.getStringOrDefault("mcpPerMaxToken", "30000"))) {
                terminate("模型单次输入超过最大限制token数");
                break;
            }
            if (errorRepeatCnt.get() >= 3) {
                terminate("检测到错误循环，请调整提示词重试");
                break;
            }
            StringBuilder result = new StringBuilder();

            sendNotify("#### 模型输出\n");
            Map<String, Object> streamOptions = Maps.newHashMap();
            streamOptions.put("include_usage", true);

            UsedToken usedToken = new UsedToken();
            List<String> messageBuffer = Lists.newLinkedList();
            chatGptSolution.invoke(request, null, true, streamOptions,
                    part -> processStream(part, result, usedToken, request, messageBuffer),
                    kvUtils.getDoubleOrDefault("mcpTemperature", 0.1), sessionId);
            if (messageBuffer.size() > 0) {
                sendStreamMessage(String.join("", messageBuffer));
            }
            this.usedOutputToken += usedToken.usedOutputToken;
            this.usedInputToken += usedToken.usedInputToken;
            this.totalCachedTokens += usedToken.totalCachedTokens;
            if (terminate) {
                break;
            }

            // 提取命令列表
            nextCmdList = parseNextCmd(result.toString());
            log.info("提取命令列表: {}", nextCmdList);
            // 提取备注列表
            targets = parseNextTargets(result.toString());
            log.info("提取备注列表: {}", targets);

            // 执行命令
            executeCmd(nextCmdList, targets, errorRepeatCnt);
        }
    }

    private void executeCmd(List<String> nextCmdList, List<String> targets, AtomicInteger errorRepeatCnt) {
        Map<String, String> cmd2TargetDesc = Maps.newHashMap();
        for (int i = 0; i < nextCmdList.size(); i++) {
            if (i < targets.size()) {
                cmd2TargetDesc.put(nextCmdList.get(i), targets.get(i));
            }
        }
        if (CollectionUtils.isEmpty(nextCmdList)) {
            terminate(null);
            return;
        }

        sendNotify("\n#### 命令执行\n| 命令 | 参数 | 结果 | 目的 | 备注 |\n| ---- | ---- | ---- | ---- | --- |\n");

        for (String nextCmd : nextCmdList) {
            if (terminate) {
                break;
            }

            String targetDesc = cmd2TargetDesc.getOrDefault(nextCmd, "无");
            if (Objects.equals(nextCmd, "无指令")) {
                sendNotify(String.format("|  %s  |  %s  |  %s  |  %s  |  %s |\n",
                        "无指令",
                        "",
                        "流程结束",
                        processBeforeSend(targetDesc, -1),
                        ""
                ));
                terminate(null);
                break;
            }
            Map.Entry<String, String[]> entry = buildParam(nextCmd);
            if (!cmdList.contains(entry.getKey())) {
                terminate(String.format("未知命令%s，流程终止", entry.getKey()));
                break;
            }

            // 查询重复命令
            CmdHistoryItem repeatCmd = null;
            for (CmdHistoryItem cmdHistoryItem : cmdHistory) {
                if (Objects.equals(nextCmd, cmdHistoryItem.getCmdAndParam())) {
                    repeatCmd = cmdHistoryItem;
                }
            }
            // 存在重复命令，将最新结果进行替换
            String headerMessage = "";
            String remark = "";
            if (repeatCmd != null) {
                if (repeatCmd == cmdHistory.get(cmdHistory.size() - 1)) {
                    errorRepeatCnt.incrementAndGet();
                    headerMessage = "[警告] 检测到重复命令，后续禁止输出该命令:" + repeatCmd.getCmdAndParam() + "\n";
                    remark = "重复命令警告，当前错误次数:" + errorRepeatCnt + ";";
                } else {
                    remark = "存在历史重复命令;";
                }
            } else {
                errorRepeatCnt.set(0);
            }

            // 执行命令
            String cmdResult;
            try {
                CmdInvokeResponse<CmdResponseContent> cmdResp = CmdSender.INSTANCE
                        .send(entry.getKey(), sessionId, entry.getValue());

                Map<String, String> resultMap = cmdResp.getData().getResultMap();
                cmdResult = resultMap.getOrDefault("result", "无");
            } catch (Exception e) {
                log.error("命令执行失败！cmd = {}", entry, e);
                cmdResult = "命令执行异常";
            }

            int currentResultToken = estimateTokens(cmdResult);
            if (currentResultToken > 512) {
                remark += "token:" + currentResultToken;
            }

            // 发送控制台
            sendNotify(String.format("|  %s  |  %s  |  %s  |  %s  |  %s |\n",
                    processBeforeSend(entry.getKey(), -1),
                    processBeforeSend(entry.getValue()[0], 32),
                    processBeforeSend(cmdResult, -1),
                    processBeforeSend(targetDesc, -1),
                    processBeforeSend(remark, -1)
            ));
            cmdHistory.add(new CmdHistoryItem(nextCmd, cmdResult, targetDesc, headerMessage, BigDecimal.ONE, false, false));
        }
    }

    private void calculateImportRate() {
        List<McpProcess> processList = Lists.newArrayList();
        processList.addAll(historyProcess);
        processList.add(this);

        List<CmdHistoryItem> historyCmd = Lists.newArrayList();

        StringBuilder allResult = new StringBuilder();
        for (McpProcess mcpProcess : processList) {
            for (CmdHistoryItem cmdHistoryItem : mcpProcess.getCmdHistory()) {
                allResult.append(cmdHistoryItem.fetchResult());
                historyCmd.add(cmdHistoryItem);
            }
        }

        // 计算重要度
        for (int i = 0; i < historyCmd.size(); i++) {
            CmdHistoryItem cmdHistoryItem = historyCmd.get(i);
            if (StringUtils.isBlank(cmdHistoryItem.fetchResult())) {
                cmdHistoryItem.setImportantRate(BigDecimal.ONE);
                return;
            }
            // 时效性
            BigDecimal agingRate = BigDecimal.valueOf(historyCmd.size() + i * 1.5)
                    .divide(BigDecimal.valueOf(historyCmd.size()).multiply(new BigDecimal(2)), 5, RoundingMode.HALF_UP);
            // 长度
            BigDecimal lengthRate = BigDecimal.valueOf(allResult.length())
                    .divide(BigDecimal.valueOf(allResult.length()).add(BigDecimal.valueOf(cmdHistoryItem.fetchResult().length())),
                            5, RoundingMode.HALF_UP);

            // 需求相关度
            BigDecimal requirementRate = BigDecimal.ONE;
            cmdHistoryItem.setImportantRate(agingRate.multiply(lengthRate).multiply(requirementRate)
                    .setScale(5, RoundingMode.HALF_UP));
        }
    }

    private String processBeforeSend(String input, int width) {
        if (input.length() > 2048) {
            input = input.substring(0, 2048) + ".....";
        }
        if (input.length() > 256) {
            String summary = input.substring(0, 256);
            String detail = input.substring(256);
            return String.format("<details><summary>%s</summary>%s</details>",
                    replaceToken(splitLineByBr(summary, width)), replaceToken(detail));
        } else {
            return replaceToken(splitLineByBr(input, width));
        }
    }

    private String splitLineByBr(String input, int width) {
        if (width < 0) {
            return input;
        }
        // 按行处理，每行每32个字符插入一个<br/>
        String[] lines = input.split("\\n");
        StringBuilder result = new StringBuilder();
        for (int lineIdx = 0; lineIdx < lines.length; lineIdx++) {
            if (lineIdx > 0) {
                result.append("\n");
            }
            String line = lines[lineIdx];
            for (int i = 0; i < line.length(); i += width) {
                if (i > 0) {
                    result.append("\n");
                }
                result.append(line, i, Math.min(i + width, line.length()));
            }
        }
        return result.toString();
    }

    private String replaceToken(String input) {
        return input
                .replace("<", "＜")
                .replace(">", "＞")
                .replace("\r\n", "<br/>").replace("\n", "<br/>")
                .replace("|", "丨")
                .replace("`", "\\`");
    }

    public void terminate(String terminalMessage) {
        this.terminalMessage = terminalMessage;
        terminate = true;
    }

    public static List<String> parseNextCmd(String gptResult) {
        List<String> commands = new ArrayList<>();

        // 使用正则表达式匹配两个#之间的命令块
        Pattern blockPattern = Pattern.compile("#start#(.*?)#(?:end)#", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(gptResult);
        while (blockMatcher.find()) {
            String commandBlock = blockMatcher.group(1).trim();
            commands.add(commandBlock);
        }
        return commands;
    }

    public static List<String> parseNextTargets(String gptResult) {
        List<String> target = new ArrayList<>();

        // 使用正则表达式匹配两个#之间的命令块
        Pattern blockPattern = Pattern.compile("#target#(.*?)#(?:target)#", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(gptResult);
        while (blockMatcher.find()) {
            target.add(blockMatcher.group(1).trim());
        }
        return target;
    }

    public boolean processStream(String part, StringBuilder result, UsedToken usedToken, String input, List<String> messageBuffer) {
        // 内容
        String content = ChatGptSolution.parseStreamContent(part, "content");
        if (content != null) {
            messageBuffer.add(content);
            result.append(content);
            if (messageBuffer.size() >= 5) {
                // 判断是否包含修改语句
                String currentResult = result.toString();
                if (currentResult.contains("createFile {") || currentResult.contains("modifyFile {")) {
                    List<String> currentCmdList = parseNextCmd(currentResult);
                    // 如果当前已经包含了未执行过的读语句，那么则直接执行，防止读无效
                    List<String> readCmd = currentCmdList.stream()
                            .filter(e -> e.contains("readFile {") || e.contains("treeFile {"))
                            .collect(Collectors.toList());
                    Set<String> historyCmdSet = cmdHistory.stream().map(CmdHistoryItem::getCmdAndParam)
                            .collect(Collectors.toSet());
                    if (readCmd.stream().anyMatch(e -> !historyCmdSet.contains(e))) {
                        // 发送流式消息
                        sendStreamMessage(String.join("", messageBuffer));
                        messageBuffer.clear();
                        usedToken.usedInputToken = estimateTokens(input);
                        usedToken.usedOutputToken = estimateTokens(result.toString());
                        sendStreamMessage("(存在未执行读取命令，系统拦截修改指令)");
                        return false;
                    }
                }

                // 发送流式消息
                sendStreamMessage(String.join("", messageBuffer));
                messageBuffer.clear();
            }
        }

        // 统计cached_tokens
        if (ChatGptSolution.hasUsage(part)) {
            usedToken.totalCachedTokens = ChatGptSolution.queryCachedTokenNum(part);
            usedToken.usedInputToken = ChatGptSolution.queryTokenNum(part, "prompt_tokens");
            usedToken.usedOutputToken = ChatGptSolution.queryTokenNum(part, "completion_tokens");
        } else if (terminate){
            usedToken.usedInputToken = estimateTokens(input);
            usedToken.usedOutputToken = estimateTokens(result.toString());
        }
        return !terminate;
    }

    private void sendStreamMessage(String content) {
        StreamMessage msg = new StreamMessage();
        msg.setContent(content);
        msg.setChatterId(robotId);
        msg.setSessionId(sessionId);
        msg.setCreateTime(new Date());
        messageSolution.sendStreamMessage(sessionId, msg);
    }

    private void stopStream() {
        StreamMessage msg = new StreamMessage();
        msg.setContent("");
        msg.setChatterId(robotId);
        msg.setSessionId(sessionId);
        msg.setCreateTime(new Date());
        msg.setEnd(true);
        messageSolution.sendStreamMessage(sessionId, msg);
    }

    public Map.Entry<String, String[]> buildParam(String inputText) {
        Map<String, String[]> cmdParams = Maps.newLinkedHashMap();

        for (String cmd : cmdDescList) {
            String[] split = cmd.split(" ");
            String cmdName = split[0].trim();
            if (inputText.startsWith(cmdName)) {
                cmdParams.put(cmdName, new String[]{
                        inputText.replace(cmdName, "").trim(),
                        processId
                });
            }
        }
        return cmdParams.entrySet().stream().findAny().orElse(null);
    }

    public String buildLogPrefix() {
        String parsed = CmdProxyConstant.MCP_LOG_PREFIX;
        StringBuilder requestMd = new StringBuilder();

        // history
        int lastCmdIdx = 0;
        for (int i = 0; i < historyProcess.size(); i++) {
            McpProcess mcpProcess = historyProcess.get(i);
            String historyUserRequest = mcpProcess.userRequest;
            requestMd.append(String.format("| %s | %s | %s |\n",i + 1, historyUserRequest
                    .replace("\r\n", "<br/>")
                    .replace("\n", "<br/>"), "已完成"));
        }

        // now
        requestMd.append(String.format("| %s | %s | %s |\n", historyProcess.size() + 1,
                processBeforeSend(userRequest, 64),
                "进行中"));

        parsed = parsed.replace(USER_REQUEST_PLACE_HOLDER, requestMd.toString());

        String userConfig = StringUtils.defaultString(systemSettings)
                + kvUtils.getStringOrDefault("mcpUserConfig_" + sessionId, "");
        parsed = parsed.replace(USER_CONFIG_PLACE_HOLDER, StringUtils.defaultIfBlank(userConfig, "无"));
        return parsed;
    }

    public String buildRequest() {
        String parsed;
        if (ProcessType.TODO.equals(processType) || ProcessType.TODO_WITH_QUESTION.equals(processType)) {
            parsed = kvUtils.getStringOrDefault("mcpTodoTemplate", CmdProxyConstant.MCP_TODO_TEMPLATE);
            parsed = parsed.replace(PROCESS_ID_HOLDER, processId);
        } else if (ProcessType.PROCESS_TODO.equals(processType)) {
            parsed = kvUtils.getStringOrDefault("mcpProcessTodoTemplate", CmdProxyConstant.MCP_PROCESS_TODO_TEMPLATE);
        } else if (ProcessType.QUESTION.equals(processType)) {
            parsed = kvUtils.getStringOrDefault("mcpQuestionTemplate", CmdProxyConstant.MCP_QUESTION_TEMPLATE);
            parsed = parsed.replace(PROCESS_ID_HOLDER, processId);
        } else {
            parsed = kvUtils.getStringOrDefault("mcpTemplate", CmdProxyConstant.MCP_TEMPLATE);
        }
        // cmdList
        StringBuilder cmdMd = new StringBuilder();
        for (String cmd : cmdDescList) {
            String[] split = cmd.split("#next#");
            cmdMd.append(String.format("| %s | %s |\n", split[0], split[1]));
        }
        parsed = parsed.replace(CMD_LIST_PLACE_HOLDER, cmdMd.toString());

        // userConfig
        String userConfig = StringUtils.defaultString(systemSettings)
                + kvUtils.getStringOrDefault("mcpUserConfig_" + sessionId, "");
        parsed = parsed.replace(USER_CONFIG_PLACE_HOLDER, StringUtils.defaultIfBlank(userConfig, "无"));

        StringBuilder cmdHistoryMd = new StringBuilder();
        StringBuilder requestMd = new StringBuilder();

        String cmdHistoryTemp = "(%s) %s\n%s执行目的:%s\n关联需求编号:%s\n执行结果:%s\n\n";

        // history
        int lastCmdIdx = 0;
        for (int i = 0; i < historyProcess.size(); i++) {
            McpProcess mcpProcess = historyProcess.get(i);
            String historyUserRequest = mcpProcess.userRequest;
            requestMd.append(String.format("| %s | %s | %s |\n",i + 1, historyUserRequest
                    .replace("\r\n", "<br/>")
                    .replace("\n", "<br/>"), "已完成"));

            // 排除不展示的历史指令
            List<CmdHistoryItem> showCmdList = mcpProcess.cmdHistory.stream()
                    .filter(e -> !e.isHiddenItem()).collect(Collectors.toList());
            for (int j = 0; j < showCmdList.size(); j++) {
                CmdHistoryItem cmdHistoryItem = showCmdList.get(j);
                cmdHistoryMd.append(String.format(cmdHistoryTemp ,
                        (i+1) + "-"+ (j + 1), // 指令编号
                        cmdHistoryItem.getCmdAndParam(),
                        cmdHistoryItem.getHeaderMessage(),
                        cmdHistoryItem.getTarget(),
                        i + 1,  // 需求编号
                        cmdHistoryItem.fetchResult()
                ));
                lastCmdIdx = j + 1;
            }
        }

        // now
        requestMd.append(String.format("| %s | %s | %s |\n", historyProcess.size() + 1,
                userRequest.replace("\r\n", "<br/>").replace("\n", "<br/>"), "进行中"));
        for (int j = 0; j < cmdHistory.size(); j++) {
            CmdHistoryItem item = cmdHistory.get(j);
            cmdHistoryMd.append(String.format(cmdHistoryTemp ,
                    lastCmdIdx + j + 1, item.getCmdAndParam(), item.getHeaderMessage()
                    ,item.getTarget(), historyProcess.size() + 1,  item.fetchResult()));
        }

        parsed = parsed.replace(CMD_HISTORY_PLACE_HOLDER, cmdHistoryMd.toString());

        // user Request
        parsed = parsed.replace(USER_REQUEST_PLACE_HOLDER, requestMd.toString());
        return parsed;
    }

    public void sendNotify(String content) {
        char[] charArray = content.toCharArray();
        int batchSize = Math.max(charArray.length / 10, 10);
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < charArray.length; i++) {
            builder.append(charArray[i]);
            if (i % batchSize != 0 && i < charArray.length - 1) {
                continue;
            }
            // 发送流式消息
            StreamMessage msg = new StreamMessage();
            msg.setContent(builder.toString());
            msg.setChatterId(robotId);
            msg.setSessionId(sessionId);
            msg.setCreateTime(new Date());
            messageSolution.sendStreamMessage(sessionId, msg);
            builder = new StringBuilder();
            try {
                Thread.sleep(new Random().nextInt(100) + 10);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (terminate) {
                return;
            }
        }
    }

    public void sendNotifyImmediately(String content) {
        Message msg = new Message();
        msg.setContent(content);
        msg.setChatterId(robotId);
        msg.setSessionId(sessionId);
        msg.setCreateTime(new Date());
        messageSolution.insertMessage(sessionId, msg);
    }


    /**
     * 估算给定字符串的 token 数量
     * @param text 输入文本
     * @return 估算的 token 数（四舍五入后的整数）
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int otherChars   = 0;

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        double tokens = chineseChars * 0.6 + otherChars * 0.3;
        return (int) Math.round(tokens);
    }

    private static boolean isChinese(char c) {
        return '\u4e00' <= c && c <= '\u9fa5';
    }

    public int getUsedInputToken() {
        return usedInputToken;
    }

    public int getUsedOutputToken() {
        return usedOutputToken;
    }

    public String getProcessId() {
        return processId;
    }

    public int getTotalCachedTokens() {
        return totalCachedTokens;
    }

    public static class UsedToken {
        private int totalCachedTokens;
        private int usedInputToken;
        private int usedOutputToken;
    }

    public static class ProcessType{
        public static final String TASK = "task";
        public static final String TODO = "todo";
        public static final String TODO_WITH_QUESTION = "todo_with_question";
        public static final String PROCESS_TODO = "process_todo";
        public static final String QUESTION = "question";
    }
}

