package com.mola.molachat.team.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.team.dto.TeamEventDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class TeamTalkToEventSolution {

    public void handle(TeamEventDTO event) {
        JSONObject data = JSON.parseObject(event.getData());
        if (data == null || data.getString("messageId") == null
                || data.getString("senderTeamMemberId") == null
                || data.getString("targetTeamMemberId") == null
                || data.getString("delivery") == null) {
            log.warn("忽略字段不完整的Fast Team talkTo事件, teamId={}, type={}",
                    event.getTeamId(), event.getType());
            return;
        }
        if ("TALK_TO_REJECTED".equals(event.getType())) {
            log.warn("Fast Team talkTo未投递, teamId={}, sender={}, target={}, delivery={}, reason={}",
                    event.getTeamId(), data.getString("senderTeamMemberId"),
                    data.getString("targetTeamMemberId"), data.getString("delivery"),
                    data.getString("reason"));
            return;
        }
        log.info("Fast Team talkTo状态, teamId={}, sender={}, target={}, delivery={}",
                event.getTeamId(), data.getString("senderTeamMemberId"),
                data.getString("targetTeamMemberId"), data.getString("delivery"));
    }
}
