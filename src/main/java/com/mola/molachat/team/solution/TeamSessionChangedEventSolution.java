package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.dto.TeamEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TeamSessionChangedEventSolution {

    private final Map<String, String> currentSessionIds = new ConcurrentHashMap<>();

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private MessageSolution messageSolution;

    public void handle(TeamEventDTO event) {
        if (event == null || StringUtils.isBlank(event.getTeamId())
                || StringUtils.isBlank(event.getTeamMemberId())) {
            return;
        }
        JSONObject data;
        try {
            data = JSON.parseObject(event.getData());
        } catch (RuntimeException e) {
            log.warn("忽略数据无效的Fast Team会话切换事件, eventId={}",
                    event.getEventId(), e);
            return;
        }
        if (data == null || !"AUTO_IDLE".equals(data.getString("reason"))
                || StringUtils.isBlank(data.getString("newSessionId"))) {
            return;
        }

        String robotId = StringUtils.defaultIfBlank(
                event.getAcpClientId(), "team-acp-" + event.getTeamMemberId());
        Chatter chatter = chatterFactory.select(robotId);
        if (!(chatter instanceof RobotChatter)) {
            log.warn("Fast Team会话切换找不到机器人投影, robotId={}, eventId={}",
                    robotId, event.getEventId());
            return;
        }
        RobotChatter robot = (RobotChatter) chatter;
        if (!TeamRobotProjectionSolution.TEAM_ACP_GROUP.equals(robot.getRobotGroup())
                || !event.getTeamId().equals(robot.getTeamId())
                || !event.getTeamMemberId().equals(robot.getTeamMemberId())
                || robot.getVisibleChatterIds() == null
                || robot.getVisibleChatterIds().size() != 1) {
            log.warn("忽略与机器人投影不匹配的Fast Team会话切换事件, robotId={}, eventId={}",
                    robotId, event.getEventId());
            return;
        }

        String projectionKey = event.getTeamId() + "\n" + event.getTeamMemberId();
        String newSessionId = data.getString("newSessionId");
        if (newSessionId.equals(currentSessionIds.put(projectionKey, newSessionId))) {
            return;
        }

        List<String> chatterIds = new ArrayList<>(robot.getVisibleChatterIds());
        chatterIds.add(robotId);
        java.util.Collections.sort(chatterIds);
        String molaSessionId = chatterIds.get(0) + chatterIds.get(1);
        try {
            messageSolution.forceStopStream(robotId, molaSessionId);
            Message message = new Message();
            message.setChatterId(robotId);
            message.setSessionId(molaSessionId);
            message.setContent("已自动开启新会话");
            message.setCreateTime(new Date());
            messageSolution.insertMessage(molaSessionId, message);
        } catch (RuntimeException e) {
            currentSessionIds.remove(projectionKey, newSessionId);
            log.warn("Fast Team自动开启新会话提示发送失败, robotId={}, sessionId={}",
                    robotId, molaSessionId, e);
        }
    }
}
