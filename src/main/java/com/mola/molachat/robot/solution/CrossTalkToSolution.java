package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSONObject;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.robot.constant.CmdProxyConstant;
import kotlin.Unit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 跨chatter talk_to 消息路由网关
 * @date : 2026-05-23
 */
@Service
@Slf4j
public class CrossTalkToSolution implements InitializingBean {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public void afterPropertiesSet() throws Exception {
        registerCrossTalkToCallback();
    }

    private void registerCrossTalkToCallback() {
        CmdSender.INSTANCE.registerCallback(
                CmdProxyConstant.CROSS_TALK_TO,
                CmdProxyConstant.CROSS_TALK_TO,
                (res) -> {
                    try {
                        String targetChatterId = res.getResultMap().get("targetChatterId");
                        String targetRobotName = res.getResultMap().get("targetRobotName");
                        String senderChatterId = res.getResultMap().get("senderChatterId");
                        String senderRobotName = res.getResultMap().get("senderRobotName");
                        String content = res.getResultMap().get("content");
                        int depth = Integer.parseInt(
                                res.getResultMap().getOrDefault("depth", "1"));

                        route(targetChatterId, targetRobotName,
                                senderChatterId, senderRobotName,
                                content, depth);
                    } catch (Exception e) {
                        log.error("crossTalkTo处理失败", e);
                    }
                    return Unit.INSTANCE;
                }
        );
    }

    private void route(String targetChatterId, String targetRobotName,
                       String senderChatterId, String senderRobotName,
                       String content, int depth) {
        // 1. 校验 depth
        if (depth > 5) {
            log.warn("crossTalkTo depth exceeded: sender={}:{}, target={}:{}, depth={}",
                    senderChatterId, senderRobotName, targetChatterId, targetRobotName, depth);
            return;
        }

        // 2. 校验目标 chatter 存在
        Chatter targetChatter = chatterFactory.select(targetChatterId);
        if (targetChatter == null) {
            log.warn("crossTalkTo target chatter not found: {}", targetChatterId);
            return;
        }

        // 3. 校验目标 robot 存在
        String targetRobotId = computeAcpId(targetRobotName);
        Chatter targetRobot = chatterFactory.select(targetRobotId);
        if (targetRobot == null) {
            log.warn("crossTalkTo target robot not found: {} (acpId={})",
                    targetRobotName, targetRobotId);
            return;
        }

        // 4. 计算目标 groupId
        String targetGroupId = computeGroupId(targetChatterId, targetRobotId);

        // 5. 转发到目标 cmd-proxy
        JSONObject deliverParam = new JSONObject();
        deliverParam.put("senderChatterId", senderChatterId);
        deliverParam.put("senderRobotName", senderRobotName);
        deliverParam.put("targetRobotName", targetRobotName);
        deliverParam.put("content", content);
        deliverParam.put("depth", depth);

        CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE.send(
                CmdProxyConstant.CROSS_TALK_TO_DELIVER,
                targetGroupId,
                new String[]{deliverParam.toJSONString()}
        );

        // 6. 记录结果日志
        if (response == null || response.getData() == null) {
            log.warn("crossTalkTo deliver failed, target offline: chatterId={}, groupId={}",
                    targetChatterId, targetGroupId);
        } else {
            log.info("crossTalkTo delivered: {}:{} -> {}:{}",
                    senderChatterId, senderRobotName, targetChatterId, targetRobotName);
        }
    }

    /**
     * 计算 ACP robot 的 chatterId
     * 规则：'acp-' + robotName.replace(' ', '_').replace('\u3000', '_')
     */
    private String computeAcpId(String robotName) {
        return "acp-" + robotName.replace(" ", "_").replace("\u3000", "_");
    }

    /**
     * 计算 groupId（与 cmd-proxy 侧一致）
     * 规则：字典序排序后直接拼接，无分隔符
     */
    private String computeGroupId(String chatterId, String robotId) {
        List<String> ids = Arrays.asList(chatterId, robotId);
        Collections.sort(ids);
        StringBuilder sb = new StringBuilder();
        for (String id : ids) {
            sb.append(id);
        }
        return sb.toString();
    }
}
