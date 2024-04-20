package com.mola.molachat.server.session;

import lombok.extern.slf4j.Slf4j;

import javax.websocket.EncodeException;
import javax.websocket.Session;
import java.io.IOException;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 13:10
 **/
@Slf4j
public class TomcatSessionWrapper implements SessionWrapper{

    private Session session;

    public TomcatSessionWrapper(Session session){
        this.session = session;
    }

    @Override
    public void sendToClient(Object message) throws IOException, EncodeException {
        session.getBasicRemote().sendObject(message);
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (IOException e) {
            log.error("close TomcatSession exception!", e);
        }
    }
}
