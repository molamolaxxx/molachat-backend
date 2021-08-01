package com.mola.molachat.service.impl;

import com.mola.molachat.annotation.AddPoint;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.data.ServerFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.enumeration.ChatterPointEnum;
import com.mola.molachat.enumeration.ServiceErrorEnum;
import com.mola.molachat.exception.ServerException;
import com.mola.molachat.exception.service.ServerServiceException;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    public ChatServer selectByChatterId(String chatterId) {
        return serverFactory.selectOne(chatterId);
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
        ChatServer server = this.selectByChatterId(targetChatterId);
        if (null != server) {
            try {
                server.getSession().sendToClient(response);
            } catch (Exception e) {
                log.error("响应失败",e);
            }
        }
    }

    @Override
    @AddPoint(action = ChatterPointEnum.HEARTBEAT, key = "#chatterId")
    public void setHeartBeat(String chatterId) {
        ChatServer server = serverFactory.selectOne(chatterId);
        Chatter chatter = chatterFactory.select(chatterId);
        if (null == server){
            //未找到chatterId对应的服务器
            throw new ServerServiceException(ServiceErrorEnum.SERVER_NOT_FOUND);
        }
        long cur = System.currentTimeMillis();
        server.setLastHeartBeat(cur);
        chatter.setLastOnline(cur);
    }
}
