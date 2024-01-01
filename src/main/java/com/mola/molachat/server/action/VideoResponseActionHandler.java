package com.mola.molachat.server.action;

import com.mola.molachat.common.annotation.Handler;
import com.mola.molachat.server.websocket.Action;
import com.mola.molachat.server.websocket.WSResponseCode;
import com.mola.molachat.session.service.VideoService;
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
public class VideoResponseActionHandler implements WSRequestActionHandler{

    @Resource
    private VideoService videoService;

    @Override
    public Integer actonCode() {
        return WSResponseCode.VIDEO_RESPONSE;
    }

    @Override
    public void handle(Action action) throws Exception {
        // 视频请求
        videoService.handleResponse(action);
    }
}
