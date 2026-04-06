package com.mola.molachat.robot.handler.impl.acp;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.FileUtils;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
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

    @Resource
    private SessionService sessionService;

    @Resource
    private SelfConfig selfConfig;

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
            // 收集当前消息之前连续的图片文件消息的base64
            List<String> images = collectRecentImageBase64(sessionId, messageReceiveEvent);

            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("groupId", sessionId);
            paramMap.put("message", userMessage);
            if (!CollectionUtils.isEmpty(images)) {
                paramMap.put("images", images);
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
     * 从当前会话中收集当前消息之前、连续的、当前用户发送的图片文件消息，读取base64
     */
    private List<String> collectRecentImageBase64(String sessionId, MessageReceiveEvent event) {
        List<String> images = new ArrayList<>();
        try {
            SessionDTO session = sessionService.findSession(sessionId);
            if (session == null) {
                return images;
            }
            List<Message> messageList = session.getMessageList();
            if (CollectionUtils.isEmpty(messageList) || messageList.size() <= 1) {
                return images;
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
                if (!FileUtils.isImage(fm.getFileName())) {
                    continue;
                }
                String filePath = resolveFilePath(fm);
                if (filePath == null) {
                    continue;
                }
                File file = new File(filePath);
                if (!file.exists()) {
                    continue;
                }
                byte[] imgData = FileUtils.readFileByBytes(filePath);
                String base64 = Base64Util.encode(imgData);
                images.add(base64);
            }
        } catch (Exception e) {
            log.error("AcpExecHandler collectRecentImageBase64 异常, sessionId={}", sessionId, e);
        }
        return images;
    }

    /**
     * 根据FileMessage的url解析实际磁盘路径
     */
    private String resolveFilePath(FileMessage fm) {
        String url = fm.getUrl();
        if (url == null) {
            return null;
        }
        // files/xxx -> uploadFilePath/xxx (使用fetchRealStoredFileName)
        String storedName = fm.fetchRealStoredFileName(false);
        if (storedName == null) {
            return null;
        }
        return selfConfig.getUploadFilePath() + File.separator + storedName;
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
