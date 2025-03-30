package com.mola.molachat.session.solution;

import com.mola.molachat.server.service.ServerService;
import com.mola.molachat.server.websocket.video.VideoWSResponse;
import com.mola.molachat.session.data.SessionFactoryInterface;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-03-29 22:20
 **/
@Component
public class VideoSessionSolution {

    @Resource
    private SessionFactoryInterface sessionFactory;

    @Resource
    private ServerService serverService;

    public void deleteVideoSession(String chatterId) {
        String needToAlert = sessionFactory.removeVideoSession(chatterId);
        // 发消息,挂断视频
        if (null != needToAlert) {
            serverService.sendResponse(needToAlert, VideoWSResponse
                    .requestVideoOff("挂断视频", null));
        }
    }
}
