package com.mola.molachat.robot.handler.impl.acp;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.model.CmdDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private static final String CMD_ACP_CLEAR = "#acp-clear#";

    @Override
    public BaseAction handler(MessageReceiveEvent messageReceiveEvent) {
        String sessionId = messageReceiveEvent.getSessionId();
        String userMessage = messageReceiveEvent.getMessage().getContent();

        // 处理取消prompt命令
        if (CMD_ACP_CANCEL.equals(userMessage)) {
            return invokeAcpCmd("acpCancelPrompt", sessionId);
        }

        // 处理清除会话命令
        if (CMD_ACP_CLEAR.equals(userMessage)) {
            return invokeAcpCmd("acpClearContext", sessionId);
        }

        // 默认：向ACP发送消息，先检查状态
        String status = getAcpStatus(sessionId);
        if (!"READY".equals(status)) {
            return MessageSendAction.withResp("ACP当前状态为 " + status + "，无法发送消息，请等待就绪后再试");
        }

        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            paramMap.put("message", userMessage);
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
                        .cmdName(CMD_ACP_CLEAR)
                        .cmdDesc("清除ACP上下文")
                        .executeScript("sendMessageInner('" + CMD_ACP_CLEAR + "')")
                        .build());
            }
        } catch (Exception e) {
            log.error("AcpExecHandler cmdDescriptions 获取状态失败, sessionId={}", sessionId, e);
        }
        return resultList;
    }
}
