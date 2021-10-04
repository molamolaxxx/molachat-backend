package com.mola.molachat.handler.action;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.annotation.Handler;
import com.mola.molachat.common.websocket.Action;
import com.mola.molachat.common.websocket.ActionCode;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.entity.Message;
import com.mola.molachat.exception.service.SessionServiceException;
import com.mola.molachat.service.SessionService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-17 00:49
 **/
@Handler
@Slf4j
public class SendMessageHandler implements WSRequestActionHandler{

    @Resource
    private SessionService sessionService;

    @Override
    public Integer actonCode() {
        return ActionCode.SEND_MESSAGE;
    }

    @Override
    public void handle(Action action) throws Exception {
        // log.info("action:客户端发送消息");
        //发送消息
        //1.解析json 发送者id sessionId
        JSONObject data = (JSONObject) action.getData();
        log.info(data.toJSONString());
        String chatterId = data.getString("chatterId");
        String content = data.getString("content");

        //2.构建message
        Message message = new Message();
        message.setContent(content);
        message.setChatterId(chatterId);
        // 判断session是否是群聊session
        if ("common-session".equals(data.getString("sessionId"))) {
            message.setCommon(true);
        }

        //3.调用session
        try {
            sessionService.insertMessage(data.getString("sessionId"), message);
        } catch (SessionServiceException e) {
            //发送异常信息
            log.error("插入信息异常", e);
            action.getSessionWrapper().sendToClient(WSResponse.exception("session-invalid", "当前会话已经失效"));
        }
    }
}
