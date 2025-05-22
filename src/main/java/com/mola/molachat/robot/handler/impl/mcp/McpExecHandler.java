package com.mola.molachat.robot.handler.impl.mcp;

import com.alibaba.fastjson.JSON;
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
import com.mola.molachat.robot.solution.ChatGptSolution;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.solution.MessageSolution;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static String TEMPLATE = "你是一个指令执行者，你需要通过执行指令和分析结果，完成用户的需求。可使用的指令如下：\n" +
            "%CMD_LIST%" +
            "\n" +
            "用户需求如下：%USER_REQUEST%\n" +
            "\n" +
            "用户配置如下：\n" +
            "%USER_CONFIG%" +
            "\n" +
            "已经执行完成的指令列表：\n" +
            "%CMD_HISTORY%" +
            "\n" +
            "当前有指令时，你只需要输出一组相同类型的指令，指令以#start#开头，#end#结尾，每个指令占一行\n" +
            "当前执行完成的指令已经满足用户需求时，无需执行后续的指令，请输出#start#无指令#end#\n，如果用户需要分析指令执行结果，请满足用户需求";
    @Resource
    private ChatGptSolution chatGptSolution;

    @Resource
    private KvUtils kvUtils;

    @Resource
    private MessageSolution messageSolution;

    @Override
    public BaseAction handler(MessageReceiveEvent messageReceiveEvent) {
        String robotId = messageReceiveEvent.getRobotChatter().getId();
        String sessionId = messageReceiveEvent.getSessionId();
        String processUniKey = String.format("%s_%s", robotId, sessionId);
        String userRequest = messageReceiveEvent.getMessage().getContent();
        if (Objects.equals(userRequest, "#clear-mcp#")) {
            McpProcess mcpProcess = processMap.get(processUniKey);
            if (mcpProcess != null) {
                mcpProcess.terminate();
            }
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
                    messageSolution
            );
            processMap.put(processUniKey, mcpProcess);

            // 开启
            mcpProcess.start();

            return MessageSendAction.withResp("Mcp流程执行完成");
        } catch (Exception e) {
            log.error("McpExecHandler error", e);
            return MessageSendAction.withResp("Mcp流程执行失败，原因：" + e.getMessage());
        } finally {
            processMap.remove(processUniKey);
        }

    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    @AllArgsConstructor
    @Slf4j
    public static class McpProcess {

        private String robotId;

        private String sessionId;

        private List<Pair<String, String>> cmdHistory;

        private String userRequest;

        private List<String> cmdDescList;

        private List<String> cmdList;

        private volatile boolean terminate;

        private ChatGptSolution chatGptSolution;

        private KvUtils kvUtils;

        private MessageSolution messageSolution;

        public void start() {
            while (!terminate) {
                String result = chatGptSolution.invoke(buildRequest());
                sendNotify(result);
                // 提取命令列表
                List<String> nextCmdList = parseNextCmd(result);
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
                    if (CollectionUtils.isNotEmpty(cmdHistory)) {
                        String latestCmd = cmdHistory.get(cmdHistory.size() - 1).getFirst();
                        if (Objects.equals(latestCmd, nextCmd)) {
                            sendNotify("识别到重复命令，流程终止");
                            terminate = true;
                            break;
                        }
                    }
                    // 执行命令
                    CmdInvokeResponse<CmdResponseContent> cmdResp = CmdSender.INSTANCE
                            .send(entry.getKey(), sessionId, entry.getValue());

                    Map<String, String> resultMap = cmdResp.getData().getResultMap();

                    String cmdResult = resultMap.getOrDefault("result", "无");
                    sendNotifyImmediately(String.format("执行命令%s完成\n入参:%s\n结果:%s",
                            entry.getKey(), JSON.toJSON(entry.getValue()), cmdResult));
                    cmdHistory.add(Pair.of(nextCmd, cmdResult));
                }
            }
        }

        public void terminate() {
            terminate = true;
        }

        public List<String> parseNextCmd(String gptResult) {
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


            blockPattern = Pattern.compile("#start#(.*?)#(?:finish)#", Pattern.DOTALL);
            blockMatcher = blockPattern.matcher(gptResult);
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

        public Map.Entry<String, String[]> splitParam(String inputText) {
            Map<String, String[]> cmdParams = Maps.newLinkedHashMap();
            String[] split = inputText.split(" ");
            if (split.length == 1) {
                cmdParams.put(split[0], new String[0]);
            } else {
                cmdParams.put(split[0], new String[]{
                        String.join(" ", Arrays.copyOfRange(split, 1, split.length))
                });
            }

            return cmdParams.entrySet().stream().findAny().orElse(null);
        }

        public String buildRequest() {
            String parsed = TEMPLATE;
            // cmdList
            parsed = parsed.replace(CMD_LIST_PLACE_HOLDER, joinWithIndex(cmdDescList));

            // userConfig
            parsed = parsed.replace(USER_CONFIG_PLACE_HOLDER,
                    kvUtils.getStringOrDefault("mcpUserConfig_" + sessionId, "无"));

            // history
            List<String> history = cmdHistory.stream()
                    .map(e -> String.format("%s\n执行结果：%s", e.getFirst(), e.getSecond()))
                    .collect(Collectors.toList());
            parsed = parsed.replace(CMD_HISTORY_PLACE_HOLDER, joinWithIndex(history));

            // user Request
            parsed = parsed.replace(USER_REQUEST_PLACE_HOLDER, userRequest);
            return parsed;
        }

        private String joinWithIndex(List<String> input) {
            if (CollectionUtils.isEmpty(input)) {
                return "空\n";
            }
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0; i < input.size(); i++) {
                stringBuilder.append(String.format("%s、%s", i + 1, input.get(i)));
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
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
    public CmdDescription cmdDescription(String robotId, String sessionId) {
        String processUniKey = String.format("%s_%s", robotId, sessionId);
        McpProcess mcpProcess = processMap.get(processUniKey);
        if (mcpProcess != null) {
            return CmdDescription.builder()
                    .cmdName("#clear-mcp#")
                    .cmdDesc("清空mcp流程")
                    .executeScript("sendMessageInner('#clear-mcp#')")
                    .build();
        }
        String userSetting = kvUtils.getStringOrDefault("mcpUserConfig_" + sessionId, "无");
        return CmdDescription.builder()
                .cmdName("#settings#")
                .cmdDesc("Mcp用户设置")
                .executeScript(String.format("popupAndSendCmd('#settings#','%s')", Base64Util.encodeBase64(userSetting)))
                .build();
    }
}
