package com.mola.molachat.session.service;

import com.mola.molachat.server.websocket.Action;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 视频聊天
 * @date : 2020-05-18 17:08
 **/
public interface VideoService {

    /**
     * 处理视频请求
     * @param action
     */
    void handleRequest(Action action);

    /**
     * 处理视频响应
     * @param action
     */
    void handleResponse(Action action);
}
