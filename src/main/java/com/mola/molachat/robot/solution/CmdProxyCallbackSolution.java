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
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-08-27 00:01
 **/
@Service
@Slf4j
public class CmdProxyCallbackSolution implements InitializingBean {

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
    private SessionFactoryInterface sessionFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        // agent
        registerAcpCallback();
        // acp机器人同步
        registerAcpSyncRobotsCallback();
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
            try {
                String robotsJson = res.getResultMap().get("robots");
                String visibleChatterIdsJson = res.getResultMap().get("visibleChatterIds");
                List<AcpRobotSyncSolution.AcpRobotParam> robots = JSONArray.parseArray(
                        robotsJson, AcpRobotSyncSolution.AcpRobotParam.class);
                Set<String> visibleChatterIds = new HashSet<>(
                        JSON.parseArray(visibleChatterIdsJson, String.class));
                acpRobotSyncSolution.sync(robots, visibleChatterIds);
            } catch (Exception e) {
                log.error("acpSyncRobots回调执行失败", e);
            }
            return Unit.INSTANCE;
        });
    }
}
