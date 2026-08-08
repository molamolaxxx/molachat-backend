package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.molachat.common.config.AppConfig;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.robot.constant.CmdProxyConstant;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.solution.MessageSolution;
import com.mola.molachat.team.solution.TeamGatewaySolution;
import com.mola.molachat.team.solution.TeamCommandTransport;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.TaskScheduler;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-08-27 00:01
 **/
@Service
@Slf4j
public class CmdProxyCallbackSolution implements InitializingBean {

    private static final int ACP_SYNC_FAST_ATTEMPTS = 6;
    private static final long ACP_SYNC_RETRY_INTERVAL_MILLIS = 30_000L;

    private final AtomicBoolean acpSyncRecoveryActive = new AtomicBoolean();

    @Resource
    private RobotSolution robotSolution;

    @Resource
    private ChatGptSolution chatGptSolution;

    @Resource
    private AppConfig appConfig;

    @Resource
    private MessageSolution messageSolution;

    @Resource
    private AcpRobotSyncSolution acpRobotSyncSolution;

    @Resource
    private AcpSessionChangedSolution acpSessionChangedSolution;

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private TeamGatewaySolution teamGatewaySolution;

    @Resource
    private TeamCommandTransport teamCommandTransport;

    @Resource
    private TaskScheduler taskScheduler;

    @Override
    public void afterPropertiesSet() throws Exception {
        // agent
        registerAcpCallback();
        // acp机器人同步
        registerAcpSyncRobotsCallback();
        registerAcpSessionChangedCallback();
        ensureAcpSyncRecovery();
    }

    public void registerAcpSessionChangedCallback() {
        CmdSender.INSTANCE.registerCallback(
                CmdProxyConstant.ACP_SESSION_CHANGED, CmdProxyConstant.ACP,
                res -> {
                    acpSessionChangedSolution.handle(res.getResultMap());
                    return Unit.INSTANCE;
                });
    }

    public void registerAcpCallback() {
        CmdSender.INSTANCE.registerCallback(CmdProxyConstant.ACP, CmdProxyConstant.ACP, (res) -> {
            String groupId = res.getResultMap().get("groupId");
            String content = res.getResultMap().get("content");
            boolean end = Objects.equals(res.getResultMap().get("end"), "Y");
            sendStreamMessage(content, groupId, end);
            return Unit.INSTANCE;
        });
    }

    private void sendStreamMessage(String content, String groupId, boolean end) {
        // 发送流式消息
        StreamMessage msg = new StreamMessage();
        msg.setContent(content);
        msg.setOpenViewModal(true);
        // 从session中获取id以acp开头的chatter
        String chatterId = CmdProxyConstant.ACP;
        Session session = sessionFactory.selectById(groupId);
        if (session != null && session.getChatterSet() != null) {
            chatterId = session.getChatterSet().stream()
                    .filter(c -> c.getId() != null && c.getId().startsWith("acp"))
                    .map(Chatter::getId)
                    .findFirst()
                    .orElse(CmdProxyConstant.ACP);
        }
        msg.setChatterId(chatterId);
        msg.setSessionId(groupId);
        msg.setCreateTime(new Date());
        msg.setEnd(end);
        messageSolution.sendStreamMessage(groupId, msg);
    }

    public void registerAcpSyncRobotsCallback() {
        CmdSender.INSTANCE.registerCallback("acpSyncRobots", "acpSyncRobots", (res) -> {
            handleAcpSyncRobots(res.getResultMap());
            return Unit.INSTANCE;
        });
    }

    void handleAcpSyncRobots(Map<String, String> resultMap) {
        try {
            teamGatewaySolution.updateDiscovery(resultMap);
            String robotsJson = resultMap.get("robots");
            String visibleChatterIdsJson = resultMap.get("visibleChatterIds");
            List<AcpRobotSyncSolution.AcpRobotParam> robots = JSONArray.parseArray(
                    robotsJson, AcpRobotSyncSolution.AcpRobotParam.class);
            Set<String> visibleChatterIds = new HashSet<>(
                    JSON.parseArray(visibleChatterIdsJson, String.class));
            acpRobotSyncSolution.sync(robots, visibleChatterIds);
        } catch (Exception e) {
            log.error("acpSyncRobots响应处理失败", e);
        }
    }

    public void ensureAcpSyncRecovery() {
        if (teamGatewaySolution.isBusinessReady()
                || !acpSyncRecoveryActive.compareAndSet(false, true)) {
            return;
        }
        scheduleAcpSyncHandshake(0);
    }

    private void scheduleAcpSyncHandshake(int attempt) {
        try {
            taskScheduler.schedule(() -> requestAcpSyncRobots(attempt),
                    new Date(System.currentTimeMillis() + retryDelayMillis(attempt)));
        } catch (RuntimeException e) {
            acpSyncRecoveryActive.set(false);
            log.warn("acpSyncRobots恢复任务调度失败", e);
        }
    }

    private void requestAcpSyncRobots(int attempt) {
        try {
            Map<String, String> resultMap = teamCommandTransport.sendWithoutArgs(
                    "acpSyncRobots", "acpSyncRobots");
            if (resultMap != null && !resultMap.isEmpty()) {
                handleAcpSyncRobots(resultMap);
            }
            if (teamGatewaySolution.isBusinessReady()) {
                acpSyncRecoveryActive.set(false);
                log.info("acpSyncRobots主动握手成功, attempt={}", attempt + 1);
                return;
            }
        } catch (RuntimeException e) {
            if (attempt < ACP_SYNC_FAST_ATTEMPTS) {
                log.warn("acpSyncRobots主动握手失败, attempt={}", attempt + 1, e);
            } else {
                log.warn("acpSyncRobots持续恢复失败, attempt={}, retryInMillis={}, error={}",
                        attempt + 1, ACP_SYNC_RETRY_INTERVAL_MILLIS, e.getMessage());
            }
        }
        scheduleAcpSyncHandshake(attempt + 1);
    }

    static long retryDelayMillis(int attempt) {
        if (attempt <= 0) {
            return 0;
        }
        if (attempt < ACP_SYNC_FAST_ATTEMPTS) {
            return 1000L << (attempt - 1);
        }
        return ACP_SYNC_RETRY_INTERVAL_MILLIS;
    }
}
