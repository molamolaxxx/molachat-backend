package com.mola.molachat.robot.solution;

import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.server.websocket.WSResponse;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.dto.TeamMemberSourceDTO;
import com.mola.molachat.team.solution.TeamGatewaySolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 将普通MAIN ACP的内部会话切换投影到MolaChat。
 */
@Service
@Slf4j
public class AcpSessionChangedSolution {

    private static final String SUPPORTED_SCHEMA_VERSION = "1";

    private final Map<String, String> currentSessionIds = new ConcurrentHashMap<>();

    @Resource
    private TeamGatewaySolution teamGatewaySolution;

    @Resource
    private MessageSolution messageSolution;

    @Resource
    private ServerService serverService;

    public void handle(Map<String, String> resultMap) {
        if (resultMap == null
                || !SUPPORTED_SCHEMA_VERSION.equals(resultMap.get("schemaVersion"))) {
            log.warn("忽略无效的ACP会话切换事件: schemaVersion={}",
                    resultMap == null ? null : resultMap.get("schemaVersion"));
            return;
        }
        String instanceId = resultMap.get("instanceId");
        String groupId = resultMap.get("groupId");
        String newSessionId = resultMap.get("newSessionId");
        if (StringUtils.isBlank(instanceId) || StringUtils.isBlank(groupId)
                || StringUtils.isBlank(newSessionId)) {
            log.warn("忽略字段不完整的ACP会话切换事件, instanceId={}, groupId={}",
                    instanceId, groupId);
            return;
        }

        TeamMemberSourceDTO source = teamGatewaySolution.findAcpSource(instanceId, groupId);
        if (source == null || StringUtils.isBlank(source.getOwnerChatterId())
                || StringUtils.isBlank(source.getSourceRobotId())) {
            log.warn("忽略未绑定到当前cmd-proxy实例的ACP会话切换事件, instanceId={}, groupId={}",
                    instanceId, groupId);
            return;
        }

        String projectionKey = instanceId + "\n" + groupId;
        if (newSessionId.equals(currentSessionIds.put(projectionKey, newSessionId))) {
            return;
        }

        messageSolution.forceStopStream(source.getSourceRobotId(), groupId);
        if ("AUTO_IDLE".equals(resultMap.get("reason"))) {
            sendAutomaticSessionMessage(source.getSourceRobotId(), groupId);
        }
        Map<String, String> event = new HashMap<>();
        event.put("groupId", groupId);
        event.put("sourceRobotId", source.getSourceRobotId());
        event.put("newSessionId", newSessionId);
        event.put("reason", resultMap.get("reason"));
        event.put("timestamp", resultMap.get("timestamp"));
        serverService.sendResponse(source.getOwnerChatterId(),
                WSResponse.acpSessionChanged("ACP session changed", event));
        log.info("ACP当前会话投影已更新, instanceId={}, groupId={}, newSessionId={}, reason={}",
                instanceId, groupId, newSessionId, resultMap.get("reason"));
    }

    private void sendAutomaticSessionMessage(String sourceRobotId, String groupId) {
        Message message = new Message();
        message.setChatterId(sourceRobotId);
        message.setSessionId(groupId);
        message.setContent("已自动开启新会话");
        message.setCreateTime(new Date());
        messageSolution.insertMessage(groupId, message);
    }

    String currentSessionId(String instanceId, String groupId) {
        return currentSessionIds.get(instanceId + "\n" + groupId);
    }
}
