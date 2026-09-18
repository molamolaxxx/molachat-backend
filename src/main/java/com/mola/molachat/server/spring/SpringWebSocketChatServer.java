package com.mola.molachat.server.spring;

import com.mola.molachat.common.MyApplicationContextAware;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.session.SpringSessionWrapper;
import com.mola.molachat.server.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 13:39
 **/
@Slf4j
public class SpringWebSocketChatServer implements WebSocketHandler {

    private ServerService serverService;

    /**
     * 物理 WebSocket session 与 ChatServer 一一对应，避免旧连接关闭时误取到新连接。
     */
    private final Map<String, ChatServer> sessionServerMap = new ConcurrentHashMap<>();

    private void initDependencyInjection(){
        // 如果不使用getBean创建ChatServer，则无法走生命周期，导致service无法注入到chatserver
        if (null == serverService) {
            serverService = MyApplicationContextAware.getApplicationContext().getBean(ServerService.class);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        initDependencyInjection();
        String[] chatterAndDeviceId = getChatterAndDeviceId(session);
        String chatterId = chatterAndDeviceId[0];
        if (!StringUtils.isEmpty(chatterId)) {
            ChatServer server = MyApplicationContextAware.getApplicationContext().getBean(ChatServer.class);
            server.onOpen(new SpringSessionWrapper(session), chatterId, chatterAndDeviceId[1]);
            sessionServerMap.put(session.getId(), server);
        } else {
            log.error("chatterId为空");
        }
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        ChatServer server = getChatServer(session);
        if (null != server) {
            server.onMessage(message.getPayload().toString());
        } else {
            log.error("[handleMessage] server为空");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        ChatServer server = getChatServer(session);
        if (null != server) {
            server.onError(exception);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        ChatServer server = sessionServerMap.remove(session.getId());
        if (null != server) {
            server.onClose();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private String[] getChatterAndDeviceId(WebSocketSession session) {
        String uri = session.getUri().toString();
        String[] chatterAndDeviceId = uri.substring(uri.lastIndexOf("/")+1).split(",");
        String[] res = new String[2];
        res[0] = chatterAndDeviceId[0];
        if (chatterAndDeviceId.length > 1) {
            res[1] = chatterAndDeviceId[1];
        }
        return res;
    }

    private ChatServer getChatServer(WebSocketSession session) {
        return sessionServerMap.get(session.getId());
    }
}
