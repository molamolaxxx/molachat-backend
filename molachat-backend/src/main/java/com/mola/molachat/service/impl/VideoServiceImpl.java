package com.mola.molachat.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.websocket.Action;
import com.mola.molachat.common.websocket.video.handler.impl.VideoRequestHandler;
import com.mola.molachat.common.websocket.video.handler.impl.VideoResponseHandler;
import com.mola.molachat.service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-18 17:09
 **/
@Service
@Slf4j
public class VideoServiceImpl implements VideoService {

    @Resource
    private VideoRequestHandler requestHandler;

    @Resource
    private VideoResponseHandler responseHandler;

    @Override
    public void handleRequest(Action action) {
        if (action.getData() instanceof JSONObject) {
            JSONObject data = (JSONObject) action.getData();
            // 获取data内部的请求码
            Integer code = data.getInteger("videoActionCode");
            // 发送方ID
            String from = data.getString("fromChatterId");
            // 获取需要请求转发的chatter
            String to = data.getString("toChatterId");

            log.debug("收到视频请求：{},code:{}",data.toString(),code);
            // 调用handler处理
            requestHandler.handle(code, from, to, data);
        }
    }

    @Override
    public void handleResponse(Action action) {
        if (action.getData() instanceof JSONObject) {
            JSONObject data = (JSONObject) action.getData();
            Integer code = data.getInteger("videoActionCode");
            // 发送方ID
            String from = data.getString("fromChatterId");
            // 获取需要请求转发的chatter
            String to = data.getString("toChatterId");
            log.debug("收到视频响应：" + data.toString());
            responseHandler.handle(code, from, to, data);
        }
    }
}
