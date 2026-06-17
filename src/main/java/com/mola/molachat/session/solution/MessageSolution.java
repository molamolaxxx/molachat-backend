package com.mola.molachat.session.solution;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.enums.ChatterPointEnum;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.common.enums.ServiceErrorEnum;
import com.mola.molachat.common.exception.service.SessionServiceException;
import com.mola.molachat.group.service.GroupService;
import com.mola.molachat.robot.solution.RobotSolution;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.server.session.SessionWrapper;
import com.mola.molachat.server.websocket.WSResponse;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.model.Session;
import com.mola.molachat.session.model.StreamMessage;
import com.mola.molachat.session.model.StreamMessageConnect;
import com.mola.molachat.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2024-01-01 19:10
 **/
@Component
@Slf4j
public class MessageSolution {

    @Resource
    private ServerService serverService;

    @Resource
    private RobotSolution robotSolution;

    @Resource
    private ChatterService chatterService;

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private GroupService groupService;

    @Resource
    private SessionService sessionService;

    private List<StreamMessageConnect> streamMessageConnectPool = Lists.newCopyOnWriteArrayList();

    @AddPoint(action = ChatterPointEnum.SEND_MESSAGE, key = "#message.chatterId")
    public Message insertMessage(String sessionId, Message message) throws SessionServiceException {
        //1.查询是否存在对应session
        Session session = sessionFactory.selectById(sessionId);
        if (null == session){
            throw new SessionServiceException(ServiceErrorEnum.SESSION_NOT_FOUND);
        }

        //2.向session中插入message
        sessionFactory.insertMessage(session.getSessionId(), message);

        //3.向socket服务器发送消息,找到session内除发送者的所有ws服务器对象
        for (String chatterId : session.getChatterSet().stream().map(Chatter::getId).collect(Collectors.toList())){
            Chatter chatter = chatterFactory.select(chatterId);
            // 如果为机器人，进行插槽调用，利用handler解析message
            if (chatter instanceof RobotChatter && !Objects.equals(message.getChatterId(), chatter.getId())) {
                robotSolution.onReceiveMessage(message, sessionId, (RobotChatter)chatter);
                continue;
            }
            //构建response,向不同客户端发送
            try {
                List<ChatServer> servers = serverService.selectByChatterId(chatterId);
                if (!CollectionUtils.isEmpty(servers)) {
                    for (ChatServer server : servers) {
                        server.getSession().sendToClient(WSResponse.message("send content!", message));
                    }
                } else {
                    // 将消息存入消息队列
                    chatterService.offerMessageIntoQueue(message,chatterId);
                }
            } catch (Exception e) {
                throw new SessionServiceException(ServiceErrorEnum.SEND_MESSAGE_ERROR, e.getMessage());
            }
        }
        return message;
    }

    public void updateMessage(String sessionId, Message message) throws SessionServiceException {
        //1.查询是否存在对应session
        Session session = sessionFactory.selectById(sessionId);
        if (null == session){
            throw new SessionServiceException(ServiceErrorEnum.SESSION_NOT_FOUND);
        }

        //2.向session中插入message
        sessionFactory.updateMessage(session.getSessionId(), message);
    }

    /**
     * 是否已经存在stream
     * @param senderId
     * @param sessionId
     * @return
     */
    public StreamMessageConnect findStreamConnect(String senderId, String sessionId) {
        return streamMessageConnectPool.stream()
                .filter(connect -> Objects.equals(connect.getSenderId(), senderId)
                        && Objects.equals(connect.getSessionId(), sessionId))
                .findAny().orElse(null);
    }

    /**
     * 发送流消息
     * @param sessionId
     * @param streamMessage
     */
    public void sendStreamMessage(String sessionId, StreamMessage streamMessage) {
        //1.查询是否存在对应session
        Session session = sessionFactory.selectById(sessionId);
        if (null == session){
            throw new SessionServiceException(ServiceErrorEnum.SESSION_NOT_FOUND);
        }

        StreamMessageConnect messageConnect = null;
        synchronized (this) {
            messageConnect = streamMessageConnectPool.stream()
                    .filter(connect -> Objects.equals(connect.getSenderId(), streamMessage.getChatterId())
                            && Objects.equals(connect.getSessionId(), sessionId)).findAny().orElse(null);
            if (messageConnect == null) {
                messageConnect = new StreamMessageConnect(
                        UUID.randomUUID().toString(),
                        streamMessage.getChatterId(),sessionId,
                        new StringBuilder(),
                        streamMessage,
                        Sets.newHashSet(),
                        false,
                        Thread.currentThread()
                );
                streamMessageConnectPool.add(messageConnect);
            }
        }
        streamMessage.setStreamId(messageConnect.getStreamId());
        streamMessage.setId(messageConnect.getStreamId());
        messageConnect.getMessageContent().append(streamMessage.getContent());

        // 发送请求到服务器
        for (String chatterId : session.getChatterSet().stream().map(Chatter::getId).collect(Collectors.toList())){
            if (Objects.equals(chatterId, messageConnect.getSenderId())) {
                continue;
            }
            //构建response,向不同客户端发送
            try {
                List<ChatServer> servers = serverService.selectByChatterId(chatterId);
                if (!CollectionUtils.isEmpty(servers)) {
                    for (ChatServer server : servers) {
                        SessionWrapper sw = server.getSession();
                        sw.sendToClient(WSResponse.steamMessage(
                                "send stream content!", streamMessage));
                    }
                }
            } catch (Exception e) {
                log.warn("sendSteamMessage send Stream failed, message = {}", streamMessage, e);
            }
        }

        if (streamMessage.isEnd()) {
            streamMessageConnectPool.remove(messageConnect);
            Message message = new Message();
            BeanUtils.copyProperties(streamMessage, message);
            message.setContent(messageConnect.getMessageContent().toString());
            sessionFactory.insertMessage(session.getSessionId(), message);

            // 流式消息完成后，通知接收方刷新session
            for (String chatterId : session.getChatterSet().stream().map(Chatter::getId).collect(Collectors.toList())) {
                if (Objects.equals(chatterId, messageConnect.getSenderId())) {
                    continue;
                }
                List<ChatServer> servers = serverService.selectByChatterId(chatterId);
                if (!CollectionUtils.isEmpty(servers)) {
                    // 推送 CREATE_SESSION 让前端重新渲染完整消息列表
                    SessionDTO sessionDTO = sessionService.findSession(session.getSessionId());
                    for (ChatServer server : servers) {
                        try {
                            server.getSession().sendToClient(WSResponse.createSession("ok", sessionDTO));
                        } catch (Exception e) {
                            log.warn("push createSession after stream end failed, chatterId = {}", chatterId, e);
                        }
                    }
                } else {
                    // 离线，放入队列
                    chatterService.offerMessageIntoQueue(message, chatterId);
                }
            }
        }
    }

    /**
     * 重连后立即推送正在进行的流式消息全量内容
     */
    public void pushPendingStreamMessages(String chatterId, SessionWrapper sessionWrapper) {
        for (StreamMessageConnect connect : streamMessageConnectPool) {
            Session session = sessionFactory.selectById(connect.getSessionId());
            if (session == null) {
                continue;
            }
            boolean isReceiver = session.getChatterSet().stream()
                    .anyMatch(c -> Objects.equals(c.getId(), chatterId) && !Objects.equals(c.getId(), connect.getSenderId()));
            if (!isReceiver) {
                continue;
            }
            StreamMessage firstSendMessage = connect.getFirstSendMessage();
            StreamMessage snapshot = new StreamMessage();
            BeanUtils.copyProperties(firstSendMessage, snapshot);
            snapshot.setContent(connect.getMessageContent().toString());
            snapshot.setStreamId(connect.getStreamId());
            snapshot.setId(connect.getStreamId());
            snapshot.setEnd(false);
            try {
                connect.getInitedSessions().add(sessionWrapper);
                sessionWrapper.sendToClient(WSResponse.steamMessage("send stream content!", snapshot));
            } catch (Exception e) {
                log.warn("pushPendingStreamMessages failed, chatterId = {}", chatterId, e);
            }
        }
    }

    public boolean stopStream(String senderId, String sessionId) {
        //1.查询是否存在对应session
        Session session = sessionFactory.selectById(sessionId);
        if (null == session){
            throw new SessionServiceException(ServiceErrorEnum.SESSION_NOT_FOUND);
        }

        StreamMessageConnect streamConnect = findStreamConnect(senderId, sessionId);
        if (streamConnect == null) {
            return false;
        }
        if (streamConnect.getHolder() != Thread.currentThread()) {
            log.info("stopStream thread is not same, senderId = {}, sessionId = {}, stableThread = {}, currentThread = {}",
                    senderId, sessionId, streamConnect.getHolder(), Thread.currentThread());
            return false;
        }

        streamMessageConnectPool.remove(streamConnect);
        Message message = new Message();
        BeanUtils.copyProperties(streamConnect, message);
        message.setContent(streamConnect.getMessageContent().toString());
        sessionFactory.insertMessage(session.getSessionId(), message);
        return true;
    }

    /**
     * 强制终止流式消息，不检查线程，向前端发送end信号
     */
    public boolean forceStopStream(String senderId, String sessionId) {
        Session session = sessionFactory.selectById(sessionId);
        if (session == null) {
            return false;
        }

        StreamMessageConnect streamConnect = findStreamConnect(senderId, sessionId);
        if (streamConnect == null) {
            return false;
        }

        streamConnect.setClosed(true);
        streamMessageConnectPool.remove(streamConnect);

        // 持久化已有内容
        Message message = new Message();
        BeanUtils.copyProperties(streamConnect.getFirstSendMessage(), message);
        message.setContent(streamConnect.getMessageContent().toString());
        message.setId(streamConnect.getStreamId());
        sessionFactory.insertMessage(session.getSessionId(), message);

        // 向前端发送 end 信号
        StreamMessage endMsg = new StreamMessage();
        endMsg.setStreamId(streamConnect.getStreamId());
        endMsg.setId(streamConnect.getStreamId());
        endMsg.setChatterId(senderId);
        endMsg.setSessionId(sessionId);
        endMsg.setContent("");
        endMsg.setEnd(true);
        for (String chatterId : session.getChatterSet().stream()
                .map(Chatter::getId).collect(Collectors.toList())) {
            if (Objects.equals(chatterId, senderId)) {
                continue;
            }
            try {
                List<ChatServer> servers = serverService.selectByChatterId(chatterId);
                if (!CollectionUtils.isEmpty(servers)) {
                    for (ChatServer server : servers) {
                        server.getSession().sendToClient(WSResponse.steamMessage("force stop", endMsg));
                    }
                }
            } catch (Exception e) {
                log.warn("forceStopStream send end signal failed, chatterId = {}", chatterId, e);
            }
        }
        return true;
    }

}
