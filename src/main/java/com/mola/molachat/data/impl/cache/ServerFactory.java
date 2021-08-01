package com.mola.molachat.data.impl.cache;

import com.mola.molachat.annotation.RefreshChatterList;
import com.mola.molachat.data.ServerFactoryInterface;
import com.mola.molachat.enumeration.DataErrorCodeEnum;
import com.mola.molachat.exception.ServerException;
import com.mola.molachat.server.ChatServer;
import com.mola.molachat.server.session.SessionWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private static Map<String, ChatServer> serverMap;

    public ServerFactory(){
        serverMap = new ConcurrentHashMap<>();
    }

    @Override
    @RefreshChatterList
    public ChatServer create(ChatServer server) throws ServerException{
        if (serverMap.keySet().contains(server.getChatterId())){
            throw new ServerException(DataErrorCodeEnum.CREATE_SERVER_ERROR);
        }
        serverMap.put(server.getChatterId(), server);

        return server;
    }

    @Override
    @RefreshChatterList
    public ChatServer remove(ChatServer server) throws ServerException{
        if (!serverMap.keySet().contains(server.getChatterId())){
            throw new ServerException(DataErrorCodeEnum.REMOVE_SERVER_ERROR);
        }
        serverMap.remove(server.getChatterId());

        return server;
    }

    @Override
    public ChatServer selectOne(String chatterId){
        return serverMap.get(chatterId);
    }

    @Override
    public SessionWrapper selectWSSessionByChatterId(String chatterId) throws ServerException{

        ChatServer server = this.selectOne(chatterId);

        return server.getSession();
    }

    @Override
    public List<ChatServer> list() {
        List<ChatServer> serverList = new ArrayList<>();
        for (String key : serverMap.keySet()){
            serverList.add(serverMap.get(key));
        }
        return serverList;
    }

    /**
     * setters : 因为后续有动态加载bean，需要loadBeanDefination，采用setter的方式进行属性注入
     * 对于cache包下每一个依赖的bean，都必须使用setter
     */
}
