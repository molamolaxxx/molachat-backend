package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.action.EmptyAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-08-27 19:42
 **/
@Component
@Slf4j
public class CmdProxyRobotHandler implements IRobotEventHandler<MessageReceiveEvent, BaseAction> {

    @Override
    public BaseAction handler(MessageReceiveEvent baseEvent) {
        MessageSendAction messageSendAction = new MessageSendAction();
        try {
            Message message = baseEvent.getMessage();
            String content = message.getContent();
            String[] splitRes = StringUtils.split(content, " ");
            if (null == splitRes || splitRes.length < 1) {
                messageSendAction.setSkip(Boolean.TRUE);
                return messageSendAction;
            }

            String[] args = Arrays.copyOfRange(splitRes, 1, splitRes.length);
            CmdInvokeResponse<CmdResponseContent> response = CmdSender.INSTANCE
                    .send(splitRes[0], message.getSessionId(), args);
            Assert.notNull(response, "response is null");
            if (!response.isSuccess()) {
                log.error("RemoteRobotChatHandler not success " + JSONObject.toJSONString(baseEvent) + ";" + response.getMsg());
                EmptyAction emptyAction = new EmptyAction();
                emptyAction.setSkip(true);
                return emptyAction;
            }
            Assert.notNull(response.getData(), "data is null");
            Assert.notNull(response.getData().getResultMap(), "resultMap is null");

            String result = response.getData().getResultMap().get("result");
            if (StringUtils.isBlank(result)) {
                EmptyAction emptyAction = new EmptyAction();
                emptyAction.setFinalExec(Boolean.TRUE);
                return emptyAction;
            }

            messageSendAction.setResponsesText(result);
            messageSendAction.setFinalExec(Boolean.TRUE);
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler error " + JSONObject.toJSONString(baseEvent), e);
            messageSendAction.setResponsesText("代理命令执行失败，" + e.getMessage());
            messageSendAction.setSkip(Boolean.TRUE);
        }
        return messageSendAction;
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }

    @Override
    public Integer order() {
        return 0;
    }
}
