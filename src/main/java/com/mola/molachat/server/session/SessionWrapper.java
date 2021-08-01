package com.mola.molachat.server.session;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 13:07
 **/
public interface SessionWrapper {

    /**
     * 发送消息到客户端
     * @param message
     */
    void sendToClient(Object message) throws Exception;
}
