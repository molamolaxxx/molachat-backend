package com.mola.molachat.server.session;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 14:33
 **/
@Slf4j
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

    @Override
    public void close() {
        try {
            socketSession.close();
        } catch (IOException e) {
            log.error("close SpringSession exception!", e);
        }
    }
}
