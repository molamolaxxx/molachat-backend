package com.mola.molachat.server.data.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mola.molachat.common.annotation.RefreshChatterList;
import com.mola.molachat.common.enums.DataErrorCodeEnum;
import com.mola.molachat.common.exception.ServerException;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.data.ServerFactoryInterface;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: molamola
 * @Date: 19-8-6 上午11:20
 * @Version 1.0
 */
@Component
@Slf4j
public class ServerFactory implements ServerFactoryInterface {

    /**
     * chatterId -> ChatServer
     */
    private static Map<String, Set<ChatServer>> serverMap;

    public ServerFactory(){
        serverMap = new ConcurrentHashMap<>();
    }

    @Override
    @RefreshChatterList
    public ChatServer create(ChatServer server) throws ServerException {
        serverMap.putIfAbsent(server.getChatterId(), Sets.newConcurrentHashSet());
        Set<ChatServer> servers = serverMap.get(server.getChatterId());
        if (servers.contains(server)){
            log.error("服务器创建重复, chatter = {}, deviceId = {}", server.getChatterId(), server.getDeviceId());
            throw new ServerException(DataErrorCodeEnum.CREATE_SERVER_ERROR);
        }
        servers.add(server);
        return server;
    }

    @Override
    @RefreshChatterList
    public ChatServer remove(ChatServer server) throws ServerException{
        if (!serverMap.containsKey(server.getChatterId())){
            throw new ServerException(DataErrorCodeEnum.REMOVE_SERVER_ERROR);
        }
        serverMap.get(server.getChatterId()).remove(server);
        return server;
    }

    @Override
    public ChatServer selectOne(String chatterId, String deviceId){
        if (!serverMap.containsKey(chatterId)) {
            return null;
        }
        return serverMap.get(chatterId).stream()
                .filter(server -> Objects.equals(server.getDeviceId(), deviceId))
                .findAny().orElse(null);
    }

    @Override
    public List<ChatServer> selectByChatterId(String chatterId) {
        if (!serverMap.containsKey(chatterId)) {
            return Collections.emptyList();
        }
        return Lists.newArrayList(serverMap.get(chatterId));
    }

    @Override
    public List<ChatServer> list() {
        List<ChatServer> serverList = new ArrayList<>();
        for (String key : serverMap.keySet()){
            serverList.addAll(serverMap.get(key));
        }
        return serverList;
    }

    /**
     * setters : 因为后续有动态加载bean，需要loadBeanDefination，采用setter的方式进行属性注入
     * 对于cache包下每一个依赖的bean，都必须使用setter
     */
}
