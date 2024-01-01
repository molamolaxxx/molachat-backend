package com.mola.molachat.server.action;

import com.mola.molachat.common.annotation.Handler;
import com.mola.molachat.server.websocket.Action;
import com.mola.molachat.server.websocket.ActionCode;
import com.mola.molachat.server.service.ServerService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-17 00:53
 **/
@Handler
@Slf4j
public class HeartBeatHandler implements WSRequestActionHandler{

    @Resource
    private ServerService serverService;

    @Override
    public Integer actonCode() {
        return ActionCode.HEART_BEAT;
    }

    @Override
    public void handle(Action action) throws Exception {
        String chatterId = (String) action.getData();
        //log.info("action:客户端发送心跳, id:"+chatterId);
        serverService.setHeartBeat(chatterId);
    }
}
