package com.mola.molachat.server.websocket.video.handler;

import com.alibaba.fastjson.JSONObject;

public interface IVideoHandler {

    /**
     * 处理视频通话请求
     * @param code
     * @param from 发送方
     * @param to 接收方
     * @param data
     */
    void handle(Integer code,String from,  String to, JSONObject data);
}
