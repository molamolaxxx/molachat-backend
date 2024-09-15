package com.mola.molachat.server.data;

import com.mola.molachat.server.ChatServer;

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
    ChatServer selectOne(String chatterId, String deviceId);


    /**
     * 根据chatterId查询server
     * @param chatterId
     * @return
     */
    List<ChatServer> selectByChatterId(String chatterId);

    /**
     * 列出server
     * @return
     */
    List<ChatServer> list();
}
