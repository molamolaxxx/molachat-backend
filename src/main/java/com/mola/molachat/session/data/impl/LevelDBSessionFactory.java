package com.mola.molachat.session.data.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.common.condition.LevelDBCondition;
import com.mola.molachat.common.LevelDBClient;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.VideoSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-06-14 01:30
 **/
@Component
@Conditional(LevelDBCondition.class)
@Slf4j
public class LevelDBSessionFactory extends SessionFactory{

    private static final String SESSION_NAMESPACE = "session:";

    /**
     * redis-key前缀
     */
    private String levelDBKeyPrefix;

    @Resource
    private LevelDBClient levelDBClient;

    @PostConstruct
    public void postConstruct(){
        levelDBKeyPrefix = SESSION_NAMESPACE;
        // 从levelDB中读取list放入缓存
        Map<String, String> map = levelDBClient.list(levelDBKeyPrefix);
        map.forEach((k, v) -> {
            super.create(parseFromJson(v));
        });
    }

    private Session parseFromJson(String json) {
        Session parseObject = JSONObject.parseObject(json, Session.class);
        if (parseObject == null) {
            return null;
        }
        Session session = fillSessionInner(parseObject);
        JSONObject jsonObject = JSONObject.parseObject(json);
        JSONArray messageList = jsonObject.getJSONArray("messageList");
        if (CollectionUtils.isEmpty(messageList)) {
            return session;
        }
        session.getMessageList().clear();
        for (Object o : messageList) {
            Message message = ((JSONObject) o).toJavaObject(Message.class);
            if (null == message.getContent()) {
                FileMessage fileMessage = ((JSONObject) o).toJavaObject(FileMessage.class);
                if (fileMessage.getUrl() != null) {
                    message = fileMessage;
                }
            }
            session.getMessageList().add(message);
        }
        return session;
    }

    @Override
    public Session create(Set<Chatter> chatterSet) {
        Session session = super.create(chatterSet);
        levelDBClient.put(levelDBKeyPrefix + session.getSessionId(), JSONObject.toJSONString(session));
        return session;
    }

    @Override
    public Session create(Session session) {
        session = super.create(session);
        levelDBClient.put(levelDBKeyPrefix + session.getSessionId(), JSONObject.toJSONString(session));
        return session;
    }

    @Override
    public Session selectById(String id) {
        // 取一级缓存
        Session firstCache = super.selectById(id);
        if (null == firstCache) {
            // 取二级缓存
            Session session = parseFromJson(levelDBClient.get(levelDBKeyPrefix + id));
            if (null != session) {
                super.create(session);
            }
        }
        return super.selectById(id);
    }

    @Override
    public Session remove(Session session) {
        session = super.remove(session);
        levelDBClient.delete(levelDBKeyPrefix + session.getSessionId());
        return session;
    }

    @Override
    public List<Session> list() {
        return super.list();
    }

    @Override
    public Message insertMessage(String sessionId, Message message) {
        message = super.insertMessage(sessionId, message);
        levelDBClient.put(levelDBKeyPrefix + sessionId, JSONObject.toJSONString(super.selectById(sessionId)));
        return message;
    }

    @Override
    public VideoSession createVideoSession(String requestChatterId, String acceptChatterId) {
        return super.createVideoSession(requestChatterId, acceptChatterId);
    }

    @Override
    public void save(String sessionId) {
        levelDBClient.put(levelDBKeyPrefix + sessionId, JSONObject.toJSONString(super.selectById(sessionId)));
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
