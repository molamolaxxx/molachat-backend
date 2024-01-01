package com.mola.molachat.server.websocket.video.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.server.websocket.WSResponse;
import com.mola.molachat.server.websocket.video.VideoResponseCode;
import com.mola.molachat.server.websocket.video.VideoWSResponse;
import com.mola.molachat.server.websocket.video.handler.IVideoHandler;
import com.mola.molachat.session.data.SessionFactoryInterface;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.session.enums.VideoStateEnum;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.server.service.ServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-19 02:02
 **/
@Component
public class VideoResponseHandler implements IVideoHandler {

    @Autowired
    private ChatterService chatterService;

    @Autowired
    private ServerService serverService;

    @Autowired
    private SessionFactoryInterface sessionFactory;

    @Override
    public void handle(Integer code,String from,  String to, JSONObject data) {
        switch (code) {
            // 接受请求的回复
            case VideoResponseCode.RESPONSE_ACCEPT:{
                handleAccept(from, to, data);
                break;
            }
            // 拒绝请求的回复
            case VideoResponseCode.RESPONSE_REFUSE:{
                handleRefuse(from, to, data);
                break;
            }
        }
    }

    // 处理对方接受视频请求的响应
    public synchronized void handleAccept(String from, String to, JSONObject data) {
        // 查找是否有对应chatter
        ChatterDTO toChatter = chatterService.selectById(to);
        ChatterDTO fromChatter = chatterService.selectById(from);
        if (null == toChatter) {
            // 向目标客户端发送异常消息
            serverService.sendResponse(from, VideoWSResponse
                    .failed("不存在视频请求确认的对象", null));
            return;
        }
        if (toChatter.getVideoState().get() != VideoStateEnum.READY.getCode()) {
            // 对方线路不在等待中
            serverService.sendResponse(from, VideoWSResponse
                    .failed("对方线路已经被占用", null));
            return;
        }
        // 改变双方状态
        if (chatterService.casVideoState(to, VideoStateEnum.READY.getCode(), VideoStateEnum.OCCUPY.getCode())
        && chatterService.casVideoState(from, VideoStateEnum.READY.getCode(), VideoStateEnum.OCCUPY.getCode())){
            JSONObject json = new JSONObject();
            json.put("fromChatterId",from);
            // 建立连接成功
            serverService.sendResponse(to, VideoWSResponse
                    .requestVideoAccept("接受视频请求", json));
        } else {
            String msg = "协商失败，建立连接失败";
            // 建立连接失败
            serverService.sendResponse(to, VideoWSResponse
                    .failed(msg, null));
            serverService.sendResponse(from, VideoWSResponse
                    .failed(msg, null));
            // 删除video-session
            sessionFactory.removeVideoSession(from);
            chatterService.changeVideoState(to, VideoStateEnum.FREE.getCode());
            chatterService.changeVideoState(from, VideoStateEnum.FREE.getCode());
        }
    }

    // 处理对方拒绝视频请求的响应
    public synchronized void handleRefuse(String from, String to, JSONObject data) {
        // 查找是否有对应chatter
        ChatterDTO toChatter = chatterService.selectById(to);
        ChatterDTO fromChatter = chatterService.selectById(from);
        if (null == toChatter) {
            // 向目标客户端发送异常消息
            serverService.sendResponse(from, VideoWSResponse
                    .failed("不存在视频请求拒绝的对象", null));
            return;
        }
        if (toChatter.getVideoState().get() != VideoStateEnum.READY.getCode()  ||
                fromChatter.getVideoState().get() != VideoStateEnum.READY.getCode()) {
            serverService.sendResponse(from, WSResponse.exception("不在准备中，无法拒绝", null));
            return;
        }
        // 改变双方状态
        chatterService.changeVideoState(to,VideoStateEnum.FREE.getCode());
        chatterService.changeVideoState(from, VideoStateEnum.FREE.getCode());
        // 删除video-session
        sessionFactory.removeVideoSession(from);
        // 释放连接成功
        serverService.sendResponse(to, VideoWSResponse
                .requestVideoRefuse("拒绝视频请求", null));
    }

}
