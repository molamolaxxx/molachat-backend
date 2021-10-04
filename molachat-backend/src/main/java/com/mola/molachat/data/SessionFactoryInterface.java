package com.mola.molachat.data;

import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.Session;
import com.mola.molachat.entity.VideoSession;

import java.util.List;
import java.util.Set;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午4:33
 * @Version 1.0
 * 保存聊天会话文件
 */
public interface SessionFactoryInterface {

    /**
     * 创建一个session
     * @param chatterSet
     * @return
     */
    Session create(Set<Chatter> chatterSet);

    /**
     * 创建一个session
     * @param session
     * @return
     */
    Session create(Session session);

    /**
     * 根据id查找session
     * @param id
     * @return
     */
    Session selectById(String id);

    /**
     * 移除session
     * @param session
     * @return
     */
    Session remove(Session session);

    /**
     * 列出所有session
     * @return
     */
    List<Session> list();

    /**
     * 向会话中插入消息
     * @param sessionId
     * @param message
     * @return
     */
    Message insertMessage(String sessionId, Message message);

    /**
     * 创建video-session
     * @param requestChatterId
     * @param acceptChatterId
     * @return
     */
    VideoSession createVideoSession(String requestChatterId, String acceptChatterId);

    /**
     * 关闭video-session，任意一方关闭都会导致全部关闭
     * @param chatterId
     * @return 需要通知的一方
     */
    String removeVideoSession(String chatterId);

    /**
     * 根据chatterId查找videosession
     * @param chatterId
     * @return
     */
    VideoSession selectVideoSession(String chatterId);

    /**
     * 列出所有video-session
     * @return
     */
    List<VideoSession> listVideoSession();

    /**
     * 进行一次刷盘
     */
    void save(String sessionId);
}
