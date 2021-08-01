package com.mola.molachat.handler.action;

import com.mola.molachat.annotation.Handler;
import com.mola.molachat.common.websocket.Action;
import com.mola.molachat.common.websocket.ActionCode;
import com.mola.molachat.service.VideoService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-05-17 00:54
 **/
@Handler
@Slf4j
public class VideoRequestActionHandler implements WSRequestActionHandler{

    @Resource
    private VideoService videoService;

    @Override
    public Integer actonCode() {
        return ActionCode.VIDEO_REQUEST;
    }

    @Override
    public void handle(Action action) throws Exception {
        // 视频请求
        videoService.handleRequest(action);
    }
}
