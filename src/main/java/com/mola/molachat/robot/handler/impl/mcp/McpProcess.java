package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    private static String TEMPLATE =
            "你是专业的指令执行者，需要通过执行指令和分析结果，实现用户的需求。\n" +
            "\n" +
            "#### 1、要求\n" +
            "\n" +
            "（1）指令以#start#开头，#end#结尾，每个指令+参数占一行。\n" +
            "（2）请保证本次输出的所有指令，指令名相同\n" +
            "（3）在每一条指令的#end#后输出执行该命令的目的，如#target#这是一条备注，简要说明了执行指令的目的#target#\n" +
            "（4）执行完成的指令已经满足用户需求时，请输出#start#无指令#end#\n" +
            "\n" +
            "#### 2、用户设置\n" +
            "%USER_CONFIG%\n" +
            "#### 3、用户需求列表\n" +
            "\n" +
            "| 编号 | 需求内容 | 执行状态   |\n" +
            "| ---- | -------- | ---------- |\n" +
            "%USER_REQUEST%" +
            "\n" +
            "#### 4、可使用的指令\n" +
            "\n" +
            "| 指令                     | 描述                       |\n" +
            "| ------------------------ | -------------------------- |\n" +
            "%CMD_LIST%" +
            "\n" +
            "#### 5、执行完成的指令\n" +
            "\n" +
            "%CMD_HISTORY%";

    private String robotId;

    private String sessionId;

    private List<CmdHistoryItem> cmdHistory;

    private String userRequest;

    private List<String> cmdDescList;

    private List<String> cmdList;

    private volatile boolean terminate;

    private transient ChatGptSolution chatGptSolution;

    private transient KvUtils kvUtils;

    private transient MessageSolution messageSolution;

    private int usedInputToken;

    private int usedOutputToken;

    private transient List<McpProcess> historyProcess;

    private transient boolean useMemory;

    public void start() {
        int errorRepeatCnt = 0;
        while (!terminate) {
            String request = buildRequest();
            if (Objects.equals("Y", kvUtils.getString("logMcpRequest"))) {
                sendNotifyImmediately(request);
            }
            if (estimateTokens(request) > 30000) {
                sendNotify("模型单次输入超过最大限制token数");
                terminate = true;
                break;
            }
            if (errorRepeatCnt >= 3) {
                sendNotify("检测到错误循环，请调整提示词重试");
                terminate = true;
                break;
            }
            StringBuilder result = new StringBuilder();
            chatGptSolution.invoke(request, null, true, part -> processStream(part, result), 0.1);
            usedInputToken += estimateTokens(request);
            usedOutputToken += estimateTokens(result.toString());
            // 提取命令列表
            List<String> nextCmdList = parseNextCmd(result.toString());
            // 提取备注列表
            List<String> remarks = parseNextRemarks(result.toString());
            Map<String, String> cmd2Remark = Maps.newHashMap();
            if (nextCmdList.size() == remarks.size()) {
                for (int i = 0; i < nextCmdList.size(); i++) {
                    cmd2Remark.put(nextCmdList.get(i), remarks.get(i));
                }
            }
            if (CollectionUtils.isEmpty(nextCmdList)) {
                terminate = true;
                break;
            }
            for (String nextCmd : nextCmdList) {
                if (terminate) {
                    break;
                }
                if (Objects.equals(nextCmd, "无指令")) {
                    terminate = true;
                    break;
                }
                Map.Entry<String, String[]> entry = splitParam(nextCmd);
                if (!cmdList.contains(entry.getKey())) {
                    sendNotify("未识别命令，流程终止");
                    terminate = true;
                    break;
                }
                // 执行命令
                CmdInvokeResponse<CmdResponseContent> cmdResp = CmdSender.INSTANCE
                        .send(entry.getKey(), sessionId, entry.getValue());

                Map<String, String> resultMap = cmdResp.getData().getResultMap();

                String cmdResult = resultMap.getOrDefault("result", "无");
                sendNotifyImmediately(String.format("执行命令%s完成\n入参:%s\n结果:%s",
                        entry.getKey(), JSON.toJSON(entry.getValue()), cmdResult));

                // 查询重复命令
                CmdHistoryItem repeatCmd = null;
                for (CmdHistoryItem cmdHistoryItem : cmdHistory) {
                    if (Objects.equals(nextCmd, cmdHistoryItem.getCmdAndParam())) {
                        repeatCmd = cmdHistoryItem;
                    }
                }
                // 存在重复命令，将最新结果进行替换
                String remark = cmd2Remark.getOrDefault(nextCmd, "无");
                if (repeatCmd != null) {
                    if (repeatCmd == cmdHistory.get(cmdHistory.size() - 1)) {
                        errorRepeatCnt ++;
                        remark = "[警告] 当前命令和上一个命令完全重复，后续禁止输出该命令:" + repeatCmd.getCmdAndParam();
                    } else {
                        sendNotifyImmediately("存在历史重复命令，将最新结果替换上下文\n重复命令: " + repeatCmd.getCmdAndParam());
                    }
                    // 上下文压缩
                    if (repeatCmd.getResult().length() > 512) {
                        repeatCmd.setResult("检测到相同命令被重复执行，当前执行结果已隐藏");
                    }
                } else {
                    errorRepeatCnt = 0;
                }
                cmdHistory.add(new CmdHistoryItem(nextCmd, cmdResult, remark));
            }
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

    public void terminate() {
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

    public static List<String> parseNextRemarks(String gptResult) {
        List<String> remarks = new ArrayList<>();

        // 使用正则表达式匹配两个#之间的命令块
        Pattern blockPattern = Pattern.compile("#target#(.*?)#(?:target)#", Pattern.DOTALL);
        Matcher blockMatcher = blockPattern.matcher(gptResult);
        while (blockMatcher.find()) {
            remarks.add(blockMatcher.group(1).trim());
        }
        return remarks;
    }

    public boolean processStream(String part, StringBuilder result) {
        try {
            Thread.sleep(new Random().nextInt(50) + 50);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        boolean stop = ChatGptSolution.isStreamResultStop(part) || terminate;

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
            msg.setEnd(stop);
            messageSolution.sendStreamMessage(sessionId, msg);
        }
        return !stop;
    }

    public Map.Entry<String, String[]> splitParam(String inputText) {
        Map<String, String[]> cmdParams = Maps.newLinkedHashMap();

        for (String cmd : cmdDescList) {
            String[] split = cmd.split(" ");
            String cmdName = split[0].trim();
            if (inputText.startsWith(cmdName)) {
                cmdParams.put(cmdName, new String[]{
                        inputText.replace(cmdName, "").trim()
                });
            }
        }
        return cmdParams.entrySet().stream().findAny().orElse(null);
    }

    public String buildRequest() {
        String parsed = kvUtils.getStringOrDefault("mcpTemplate", TEMPLATE);
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


        String cmdHistoryTemp = "(%s) %s\n执行目的:%s\n关联需求编号:%s\n执行结果:%s\n\n";

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
                        j + 1, cmdHistoryItem.getCmdAndParam(),cmdHistoryItem.getRemark(), i + 1,  cmdHistoryItem.getResult()));
                lastCmdIdx = j + 1;
            }
        }

        // now
        requestMd.append(String.format("| %s | %s | %s |\n", historyProcess.size() + 1,
                userRequest.replace("\r\n", "<br/>").replace("\n", "<br/>"), "进行中"));
        for (int j = 0; j < cmdHistory.size(); j++) {
            CmdHistoryItem item = cmdHistory.get(j);
            cmdHistoryMd.append(String.format(cmdHistoryTemp ,
                    lastCmdIdx + j + 1, item.getCmdAndParam(),item.getRemark(), historyProcess.size() + 1,  item.getResult()));
        }

        parsed = parsed.replace(CMD_HISTORY_PLACE_HOLDER, cmdHistoryMd.toString());

        // user Request
        parsed = parsed.replace(USER_REQUEST_PLACE_HOLDER, requestMd.toString());
        return parsed;
    }

    public void sendNotify(String content) {
        char[] charArray = content.toCharArray();
        int batchSize = Math.max(charArray.length / 100, 2);
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
            msg.setEnd(i == charArray.length - 1 || terminate);
            messageSolution.sendStreamMessage(sessionId, msg);
            builder = new StringBuilder();
            try {
                Thread.sleep(new Random().nextInt(30) + 10);
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
}

