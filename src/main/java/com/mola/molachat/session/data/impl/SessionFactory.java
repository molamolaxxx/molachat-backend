package com.mola.molachat.session.data.impl;

import com.mola.molachat.chatter.data.impl.ChatterFactory;
import com.mola.molachat.common.condition.CacheCondition;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.VideoSession;
import com.mola.molachat.common.enums.DataErrorCodeEnum;
import com.mola.molachat.session.enums.VideoStateEnum;
import com.mola.molachat.common.exception.SessionException;
import com.mola.molachat.common.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午4:56
 * @Version 1.0
 * 管理session的工厂
 */
@Component
@Conditional(CacheCondition.class)
@Slf4j
public class SessionFactory implements SessionFactoryInterface {

    @Autowired
    private ChatterFactory chatterFactory;

    @Autowired
    private SelfConfig config;

    /**
     * sessionMap sessionId -> entity
     */
    private static Map<String, Session> sessionMap;

    /**
     * sessionMap chatterId -> videoSession
     */
    private static Map<String, VideoSession> videoSessionMap;

    public SessionFactory(){
        sessionMap = new ConcurrentHashMap<>();
        videoSessionMap = new ConcurrentHashMap<>();
    }

    @Override
    public synchronized Session create(Set<Chatter> chatterSet) throws SessionException{
        //1.创建session
        Session session = createSessionInner(chatterSet);

        //2.存储
        this.create(session);

        return session;
    }

    protected Session createSessionInner(Set<Chatter> chatterSet) {
        if (chatterSet.size() < 2){
            log.info("集合中小于两个chatter，无法创建session");
            throw new SessionException(DataErrorCodeEnum.CREATE_SESSION_ERROR
                    , "集合中小于两个chatter，无法创建session");
        }

        //sessionID为聊天室包含所有聊天者的id，第一个为创建者
        StringBuffer sessionId = new StringBuffer();
        for (Chatter chatter : chatterSet){
            sessionId.append(chatter.getId());
        }

        //1.创建session
        Session session = new Session();
        session.setChatterSet(chatterSet);
        session.setSessionId(sessionId.toString());
        return session;
    }


    protected Session fillSessionInner(Session session) {
        Assert.notNull(session, "session is null!");
        if (StringUtils.isEmpty(session.getSessionId())) {
            session.setSessionId(IdUtils.getSessionId());
        }
        session.setCreateTime(new Date());
        if (session.getMessageList() == null) {
            session.setMessageList(new ArrayList<>());
        }
        return session;
    }


    @Override
    public Session create(Session session) {
        fillSessionInner(session);
        sessionMap.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public Session selectById(String id) {
        return sessionMap.get(id);
    }


    @Override
    public Session remove(Session session) throws SessionException{
        session = selectById(session.getSessionId());
        if (!sessionMap.containsKey(session.getSessionId())){
            throw new SessionException(DataErrorCodeEnum.REMOVE_SESSION_ERROR);
        }
        sessionMap.remove(session.getSessionId());
        session.setRemoved(true);

        return session;
    }

    @Override
    public List<Session> list() {

        List<Session> sessionList = new ArrayList<>();

        for(String key : sessionMap.keySet()){
            sessionList.add(sessionMap.get(key));
        }

        return sessionList;
    }

    @Override
    public Message insertMessage(String sessionId, Message message) throws SessionException {
        Session session = sessionMap.get(sessionId);
        // 激活session
        session.setRemoved(false);
        return insertMessageInner(session, message);
    }

    protected Message insertMessageInner(Session session, Message message) {
        if (null == session) {
            throw new SessionException(DataErrorCodeEnum.SESSION_NOT_EXIST);
        }
        List<Message> messageList = session.getMessageList();
        message.setId(IdUtils.getMessageId());
        message.setCreateTime(new Date());
        //针对每一个messageList同步
        synchronized (messageList) {
            if (messageList.size() >= config.getMAX_SESSION_MESSAGE_NUM()) {
                messageList.remove(0);
            }
            messageList.add(message);
        }

        return message;
    }

    @Override
    public synchronized VideoSession createVideoSession(String requestChatterId, String acceptChatterId) {
        VideoSession videoSession = new VideoSession();
        videoSession.setRequest(chatterFactory.select(requestChatterId));
        videoSession.setAccept(chatterFactory.select(acceptChatterId));
        videoSessionMap.put(requestChatterId, videoSession);
        videoSessionMap.put(acceptChatterId,videoSession);
        return videoSession;
    }

    @Override
    public String removeVideoSession(String chatterId) {
        VideoSession target = videoSessionMap.get(chatterId);
        if (null == target) {
            return null;
        }
        Chatter request = target.getRequest();
        Chatter accept = target.getAccept();
        if (null != target && request != null && accept != null) {
            // 将状态改为free
            target.getRequest().getVideoState().set(VideoStateEnum.FREE.getCode());
            target.getAccept().getVideoState().set(VideoStateEnum.FREE.getCode());
            // 移除video-session
            videoSessionMap.remove(target.getAccept().getId());
            videoSessionMap.remove(target.getRequest().getId());
        }
        // 返回需要被通知的chatterID
        return request.getId().equals(chatterId) ? accept.getId() : request.getId();
    }

    @Override
    public VideoSession selectVideoSession(String chatterId) {
        return videoSessionMap.get(chatterId);
    }

    @Deprecated
    @Override
    public List<VideoSession> listVideoSession() {
        List<VideoSession> sessionList = new ArrayList<>();

        for(String key : videoSessionMap.keySet()){
            sessionList.add(videoSessionMap.get(key));
        }

        return sessionList;
    }

    /**
     * setters : 因为后续有动态加载bean，需要loadBeanDefination，采用setter的方式进行属性注入
     * 对于cache包下每一个依赖的bean，都必须使用setter
     */
    public void setChatterFactory(ChatterFactory chatterFactory) {
        this.chatterFactory = chatterFactory;
    }

    public void setSelfConfig(SelfConfig config) {
        this.config = config;
    }
}
