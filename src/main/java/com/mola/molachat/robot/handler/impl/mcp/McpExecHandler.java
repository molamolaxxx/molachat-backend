package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.alibaba.nacos.common.utils.MapUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.robot.model.Pair;
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static String TEMPLATE = " \n" +
            "\n" +
            "你是专业的指令执行者，需要通过执行指令和分析结果，实现用户的需求。\n" +
            "\n" +
            "#### 1、要求\n" +
            "\n" +
            "（1）指令以#start#开头，#end#结尾，每个指令+参数占一行。\n" +
            "\n" +
            "（2）每次输出一组指令时，需要保证这一组指令名相同\n" +
            "\n" +
            "（3）当前执行完成的指令已经满足用户需求时，无需执行后续的指令，请输出#start#无指令#end#\n" +
            "\n" +
            "#### 2、用户需求列表\n" +
            "\n" +
            "| 编号 | 需求内容 | 执行状态   |\n" +
            "| ---- | -------- | ---------- |\n" +
            "%USER_REQUEST%" +
            "\n" +
            "#### 3、可使用的指令\n" +
            "\n" +
            "| 指令                     | 描述                       |\n" +
            "| ------------------------ | -------------------------- |\n" +
            "%CMD_LIST%" +
            "\n" +
            "#### 4、已经执行完成的指令\n" +
            "\n" +
            "%CMD_HISTORY%" +
            "\n" +
            "#### 5、用户设置\n" +
            "%USER_CONFIG%\n";

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
                    useMemory,
                    Lists.newArrayList()
            );
            processMap.put(processUniKey, mcpProcess);

            // 开启
            mcpProcess.start();

            return MessageSendAction.withResp(
                    String.format("Mcp流程执行完成，输入token：%s，输出token：%s", mcpProcess.usedInputToken, mcpProcess.usedOutputToken));
        } catch (Exception e) {
            log.error("McpExecHandler error", e);
            return MessageSendAction.withResp("Mcp流程执行失败，原因：" + e.getMessage());
        } finally {
            processMap.remove(processUniKey);
        }
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

    @NoArgsConstructor
    @AllArgsConstructor
    @Slf4j
    @Data
    public static class McpProcess {

        private String robotId;

        private String sessionId;

        private List<Pair<String, String>> cmdHistory;

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

        private List<String> hints;

        public void start() {
            while (!terminate) {
                String request = buildRequest();
                sendNotifyImmediately(request);
                if (request.length() > 30000) {
                    sendNotify("模型单次输入超过最大限制");
                    terminate = true;
                    break;
                }
                StringBuilder result = new StringBuilder();
                chatGptSolution.invoke(request, null, true, part -> processStream(part, result), 0.0);
                usedInputToken += estimateTokens(request);
                usedOutputToken += estimateTokens(result.toString());
                hints.clear();
                // 提取命令列表
                List<String> nextCmdList = parseNextCmd(result.toString());
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
                    Pair<String, String> repeatCmd = cmdHistory.stream()
                            .filter(e -> Objects.equals(nextCmd, e.getFirst()))
                            .findAny().orElse(null);
                    // 存在重复命令，将最新结果进行替换
                    if (repeatCmd != null) {
                        if (repeatCmd == cmdHistory.get(cmdHistory.size() - 1)) {
                            hints.add("请勿重复执行命令" + repeatCmd.getFirst());
                        } else {
                            sendNotifyImmediately("存在历史重复命令，将最新结果替换上下文\n重复命令: " + repeatCmd.getFirst());
                        }
                        cmdHistory.remove(repeatCmd);
                    }
                    cmdHistory.add(Pair.of(nextCmd, cmdResult));
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
            String parsed = TEMPLATE;
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


            String cmdHistoryTemp = "(%s)、%s\n关联需求编号:%s\n执行结果:%s\n\n";

            // history
            int lastCmdIdx = 0;
            for (int i = 0; i < historyProcess.size(); i++) {
                McpProcess mcpProcess = historyProcess.get(i);
                String historyUserRequest = mcpProcess.userRequest;
                requestMd.append(String.format("| %s | %s | %s |\n",i + 1, historyUserRequest
                        .replace("\r\n", "<br/>")
                        .replace("\n", "<br/>"), "已完成"));
                for (int j = 0; j < mcpProcess.cmdHistory.size(); j++) {
                    Pair<String, String> pair = mcpProcess.cmdHistory.get(j);
                    cmdHistoryMd.append(String.format(cmdHistoryTemp ,
                            j + 1, pair.getFirst(), i + 1, pair.getSecond()));
                    lastCmdIdx = j + 1;
                }
            }

            // now
            requestMd.append(String.format("| %s | %s | %s |\n", historyProcess.size() + 1,
                    userRequest.replace("\r\n", "<br/>").replace("\n", "<br/>"), "进行中"));
            for (int j = 0; j < cmdHistory.size(); j++) {
                Pair<String, String> pair = cmdHistory.get(j);
                cmdHistoryMd.append(String.format(cmdHistoryTemp ,
                        lastCmdIdx + j + 1, pair.getFirst(), historyProcess.size() + 1, pair.getSecond()));
            }

            parsed = parsed.replace(CMD_HISTORY_PLACE_HOLDER, cmdHistoryMd.toString());

            // user Request
            parsed = parsed.replace(USER_REQUEST_PLACE_HOLDER, requestMd.toString());

            if (hints.size() != 0) {
                parsed = parsed + "\n#### 6、警告\n" + String.join("\n", hints);
            }
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
