package com.mola.molachat.data;

import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.session.SessionWrapper;

import java.util.List;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:20
 * @Version 1.0
 */
public interface ServerFactoryInterface {

    /**
     * 创建server
     * @param server
     * @return
     */
    ChatServer create(ChatServer server);

    /**
     * 移除server
     * @param server
     * @return
     */
    ChatServer remove(ChatServer server);

    /**
     * 根据chatterId查询server
     * @param chatterId
     * @return
     */
    ChatServer selectOne(String chatterId);

    /**
     * 获取对应chatterId的websocket session
     * @param chatterId
     * @return
     */
    SessionWrapper selectWSSessionByChatterId(String chatterId);

    /**
     * 列出server
     * @return
     */
    List<ChatServer> list();
}
