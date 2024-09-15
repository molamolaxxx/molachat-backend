package com.mola.molachat.server.service.impl;

import com.mola.molachat.chatter.data.ChatterFactoryInterface;
import com.mola.molachat.chatter.enums.ChatterPointEnum;
import com.mola.molachat.common.annotation.AddPoint;
import com.mola.molachat.common.enums.ServiceErrorEnum;
import com.mola.molachat.common.exception.ServerException;
import com.mola.molachat.common.exception.service.ServerServiceException;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.data.ServerFactoryInterface;
import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.server.websocket.WSResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:59
 * @Version 1.0
 */
@Service
@Slf4j
public class ServerServiceImpl implements ServerService {

    @Autowired
    private ServerFactoryInterface serverFactory;

    @Autowired
    private ChatterFactoryInterface chatterFactory;

    @Override
    public ChatServer selectByChatterId(String chatterId, String deviceId) {
        return serverFactory.selectOne(chatterId, deviceId);
    }

    @Override
    public List<ChatServer> selectByChatterId(String chatterId) {
        return serverFactory.selectByChatterId(chatterId);
    }

    @Override
    @AddPoint(action = ChatterPointEnum.CREATE_SERVER, key = "#chatServer.chatterId")
    public ChatServer create(ChatServer chatServer){
        //data层创建服务器
        ChatServer server = null;
        try {
            server = serverFactory.create(chatServer);
        } catch (ServerException e) {
            throw new ServerServiceException(ServiceErrorEnum.SERVER_CREATE_ERROR, e.getMessage());
        }
        return server;
    }

    @Override
    public ChatServer remove(ChatServer chatServer) {

        ChatServer server = null;
        try {
            server = serverFactory.remove(chatServer);
        } catch (ServerException e) {
            throw new ServerServiceException(ServiceErrorEnum.SERVER_REMOVE_ERROR, e.getMessage());
        }

        return server;
    }

    @Override
    public List<ChatServer> list() {
        return serverFactory.list();
    }

    @Override
    public void sendResponse(String targetChatterId, WSResponse response) {
        List<ChatServer> servers = this.selectByChatterId(targetChatterId);
        if (CollectionUtils.isEmpty(servers)) {
            return;
        }
        for (ChatServer server : servers) {
            try {
                server.getSession().sendToClient(response);
            } catch (Exception e) {
                log.error("响应失败 {} {}", server.getChatterId(), server.getDeviceId(),e);
            }
        }
    }

    @Override
    public void sendResponse(String targetChatterId, String targetDeviceId, WSResponse response) {
        ChatServer server = this.selectByChatterId(targetChatterId, targetDeviceId);
        if (server == null) {
            return;
        }
        try {
            server.getSession().sendToClient(response);
        } catch (Exception e) {
            log.error("响应失败 {} {}", server.getChatterId(), server.getDeviceId(),e);
        }
    }

    @Override
    @AddPoint(action = ChatterPointEnum.HEARTBEAT, key = "#chatterId")
    public void setHeartBeat(String chatterId, String deviceId) {
        ChatServer server = serverFactory.selectOne(chatterId, deviceId);
        if (null == server){
            //未找到chatterId对应的服务器
            throw new ServerServiceException(ServiceErrorEnum.SERVER_NOT_FOUND);
        }
        long cur = System.currentTimeMillis();
        server.setLastHeartBeat(cur);
        chatterFactory.select(chatterId).setLastOnline(cur);
    }
}
