package com.mola.molachat.session.service.impl;

import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.session.constant.SessionConstant;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.common.exception.SessionException;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.chatter.enums.ChatterPointEnum;
import com.mola.molachat.common.enums.ServiceErrorEnum;
import com.mola.molachat.common.exception.service.SessionServiceException;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.common.utils.BeanUtilsPlug;
import com.mola.molachat.common.utils.IdUtils;
import com.mola.molachat.common.utils.SegmentLock;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:08
 * @Version 1.0
 */
@Service
public class SessionServiceImpl implements SessionService{

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private SegmentLock segmentLock;

    @Override
    public SessionDTO create(Set<ChatterDTO> chatterDTOSet) {
        //检查是否set中的chatter都存在，不存在返回错误
        for (ChatterDTO chatterDTO : chatterDTOSet){
            if (null == chatterDTO.getId() || null == chatterFactory.select(chatterDTO.getId())){
                throw new SessionServiceException(ServiceErrorEnum.SESSION_CREATE_ERROR);
            }
        }
        Set<Chatter> chatterSet = new HashSet<>();
        for (ChatterDTO dto : chatterDTOSet){
            chatterSet.add((Chatter) BeanUtilsPlug.copyPropertiesReturnTarget(dto, new Chatter()));
        }
        Session session = sessionFactory.create(chatterSet);
        return (SessionDTO) BeanUtilsPlug.copyPropertiesReturnTarget(session, new SessionDTO());
    }

    @Override
    public boolean deleteById(String sessionId) {
        Session sessionToDelete = new Session();
        sessionToDelete.setSessionId(sessionId);
        sessionFactory.remove(sessionToDelete);
        return true;
    }

    @Override
    public SessionDTO findOrCreateSession(String chatterId1, String chatterId2) {
        Assert.isTrue(!StringUtils.equals(chatterId1, chatterId2), "会话建立失败，id1 equals id2");
        SessionDTO result = null;
        String sessId1 = chatterId1 + chatterId2;
        String sessId2 = chatterId2 + chatterId1;
        Session session1 = sessionFactory.selectById(sessId1);
        Session session2 = sessionFactory.selectById(sessId2);
        Session session = (null == session1 ? session2 : session1);

        //如果为null 创建session
        if (null == session){
            //创建对象
            ChatterDTO dto1 = new ChatterDTO();
            dto1.setId(chatterId1);
            ChatterDTO dto2 = new ChatterDTO();
            dto2.setId(chatterId2);
            Set<ChatterDTO> dtoSet = new HashSet<>();
            dtoSet.add(dto1);
            dtoSet.add(dto2);
            result = this.create(dtoSet);
        } else {
            result = (SessionDTO) BeanUtilsPlug
                    .copyPropertiesReturnTarget(session, new SessionDTO());
        }
        return result;
    }

    @Override
    public SessionDTO findSession(String sessionId) {
        Session session = sessionFactory.selectById(sessionId);
        if (null == session) {
            return null;
        }
        return (SessionDTO) BeanUtilsPlug
                .copyPropertiesReturnTarget(session, new SessionDTO());
    }

    @Override
    @AddPoint(action = ChatterPointEnum.INTO_COMMON, key = "#chatterId")
    public SessionDTO findCommonAndGroupSession(String chatterId, String sessionId) {
        // 查询chatter
        Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter) {
            throw new SessionServiceException(ServiceErrorEnum.SESSION_CREATE_ERROR);
        }
        Session commonSession = sessionFactory.selectById(sessionId);
        if (null == commonSession && SessionConstant.COMMON_SESSION_ID.equals(sessionId)) {
            // 创建公共聊天区域
            commonSession = createCommonSession(chatter);
        } else {
            // 扫描可能会引发线程安全问题
            try {
                segmentLock.lock(commonSession);
                commonSession.getChatterSet().add(chatter);
            } finally {
                segmentLock.unlock(commonSession);
            }
        }
        return (SessionDTO) BeanUtilsPlug.copyPropertiesReturnTarget(commonSession, new SessionDTO());
    }

    private Session createCommonSession(Chatter chatter) {
        Session commonSession = new Session();
        commonSession.setSessionId("common-session");
        Set<Chatter> chatterSet = new HashSet<>();
        chatterSet.add(chatter);
        commonSession.setChatterSet(chatterSet);
        commonSession.setMessageList(new CopyOnWriteArrayList<>());
        sessionFactory.create(commonSession);
        return commonSession;
    }

    private Session createGroupSessionInner(Set<String> chatterIds, String creatorId) {
        Session groupSession = new Session();
        groupSession.setSessionId(IdUtils.getSessionId());
        List<Chatter> chatters = chatterIds.stream()
                .map(id -> chatterFactory.select(id))
                .filter(chatter -> null != chatter)
                .collect(Collectors.toList());
        groupSession.setChatterSet(new HashSet<>(chatters));
        sessionFactory.create(groupSession);
        return groupSession;
    }

    @Override
    public List<SessionDTO> list() {
        List<Session> sessionList = sessionFactory.list();

        List<SessionDTO> sessionDTOS = sessionList.stream().map(e -> (SessionDTO) BeanUtilsPlug
                .copyPropertiesReturnTarget(e, new SessionDTO()))
                .collect(Collectors.toList());

        return sessionDTOS;
    }

    @Override
    public Integer closeSessions(String chatterId) throws SessionServiceException {
        Integer result = 0;
        //当用户关掉页面时，删除所有session
        List<Session> sessionList = sessionFactory.list();

        for (Session session : sessionList){
            if (session.getSessionId().contains(chatterId)){
                try {
                    sessionFactory.remove(session);
                    result ++;
                } catch (SessionException e) {
                    throw new SessionServiceException(ServiceErrorEnum.SESSIONS_CLOSE_ERROR, e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public void save(List<SessionDTO> sessionList) {
        for (SessionDTO dto : sessionList){
            if (null == sessionFactory.selectById(dto.getSessionId())){
                sessionFactory.create((Session) BeanUtilsPlug
                        .copyPropertiesReturnTarget(dto, new Session()));
            }
        }
    }

    @Override
    public void sendMessageAsync(ChatServer server, Message message) {
    }

    @Override
    public void removeChatterFromSession(Chatter chatter, SessionDTO session) {
        sessionFactory.selectById(session.getSessionId())
                .getChatterSet().remove(chatter);
    }
}
