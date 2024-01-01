package com.mola.molachat.server.spring;

import com.mola.molachat.common.MyApplicationContextAware;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.session.SpringSessionWrapper;
import com.mola.molachat.server.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.*;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-04-02 13:39
 **/
@Slf4j
public class SpringWebSocketChatServer implements WebSocketHandler {

    private ServerService serverService;

    private void initDependencyInjection(){
        // 如果不使用getBean创建ChatServer，则无法走生命周期，导致service无法注入到chatserver
        if (null == serverService) {
            serverService = MyApplicationContextAware.getApplicationContext().getBean(ServerService.class);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        initDependencyInjection();
        String chatterId = getChatterId(session);
        if (!StringUtils.isEmpty(chatterId)) {
            ChatServer server = serverService.selectByChatterId(chatterId);
            if (null == server) {
                server = MyApplicationContextAware.getApplicationContext().getBean(ChatServer.class);
            }
            server.onOpen(new SpringSessionWrapper(session), chatterId);
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
        ChatServer server = getChatServer(session);
        if (null != server) {
            server.onClose();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private String getChatterId(WebSocketSession session) {
        String uri = session.getUri().toString();
        String chatterId = uri.substring(uri.lastIndexOf("/")+1);
        return chatterId;
    }

    private ChatServer getChatServer(WebSocketSession session) {
        String chatterId = getChatterId(session);
        if (!StringUtils.isEmpty(chatterId)) {
            return serverService.selectByChatterId(chatterId);
        } else {
            log.error("chatterId为空");
        }
        return null;
    }
}
