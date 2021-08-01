package com.mola.molachat.data.impl.cache;

import com.mola.molachat.condition.CacheCondition;
import com.mola.molachat.config.SelfConfig;
import com.mola.molachat.data.SessionFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.Session;
import com.mola.molachat.entity.VideoSession;
import com.mola.molachat.enumeration.DataErrorCodeEnum;
import com.mola.molachat.enumeration.VideoStateEnum;
import com.mola.molachat.exception.SessionException;
import com.mola.molachat.utils.IdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
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

        //2.创建
        this.create(session);

        return session;
    }

    @Override
    public Session create(Session session) {
        if (StringUtils.isEmpty(session.getSessionId())) {
            session.setSessionId(IdUtils.getSessionId());
        }
        session.setCreateTime(new Date());
        if (session.getMessageList() == null) {
            session.setMessageList(new ArrayList<>());
        }
        sessionMap.put(session.getSessionId(), session);
        return session;
    }

    @Override
    public Session selectById(String id) {
        return sessionMap.get(id);
    }


    @Override
    public Session remove(Session session) throws SessionException{

        if (!sessionMap.keySet().contains(session.getSessionId())){
            throw new SessionException(DataErrorCodeEnum.REMOVE_SESSION_ERROR);
        }
        sessionMap.remove(session.getSessionId());

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

    @Override
    public void save(String sessionId) {
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
