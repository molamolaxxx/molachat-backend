package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.dto.TeamEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class TeamMessageEventSolution {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private MessageSolution messageSolution;

    public void handle(TeamEventDTO event) {
        String robotId = event.getAcpClientId() == null
                ? "team-acp-" + event.getTeamMemberId() : event.getAcpClientId();
        Chatter chatter = chatterFactory.select(robotId);
        if (!(chatter instanceof RobotChatter)) {
            log.warn("Fast Team消息找不到机器人投影, robotId={}, eventId={}",
                    robotId, event.getEventId());
            return;
        }
        RobotChatter robot = (RobotChatter) chatter;
        String ownerChatterId = robot.getVisibleChatterIds() == null
                || robot.getVisibleChatterIds().size() != 1
                ? null : robot.getVisibleChatterIds().iterator().next();
        if (ownerChatterId == null) {
            log.warn("Fast Team消息无法确定owner, robotId={}, eventId={}",
                    robotId, event.getEventId());
            return;
        }

        JSONObject data = JSON.parseObject(event.getData());
        StreamMessage message = new StreamMessage();
        message.setChatterId(robotId);
        message.setSessionId(computeSessionId(ownerChatterId, robotId));
        message.setCreateTime(new Date());
        message.setOpenViewModal(true);
        if ("MESSAGE_CHUNK".equals(event.getType())) {
            message.setContent(data == null ? "" : data.getString("content"));
            message.setEnd(false);
        } else if ("MESSAGE_COMPLETE".equals(event.getType())) {
            message.setContent("");
            message.setEnd(true);
        } else if ("MESSAGE_ERROR".equals(event.getType())) {
            String content = data == null ? null : data.getString("content");
            message.setContent(content == null
                    ? "====== 发生错误 ======\n"
                    + (data == null ? "未知错误" : data.getString("message"))
                    : content);
            message.setEnd(true);
        } else {
            return;
        }
        try {
            messageSolution.sendStreamMessage(message.getSessionId(), message);
        } catch (RuntimeException e) {
            log.warn("Fast Team流式消息发送失败, eventId={}, sessionId={}",
                    event.getEventId(), message.getSessionId(), e);
        }
    }

    private String computeSessionId(String ownerChatterId, String robotId) {
        List<String> ids = Arrays.asList(ownerChatterId, robotId);
        Collections.sort(ids);
        return ids.get(0) + ids.get(1);
    }
}
