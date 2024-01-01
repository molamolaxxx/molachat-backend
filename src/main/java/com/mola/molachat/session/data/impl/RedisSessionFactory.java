package com.mola.molachat.session.data.impl;

import com.mola.molachat.common.condition.RedisExistCondition;
import com.mola.molachat.common.config.AppConfig;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.VideoSession;
import com.mola.molachat.common.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-06-14 01:30
 **/
@Component
@Conditional(RedisExistCondition.class)
@Slf4j
public class RedisSessionFactory extends SessionFactory{

    @Resource
    private SelfConfig config;

    @Resource
    private RedisUtil redisUtil;

    @Resource
    private AppConfig appConfig;

    private static final String SESSION_NAMESPACE = "session:";

    /**
     * redis-key前缀
     */
    private String redisKeyPrefix;

    @PostConstruct
    public void postConstruct(){
        redisKeyPrefix = appConfig.getId() + ":" + SESSION_NAMESPACE;
        // 从redis中读取list放入缓存
        Set keys = redisUtil.keys(redisKeyPrefix);
        for (Object key : keys) {
            Session session = (Session) redisUtil.get((String) key);
            super.create(session);
        }
    }

    @Override
    public Session create(Set<Chatter> chatterSet) {
        Session session = super.create(chatterSet);
        redisUtil.set(redisKeyPrefix + session.getSessionId(), session);
        return session;
    }

    @Override
    public Session create(Session session) {
        session = super.create(session);
        redisUtil.set(redisKeyPrefix + session.getSessionId(), session);
        return session;
    }

    @Override
    public Session selectById(String id) {
        // 取一级缓存
        Session firstCache = super.selectById(id);
        if (null == firstCache) {
            // 取二级缓存
            Object secondCache = redisUtil.get(redisKeyPrefix + id);
            if (null != secondCache) {
                super.create((Session) secondCache);
            }
        }
        return super.selectById(id);
    }

    @Override
    public Session remove(Session session) {
        session = super.remove(session);
        redisUtil.del(redisKeyPrefix + session.getSessionId());
        return session;
    }

    @Override
    public List<Session> list() {
        return super.list();
    }

    @Override
    public Message insertMessage(String sessionId, Message message) {
        message = super.insertMessage(sessionId, message);
        // todo 同步刷盘，可能有性能问题
        redisUtil.set(redisKeyPrefix + sessionId ,super.selectById(sessionId));
        return message;
    }

    @Override
    public VideoSession createVideoSession(String requestChatterId, String acceptChatterId) {
        return super.createVideoSession(requestChatterId, acceptChatterId);
    }

    @Override
    public void save(String sessionId) {
        redisUtil.set(redisKeyPrefix + sessionId ,super.selectById(sessionId));
    }

    @Override
    public String removeVideoSession(String chatterId) {
        return super.removeVideoSession(chatterId);
    }

    @Override
    public VideoSession selectVideoSession(String chatterId) {
        return super.selectVideoSession(chatterId);
    }

    @Override
    public List<VideoSession> listVideoSession() {
        return super.listVideoSession();
    }
}
