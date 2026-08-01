package com.mola.molachat.robot.handler.impl.acp;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.solution.TeamAcpExecSolution;
import com.mola.molachat.team.solution.TeamRobotProjectionSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: ACP执行handler，负责向ACP会话发送消息及管理会话状态
 * @date : 2026-03-15 17:29
 **/
@Component
@Slf4j
public class AcpExecHandler implements IRobotEventHandler<MessageReceiveEvent, BaseAction> {

    private static final String CMD_ACP_CANCEL = "#acp-cancel#";
    private static final String CMD_ACP_FORCE_STOP = "#stop-stream#";
    private static final String CMD_NEW_SESSION = "#new-session#";
    private static final String CMD_ACP_DREAM = "#acp-dream#";
    private static final String CMD_LIST_SESSIONS = "#list-sessions#";
    private static final String CMD_RESTORE_SESSION = "#restore-session#";

    @Resource
    private SessionService sessionService;

    @Resource
    private MessageSolution messageSolution;

    @Resource
    private TeamAcpExecSolution teamAcpExecSolution;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    private static final String FILE_DOWNLOAD_BASE_URL = "https://106.54.193.10:8550/chat/files/";

    @Override
    public BaseAction handler(MessageReceiveEvent messageReceiveEvent) {
        if (TeamRobotProjectionSolution.TEAM_ACP_GROUP.equals(
                messageReceiveEvent.getRobotChatter().getRobotGroup())) {
            return teamAcpExecSolution.handle(messageReceiveEvent);
        }
        String sessionId = messageReceiveEvent.getSessionId();
        String userMessage = messageReceiveEvent.getMessage().getContent();

        // 处理取消prompt命令
        if (CMD_ACP_CANCEL.equals(userMessage)) {
            return invokeAcpCmd("acpCancelPrompt", sessionId);
        }

        // 强制终止本地流式输出
        if (CMD_ACP_FORCE_STOP.equals(userMessage)) {
            String robotId = messageReceiveEvent.getRobotChatter().getId();
            boolean stopped = messageSolution.forceStopStream(robotId, sessionId);
            return stopped
                    ? MessageSendAction.skip()
                    : MessageSendAction.withResp("当前没有进行中的流式输出");
        }

        // 处理清除会话命令
        if (CMD_NEW_SESSION.equals(userMessage)) {
            return invokeAcpCmd("acpNewSession", sessionId);
        }

        // 处理记忆整理命令
        if (CMD_ACP_DREAM.equals(userMessage)) {
            return invokeAcpCmd("acpMemoryDream", sessionId);
        }

        // 处理获取会话列表命令
        if (CMD_LIST_SESSIONS.equals(userMessage)) {
            return invokeListSessions(messageReceiveEvent);
        }

        // 处理恢复会话命令，格式: #restore-session# <sessionId>
        if (userMessage.startsWith(CMD_RESTORE_SESSION)) {
            String targetSessionId = userMessage.substring(CMD_RESTORE_SESSION.length()).trim();
            return invokeRestoreSession(sessionId, targetSessionId);
        }

        // 默认：向ACP发送消息，先检查状态
        String status = getAcpStatus(sessionId);
        if (!"READY".equals(status)) {
            return MessageSendAction.withResp("ACP当前状态为 " + status + "，无法发送消息，请等待就绪后再试");
        }

        try {
            // 收集当前消息之前连续的文件消息，每个文件以文件名->下载URL的形式存储
            List<Map<String, String>> files = collectRecentFileUrls(sessionId, messageReceiveEvent);

            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            paramMap.put("message", userMessage);
            if (!CollectionUtils.isEmpty(files)) {
                paramMap.put("files", files);
            }
            String paramJson = JSON.toJSONString(paramMap);

            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send("acpSendMessage", sessionId, new String[]{paramJson});
            if (response == null || response.getData() == null) {
                return MessageSendAction.withResp("ACP消息发送失败：响应为空");
            }
            return MessageSendAction.skip();
        } catch (Exception e) {
            log.error("AcpExecHandler 发送消息失败, sessionId={}", sessionId, e);
            return MessageSendAction.withResp("ACP消息发送失败: " + e.getMessage());
        }
    }

    /**
     * 从当前会话中收集当前消息之前、连续的、当前用户发送的文件消息，返回文件下载URL
     * @return List<Map<String, String>>，每个Map中key为文件名，value为文件的下载URL
     */
    private List<Map<String, String>> collectRecentFileUrls(String sessionId, MessageReceiveEvent event) {
        List<Map<String, String>> files = new ArrayList<>();
        try {
            SessionDTO session = sessionService.findSession(sessionId);
            if (session == null) {
                return files;
            }
            List<Message> messageList = session.getMessageList();
            if (CollectionUtils.isEmpty(messageList) || messageList.size() <= 1) {
                return files;
            }
            String currentChatterId = event.getMessage().getChatterId();
            String robotChatterId = event.getRobotChatter().getId();

            // 从倒数第二条消息开始向前遍历（最后一条是当前消息），收集连续的FileMessage
            List<FileMessage> consecutiveFileMessages = new ArrayList<>();
            for (int i = messageList.size() - 2; i >= 0; i--) {
                Message msg = messageList.get(i);
                // 必须是当前用户发送的，非机器人发送
                if (!currentChatterId.equals(msg.getChatterId()) || robotChatterId.equals(msg.getChatterId())) {
                    break;
                }
                if (!(msg instanceof FileMessage)) {
                    break;
                }
                consecutiveFileMessages.add((FileMessage) msg);
            }

            // 反转为时间正序
            for (int i = consecutiveFileMessages.size() - 1; i >= 0; i--) {
                FileMessage fm = consecutiveFileMessages.get(i);
                String storedName = fm.fetchRealStoredFileName(false);
                if (storedName == null) {
                    continue;
                }
                String downloadUrl = FILE_DOWNLOAD_BASE_URL + storedName;
                Map<String, String> fileEntry = new HashMap<>();
                fileEntry.put(fm.getFileName(), downloadUrl);
                files.add(fileEntry);
            }
        } catch (Exception e) {
            log.error("AcpExecHandler collectRecentFileUrls 异常, sessionId={}", sessionId, e);
        }
        return files;
    }


    /**
     * 调用ACP远程命令（仅需groupId参数）
     */
    private BaseAction invokeAcpCmd(String cmdName, String sessionId) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            String paramJson = JSON.toJSONString(paramMap);

            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send(cmdName, sessionId, new String[]{paramJson});
            if (response == null || response.getData() == null) {
                return MessageSendAction.withResp(cmdName + " 执行失败：响应为空");
            }
            String result = response.getData().getResultMap().get("result");
            return MessageSendAction.withResp(result);
        } catch (Exception e) {
            log.error("AcpExecHandler {} 执行失败, sessionId={}", cmdName, sessionId, e);
            return MessageSendAction.withResp(cmdName + " 执行失败: " + e.getMessage());
        }
    }

    /**
     * 获取ACP会话列表，通过流式消息+openViewModal自动弹出模态框展示
     */
    private BaseAction invokeListSessions(MessageReceiveEvent event) {
        String sessionId = event.getSessionId();
        try {
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            String paramJson = JSON.toJSONString(paramMap);

            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send("acpListSessions", sessionId, new String[]{paramJson});
            if (response == null || response.getData() == null) {
                return MessageSendAction.withResp("获取会话列表失败：响应为空");
            }
            String result = response.getData().getResultMap().get("result");
            List<Map> sessions = JSON.parseArray(result, Map.class);
            if (CollectionUtils.isEmpty(sessions)) {
                return MessageSendAction.withResp("暂无历史会话");
            }
            StringBuilder table = new StringBuilder();
            table.append("| 会话预览 | 最后修改 | 操作 |\n");
            table.append("| ----- | ----- | ---- |\n");
            for (Map session : sessions) {
                String sid = String.valueOf(session.get("sessionId"));
                String preview = String.valueOf(session.get("preview"));
                String lastModified = String.valueOf(session.get("lastModified"));
                boolean current = Boolean.TRUE.equals(session.get("current"));
                String button = current
                        ? "<button class=\"blue-ring-button\" style=\"opacity:0.5\" onClick=\"showToast('当前正在使用的会话',1000)\">恢复</button>"
                        : String.format("<button class=\"blue-ring-button\" onClick=\"sendMessageInner('%s %s');$('#message-view-modal').modal('close')\">恢复</button>",
                                CMD_RESTORE_SESSION, sid);
                table.append(String.format("| %s | %s | %s |\n", preview, lastModified, button));
            }
            // 通过流式消息发送，自动弹出模态框
            StreamMessage msg = new StreamMessage();
            msg.setContent(table.toString());
            msg.setChatterId(event.getRobotChatter().getId());
            msg.setSessionId(sessionId);
            msg.setCreateTime(new Date());
            msg.setOpenViewModal(true);
            msg.setEnd(true);
            messageSolution.sendStreamMessage(sessionId, msg);
            return MessageSendAction.skip();
        } catch (Exception e) {
            log.error("AcpExecHandler 获取会话列表失败, sessionId={}", sessionId, e);
            return MessageSendAction.withResp("获取会话列表失败: " + e.getMessage());
        }
    }

    /**
     * 恢复指定ACP会话
     */
    private BaseAction invokeRestoreSession(String sessionId, String targetSessionId) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            paramMap.put("sessionId", targetSessionId);
            String paramJson = JSON.toJSONString(paramMap);

            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send("acpRestoreSession", sessionId, new String[]{paramJson});
            if (response == null || response.getData() == null) {
                return MessageSendAction.withResp("恢复会话失败：响应为空");
            }
            String result = response.getData().getResultMap().get("result");
            // 有值说明失败，返回错误信息；无值说明成功，cmd-proxy会异步发送流式消息
            return StringUtils.isNotBlank(result) ? MessageSendAction.withResp(result) : MessageSendAction.skip();
        } catch (Exception e) {
            log.error("AcpExecHandler 恢复会话失败, sessionId={}, targetSessionId={}", sessionId, targetSessionId, e);
            return MessageSendAction.withResp("恢复会话失败: " + e.getMessage());
        }
    }
    /**
     * 获取ACP会话状态
     * @return 状态字符串，如 READY、BUSY 等，获取失败时返回 UNKNOWN
     */
    private String getAcpStatus(String sessionId) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            String paramJson = JSON.toJSONString(paramMap);

            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send("acpGetStatus", sessionId, new String[]{paramJson});
            if (response != null && response.getData() != null) {
                String result = response.getData().getResultMap().get("result");
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
            log.error("AcpExecHandler 获取ACP状态失败, sessionId={}", sessionId, e);
        }
        return "UNKNOWN";
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    @Override
    public List<CmdDescription> cmdDescriptions(String robotId, String sessionId) {
        Chatter chatter = chatterFactory.select(robotId);
        if (chatter instanceof RobotChatter
                && TeamRobotProjectionSolution.TEAM_ACP_GROUP.equals(
                ((RobotChatter) chatter).getRobotGroup())) {
            return teamAcpExecSolution.cmdDescriptions((RobotChatter) chatter, sessionId);
        }
        List<CmdDescription> resultList = Lists.newArrayList();
        try {
            String status = getAcpStatus(sessionId);
            boolean isBusy = "BUSY".equals(status);

            if (isBusy) {
                // BUSY状态展示取消命令
                resultList.add(CmdDescription.builder()
                        .cmdName(CMD_ACP_CANCEL)
                        .cmdDesc("取消ACP当前prompt")
                        .executeScript("sendMessageInner('" + CMD_ACP_CANCEL + "')")
                        .build());
            } else if ("READY".equals(status)){
                // 非BUSY状态展示清除会话命令
                resultList.add(CmdDescription.builder()
                        .cmdName(CMD_NEW_SESSION)
                        .cmdDesc("开启新会话")
                        .executeScript("sendMessageInner('" + CMD_NEW_SESSION + "')")
                        .build());
                // 展示记忆整理命令
                resultList.add(CmdDescription.builder()
                        .cmdName(CMD_ACP_DREAM)
                        .cmdDesc("触发记忆整理")
                        .executeScript("sendMessageInner('" + CMD_ACP_DREAM + "')")
                        .build());
                // 展示会话列表命令
                resultList.add(CmdDescription.builder()
                        .cmdName(CMD_LIST_SESSIONS)
                        .cmdDesc("查看历史会话")
                        .executeScript("sendMessageInner('" + CMD_LIST_SESSIONS + "')")
                        .build());
            }
            // 当前robot会话有进行中的流式输出时展示停止按钮
            if (messageSolution.findStreamConnect(robotId, sessionId) != null) {
                resultList.add(CmdDescription.builder()
                        .cmdName(CMD_ACP_FORCE_STOP)
                        .cmdDesc("停止输出")
                        .executeScript("sendMessageInner('" + CMD_ACP_FORCE_STOP + "')")
                        .build());
            }
        } catch (Exception e) {
            log.error("AcpExecHandler cmdDescriptions 获取状态失败, sessionId={}", sessionId, e);
        }
        return resultList;
    }
}
