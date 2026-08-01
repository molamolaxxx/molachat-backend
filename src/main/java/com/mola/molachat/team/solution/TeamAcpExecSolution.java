package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.session.solution.MessageSolution;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TeamAcpExecSolution {

    private static final String CMD_CANCEL = "#acp-cancel#";
    private static final String CMD_STOP_STREAM = "#stop-stream#";
    private static final String CMD_NEW_SESSION = "#new-session#";
    private static final String CMD_MEMORY_DREAM = "#acp-dream#";
    private static final String CMD_LIST_SESSIONS = "#list-sessions#";
    private static final String CMD_RESTORE_SESSION = "#restore-session#";
    private static final String FILE_DOWNLOAD_BASE_URL = "https://106.54.193.10:8550/chat/files/";

    @Resource
    private TeamGatewaySolution teamGatewaySolution;

    @Resource
    private SessionService sessionService;

    @Resource
    private MessageSolution messageSolution;

    public BaseAction handle(MessageReceiveEvent event) {
        String ownerChatterId = event.getMessage().getChatterId();
        String content = event.getMessage().getContent();
        String teamId = event.getRobotChatter().getTeamId();
        String teamMemberId = event.getRobotChatter().getTeamMemberId();
        String acpClientId = event.getRobotChatter().getId();
        try {
            if (CMD_CANCEL.equals(content)) {
                teamGatewaySolution.cancel(
                        ownerChatterId, teamId, teamMemberId, acpClientId);
                return MessageSendAction.withResp("已发送取消指令");
            }
            if (CMD_STOP_STREAM.equals(content)) {
                teamGatewaySolution.cancel(
                        ownerChatterId, teamId, teamMemberId, acpClientId);
                messageSolution.forceStopStream(acpClientId, event.getSessionId());
                return MessageSendAction.skip();
            }
            if (CMD_NEW_SESSION.equals(content)) {
                teamGatewaySolution.newSession(
                        ownerChatterId, teamId, teamMemberId, acpClientId);
                return MessageSendAction.withResp("已开启新会话");
            }
            if (CMD_MEMORY_DREAM.equals(content)) {
                teamGatewaySolution.memoryDream(
                        ownerChatterId, teamId, teamMemberId, acpClientId);
                return MessageSendAction.withResp("记忆整理已触发，将在后台执行");
            }
            if (CMD_LIST_SESSIONS.equals(content)) {
                return listSessions(event, teamGatewaySolution.listSessions(
                        ownerChatterId, teamId, teamMemberId, acpClientId, 20));
            }
            if (content.startsWith(CMD_RESTORE_SESSION)) {
                String sessionId = content.substring(CMD_RESTORE_SESSION.length()).trim();
                teamGatewaySolution.restoreSession(
                        ownerChatterId, teamId, teamMemberId, acpClientId, sessionId);
                return MessageSendAction.skip();
            }
            teamGatewaySolution.send(ownerChatterId, teamId, teamMemberId, acpClientId,
                    content, collectRecentFileUrls(event));
            return MessageSendAction.skip();
        } catch (TeamCommandException e) {
            return MessageSendAction.withResp("Fast Team请求失败: " + e.getMessage());
        }
    }

    public List<CmdDescription> cmdDescriptions(RobotChatter robot, String sessionId) {
        List<CmdDescription> result = new ArrayList<>();
        String ownerChatterId = robot.getVisibleChatterIds() == null
                || robot.getVisibleChatterIds().size() != 1
                ? null : robot.getVisibleChatterIds().iterator().next();
        if (ownerChatterId == null) {
            return result;
        }
        try {
            JSONObject statusData = teamGatewaySolution.getStatus(
                    ownerChatterId, robot.getTeamId(), robot.getTeamMemberId(), robot.getId());
            String state = statusData.getString("clientState");
            if (state == null) {
                state = statusData.getString("state");
            }
            if ("BUSY".equals(state)) {
                result.add(command(CMD_CANCEL, "取消ACP当前prompt"));
            } else if ("READY".equals(state)) {
                result.add(command(CMD_NEW_SESSION, "开启新会话"));
                if (teamGatewaySolution.supportsCommand(
                        ownerChatterId, "acpTeamMemoryDream")) {
                    result.add(command(CMD_MEMORY_DREAM, "触发记忆整理"));
                }
                result.add(command(CMD_LIST_SESSIONS, "查看历史会话"));
            }
            if (messageSolution.findStreamConnect(robot.getId(), sessionId) != null) {
                result.add(command(CMD_STOP_STREAM, "停止输出"));
            }
        } catch (TeamCommandException ignored) {
            // transport恢复期间保持命令面板为空，由下一次打开重新查询。
        }
        return result;
    }

    public Double fetchContextUsage(RobotChatter robot) {
        String ownerChatterId = robot.getVisibleChatterIds() == null
                || robot.getVisibleChatterIds().size() != 1
                ? null : robot.getVisibleChatterIds().iterator().next();
        if (ownerChatterId == null) {
            return null;
        }
        JSONObject data = teamGatewaySolution.getContextUsage(
                ownerChatterId, robot.getTeamId(), robot.getTeamMemberId(), robot.getId());
        Double percentage = data.getDouble("contextUsagePercentage");
        return percentage != null && percentage >= 0 ? percentage : null;
    }

    private CmdDescription command(String name, String description) {
        return CmdDescription.builder()
                .cmdName(name)
                .cmdDesc(description)
                .executeScript("sendMessageInner('" + name + "')")
                .build();
    }

    private BaseAction listSessions(MessageReceiveEvent event, JSONObject data) {
        List<JSONObject> sessions = data == null || data.getJSONArray("sessions") == null
                ? new ArrayList<>()
                : data.getJSONArray("sessions").toJavaList(JSONObject.class);
        if (sessions.isEmpty()) {
            return MessageSendAction.withResp("暂无历史会话");
        }
        StringBuilder table = new StringBuilder();
        table.append("| 会话预览 | 最后修改 | 操作 |\n")
                .append("| ----- | ----- | ---- |\n");
        for (JSONObject session : sessions) {
            String sessionId = session.getString("sessionId");
            String button = session.getBooleanValue("current")
                    ? "<button class=\"blue-ring-button\" style=\"opacity:0.5\" "
                    + "onClick=\"showToast('当前正在使用的会话',1000)\">恢复</button>"
                    : String.format("<button class=\"blue-ring-button\" "
                    + "onClick=\"sendMessageInner('%s %s');"
                    + "$('#message-view-modal').modal('close')\">恢复</button>",
                    CMD_RESTORE_SESSION, sessionId);
            table.append(String.format("| %s | %s | %s |\n",
                    session.getString("preview"), session.getString("lastModified"), button));
        }
        StreamMessage message = new StreamMessage();
        message.setContent(table.toString());
        message.setChatterId(event.getRobotChatter().getId());
        message.setSessionId(event.getSessionId());
        message.setCreateTime(new Date());
        message.setOpenViewModal(true);
        message.setEnd(true);
        messageSolution.sendStreamMessage(event.getSessionId(), message);
        return MessageSendAction.skip();
    }

    private List<Map<String, String>> collectRecentFileUrls(MessageReceiveEvent event) {
        List<Map<String, String>> files = new ArrayList<>();
        SessionDTO session = sessionService.findSession(event.getSessionId());
        if (session == null || session.getMessageList() == null
                || session.getMessageList().size() <= 1) {
            return files;
        }
        List<Message> messages = session.getMessageList();
        List<FileMessage> consecutiveFiles = new ArrayList<>();
        for (int index = messages.size() - 2; index >= 0; index--) {
            Message message = messages.get(index);
            if (!event.getMessage().getChatterId().equals(message.getChatterId())
                    || !(message instanceof FileMessage)) {
                break;
            }
            consecutiveFiles.add((FileMessage) message);
        }
        for (int index = consecutiveFiles.size() - 1; index >= 0; index--) {
            FileMessage file = consecutiveFiles.get(index);
            String storedName = file.fetchRealStoredFileName(false);
            if (storedName == null) {
                continue;
            }
            Map<String, String> item = new HashMap<>();
            item.put(file.getFileName(), FILE_DOWNLOAD_BASE_URL + storedName);
            files.add(item);
        }
        return files;
    }
}
