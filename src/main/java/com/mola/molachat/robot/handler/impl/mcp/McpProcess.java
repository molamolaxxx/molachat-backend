package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
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

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private int estimateUsedInputToken;

    private int estimateUsedOutputToken;

    private int totalCachedTokens;

    private transient List<McpProcess> historyProcess;

    private transient boolean useMemory;

    private transient volatile String terminalMessage;

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
        if (useMemory) {
            String mcpHistoryProcessStr = kvUtils.getString("mcpHistoryProcess_" + sessionId);
            List<McpProcess> processList = Lists.newArrayList();
            processList.addAll(historyProcess);
            processList.add(this);
            // 保存上下文
            kvUtils.set("mcpHistoryProcess_" + sessionId, JSON.toJSONString(processList), robotId);
        }
    }

    private void loop() {
        int errorRepeatCnt = 0;
        sendNotify(buildLogPrefix());
        while (!terminate) {
            String request = buildRequest();
            if (estimateTokens(request) >
                    Integer.parseInt(kvUtils.getStringOrDefault("mcpPerMaxToken", "30000"))) {
                terminate("模型单次输入超过最大限制token数");
                break;
            }
            if (errorRepeatCnt >= 3) {
                terminate("检测到错误循环，请调整提示词重试");
                break;
            }
            StringBuilder result = new StringBuilder();

            sendNotify("#### 模型输出\n");
            Map<String, Object> streamOptions = Maps.newHashMap();
            streamOptions.put("include_usage", true);
            chatGptSolution.invoke(request, null, true, streamOptions,
                    part -> processStream(part, result),
                    kvUtils.getDoubleOrDefault("mcpTemperature", 0.1));

            estimateUsedInputToken += estimateTokens(request);
            estimateUsedOutputToken += estimateTokens(result.toString());
            // 提取命令列表
            List<String> nextCmdList = parseNextCmd(result.toString());
            // 提取备注列表
            List<String> targets = parseNextTargets(result.toString());
            Map<String, String> cmd2TargetDesc = Maps.newHashMap();
            if (nextCmdList.size() == targets.size()) {
                for (int i = 0; i < nextCmdList.size(); i++) {
                    cmd2TargetDesc.put(nextCmdList.get(i), targets.get(i));
                }
            }
            if (CollectionUtils.isEmpty(nextCmdList)) {
                terminate(null);
                break;
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
                // 执行命令
                CmdInvokeResponse<CmdResponseContent> cmdResp = CmdSender.INSTANCE
                        .send(entry.getKey(), sessionId, entry.getValue());

                Map<String, String> resultMap = cmdResp.getData().getResultMap();

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
                        errorRepeatCnt ++;
                        headerMessage = "[警告] 检测到重复命令，后续禁止输出该命令:" + repeatCmd.getCmdAndParam() + "\n";
                        remark = "重复命令警告，当前错误次数:" + errorRepeatCnt;
                    } else {
                        remark = "存在历史重复命令";
                    }
                    // 上下文压缩
                    if (repeatCmd.getResult().length() > 512) {
                        repeatCmd.setResult("检测到相同命令被重复执行，当前执行结果已隐藏");
                        remark += "，上下文中隐藏历史命令执行结果";
                    }
                } else {
                    errorRepeatCnt = 0;
                }

                String cmdResult = resultMap.getOrDefault("result", "无");

                // 发送控制台
                sendNotify(String.format("|  %s  |  %s  |  %s  |  %s  |  %s |\n",
                        processBeforeSend(entry.getKey(), -1),
                        processBeforeSend(entry.getValue()[0], 32),
                        processBeforeSend(cmdResult, -1),
                        processBeforeSend(targetDesc, -1),
                        processBeforeSend(remark, -1)
                ));
                cmdHistory.add(new CmdHistoryItem(nextCmd, cmdResult, targetDesc, headerMessage));
            }
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
                    result.append("<br/>");
                }
                result.append(line, i, Math.min(i + width, line.length()));
            }
        }
        return result.toString();
    }

    private String replaceToken(String input) {
        return input.replace("\r\n", "<br/>").replace("\n", "<br/>")
                .replace("|", "丨")
                .replace("`", "\\`");       // 圆括号
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

            // 分割每行命令
            String[] lines = commandBlock.split("\\r?\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    commands.add(line.trim());
                }
            }
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

    public boolean processStream(String part, StringBuilder result) {
        try {
            Thread.sleep(new Random().nextInt(50) + 50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 内容
        String content = ChatGptSolution.parseStreamContent(part, "content");
        if (content != null) {
            result.append(content);
            // 发送流式消息
            StreamMessage msg = new StreamMessage();
            msg.setContent(content);
            msg.setChatterId(robotId);
            msg.setSessionId(sessionId);
            msg.setCreateTime(new Date());
            messageSolution.sendStreamMessage(sessionId, msg);
        }
        boolean streamResultStop = ChatGptSolution.hasUsage(part) || terminate;
        // 统计cached_tokens
        if (ChatGptSolution.hasUsage(part)) {
            this.totalCachedTokens += ChatGptSolution.queryCachedTokenNum(part);
            this.usedInputToken += ChatGptSolution.queryTokenNum(part, "prompt_tokens");
            this.usedOutputToken += ChatGptSolution.queryTokenNum(part, "completion_tokens");
        } else if (terminate){
            this.usedInputToken = estimateUsedInputToken;
            this.usedOutputToken = estimateUsedOutputToken;
        }
        return !streamResultStop;
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
        return parsed;
    }

    public String buildRequest() {
        String parsed = kvUtils.getStringOrDefault("mcpTemplate", CmdProxyConstant.MCP_TEMPLATE);
        // cmdList
        StringBuilder cmdMd = new StringBuilder();
        for (String cmd : cmdDescList) {
            String[] split = cmd.split("#next#");
            cmdMd.append(String.format("| %s | %s |\n", split[0], split[1]));
        }
        parsed = parsed.replace(CMD_LIST_PLACE_HOLDER, cmdMd.toString());

        // userConfig
        parsed = parsed.replace(USER_CONFIG_PLACE_HOLDER,
                kvUtils.getStringOrDefault("mcpUserConfig_" + sessionId, "无"));

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
            for (int j = 0; j < mcpProcess.cmdHistory.size(); j++) {
                CmdHistoryItem cmdHistoryItem = mcpProcess.cmdHistory.get(j);
                cmdHistoryMd.append(String.format(cmdHistoryTemp ,
                        j + 1, cmdHistoryItem.getCmdAndParam(), cmdHistoryItem.getHeaderMessage(),
                        cmdHistoryItem.getTarget(), i + 1,  cmdHistoryItem.getResult()));
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
                    ,item.getTarget(), historyProcess.size() + 1,  item.getResult()));
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
                Thread.sleep(new Random().nextInt(200) + 10);
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
}

