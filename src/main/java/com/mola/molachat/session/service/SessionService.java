package com.mola.molachat.session.service;


import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.server.ChatServer;

import java.util.List;
import java.util.Set;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:07
 * @Version 1.0
 */
public interface SessionService {

    /**
     * 创建session
     * @return
     */
    SessionDTO create(Set<ChatterDTO> chatterDTOSet);

    /**
     *
     * @param sessionId
     * @return
     */
    boolean deleteById(String sessionId);

    /**
     * 查询session
     * @param
     * @return
     */
    SessionDTO findOrCreateSession(String chatterId1, String chatterId2);

    /**
     *
     */
    SessionDTO findSession(String sessionId);

    /**
     * 查询公共session
     * @param chatterId
     * @return
     */
    SessionDTO findCommonAndGroupSession(String chatterId, String groupId);

    /**
     * 列出所有session信息
     * @return
     */
    List<SessionDTO> list();

    /**
     * 根据chatterId关闭session
     * @param chatterId
     * @return
     */
    Integer closeSessions(String chatterId);

    /**
     * 保存sessionList
     * @param sessionList
     */
    void save(List<SessionDTO> sessionList);

    /**
     * 发送消息，利用生产消费者模型异步执行
     * @param server
     * @param message
     */
    void sendMessageAsync(ChatServer server, Message message);

    /**
     * 在会话中移除某个用户
     * @param session
     */
    void removeChatterFromSession(Chatter chatter, SessionDTO session);
}
