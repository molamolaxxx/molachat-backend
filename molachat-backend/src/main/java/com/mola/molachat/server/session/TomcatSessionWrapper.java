package com.mola.molachat.server.session;

import javax.websocket.EncodeException;
import javax.websocket.Session;
import java.io.IOException;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 13:10
 **/
public class TomcatSessionWrapper implements SessionWrapper{

    private Session session;

    public TomcatSessionWrapper(Session session){
        this.session = session;
    }

    @Override
    public void sendToClient(Object message) throws IOException, EncodeException {
        session.getBasicRemote().sendObject(message);
    }
}
