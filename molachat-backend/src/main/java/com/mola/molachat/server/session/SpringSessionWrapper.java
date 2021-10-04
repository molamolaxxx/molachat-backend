package com.mola.molachat.server.session;

import com.alibaba.fastjson.JSON;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 14:33
 **/
public class SpringSessionWrapper implements SessionWrapper{

    private WebSocketSession socketSession;

    public SpringSessionWrapper(WebSocketSession socketSession) {
        this.socketSession = socketSession;
    }

    @Override
    public void sendToClient(Object message) throws Exception {
        TextMessage txt = new TextMessage(JSON.toJSONString(message));
        socketSession.sendMessage(txt);
    }
}
