package com.mola.molachat.common.websocket.video.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.websocket.WSResponse;
import com.mola.molachat.common.websocket.video.VideoRequestCode;
import com.mola.molachat.common.websocket.video.VideoWSResponse;
import com.mola.molachat.common.websocket.video.handler.IVideoHandler;
import com.mola.molachat.data.SessionFactoryInterface;
import com.mola.molachat.entity.VideoSession;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.VideoStateEnum;
import com.mola.molachat.service.ChatterService;
import com.mola.molachat.service.ServerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-19 02:02
 **/
@Component
@Slf4j
public class VideoRequestHandler implements IVideoHandler {

    @Autowired
    private ChatterService chatterService;

    @Autowired
    private ServerService serverService;

    @Autowired
    private SessionFactoryInterface sessionFactory;



    @Override
    public void handle(Integer code, String from, String to, JSONObject data)  {
        switch (code) {
            case VideoRequestCode.REQUEST_VIDEO_ON:{
                // 视频请求
                handleVideoOn(from, to, data);
                break;
            }
            case VideoRequestCode.REQUEST_VIDEO_OFF:{
                // 取消视频请求
                handleVideoOff(from, to, data);
                break;
            }
            case VideoRequestCode.SIGNALLING_CHANGE:{
                // 信令交换
                handleSignallingChange(from, to, data);
                break;
            }
            case VideoRequestCode.REQUEST_CANCEL: {
                // 取消请求
                handleRequestCancel(from, to, data);
                break;
            }
            case VideoRequestCode.VIDEO_STATE_CHANGE: {
                // 视频状态变化，如从视频变成屏幕共享
                handleVideoStateChange(from, to, data);
                break;
            }
        }
    }

    /**
     * 在对方接电话之前，取消通话请求
     * @param from
     * @param to
     * @param data
     */
    private synchronized void handleRequestCancel(String from, String to, JSONObject data) {
        // 取消请求
        // 查找对应chatter
        ChatterDTO toChatter = chatterService.selectById(to);
        // 检查状态能否进行取消
        checkVideoChannelState(toChatter, from);
        ChatterDTO fromChatter = chatterService.selectById(from);
        // 改变双方状态为释放
        chatterService.changeVideoState(to, VideoStateEnum.FREE.getCode());
        chatterService.changeVideoState(from, VideoStateEnum.FREE.getCode());
        // 删除video-session
        sessionFactory.removeVideoSession(from);
        // 释放连接成功
        serverService.sendResponse(to, VideoWSResponse
                .requestVideoCancel("取消视频", null));
    }

    private void handleSignallingChange(String from, String to, JSONObject data) {
        // 信令交换
        serverService.sendResponse(to, VideoWSResponse.requestSignalChange("信令交换", data));
    }

    public synchronized void handleVideoOn(String from, String to, JSONObject data){
        // 查找是否有对应chatter
        ChatterDTO toChatter = chatterService.selectById(to);
        // 检查状态能否进行通信
        checkVideoChannelState(toChatter, from);
        if (toChatter.getVideoState().get() != VideoStateEnum.FREE.getCode()) {
            VideoSession videoSession = sessionFactory.selectVideoSession(from);
            if (videoSession != null) {
                if (videoSession.getRequest().getId().equals(to)) {
                    // 如果发起方已经存在videoSession且此session的发起放是对面
                    // 认为存在互相占用线路，直接返回不作处理
                    log.info("存在互相占用线路，不作处理，from:{},to:{}",from,to);
                    return;
                }
            }
            // 线路已经被占用，返回错误
            serverService.sendResponse(from, VideoWSResponse
                    .failed("对方正在通话中", null));
            return;
        }
        ChatterDTO fromChatter = chatterService.selectById(from);
        // 强条件，自身必须不在通话中，由前端进行过滤
        if (fromChatter.getVideoState().get() != VideoStateEnum.FREE.getCode()) {
            // 个人线路已经被占用，返回错误
            serverService.sendResponse(from, VideoWSResponse
                    .failed("您已经在通话中", null));
            return;
        }
        // 尝试占用对方线路（并发）
        if (chatterService.casVideoState(to,
                VideoStateEnum.FREE.getCode(),
                VideoStateEnum.READY.getCode())) {
            // 占用自己的线路
            if (chatterService.casVideoState(from, VideoStateEnum.FREE.getCode(), VideoStateEnum.READY.getCode())) {
                // 创建video-session
                sessionFactory.createVideoSession(from, to);
                JSONObject json = new JSONObject();
                json.put("fromChatterId", from);
                json.put("fromChatterName", chatterService.selectById(from).getName());
                // 占用成功, 向对方发送视频请求
                serverService.sendResponse(to, VideoWSResponse
                        .requestVideoOn("视频请求", json));
            } else {
                // 回滚占用的对方线路
                chatterService.changeVideoState(to, VideoStateEnum.FREE.getCode());
            }

        } else {
            // 线路已经被占用，返回错误
            serverService.sendResponse(from, VideoWSResponse
                    .failed("对方线路已经被占用", null));
            return;
        }
    }

    public synchronized void handleVideoOff(String from, String to, JSONObject data) {
        // 不存在会话，无法挂断
        if (null == sessionFactory.selectVideoSession(from) || null == sessionFactory.selectVideoSession(to)) {
            serverService.sendResponse(from, WSResponse.exception("不存在视频会话", null));
            return;
        }
        // 查找对应chatter
        ChatterDTO toChatter = chatterService.selectById(to);
        checkVideoChannelState(toChatter, from);
        ChatterDTO fromChatter = chatterService.selectById(from);
        if (toChatter.getVideoState().get() != VideoStateEnum.OCCUPY.getCode()  ||
                fromChatter.getVideoState().get() != VideoStateEnum.OCCUPY.getCode()) {
            serverService.sendResponse(from, WSResponse.exception("不在通话中，无法挂断", null));
            return;
        }
        // 改变双方状态
        chatterService.changeVideoState(to,VideoStateEnum.FREE.getCode());
        chatterService.changeVideoState(from, VideoStateEnum.FREE.getCode());
        // 删除video-session
        sessionFactory.removeVideoSession(from);
        // 释放连接成功
        serverService.sendResponse(to, VideoWSResponse
                .requestVideoOff("挂断视频", null));

    }

    /**
     * 检查双方状态能否进行通话
     * @param toChatter 目标用户
     * @param from 发起用户
     */
    private void checkVideoChannelState(ChatterDTO toChatter, String from) {
        if (null == toChatter) {
            // 向目标客户端发送异常消息
            serverService.sendResponse(from, VideoWSResponse
                    .failed("不存在视频请求的对象", null));
            return;
        }
        // 判断chatter是否在线
        if (toChatter.getStatus() != ChatterStatusEnum.ONLINE.getCode()) {
            serverService.sendResponse(from, VideoWSResponse.failed("对方不在线", null));
            return;
        }
    }

    private void handleVideoStateChange(String from, String to, JSONObject data) {
        // 信令交换
        serverService.sendResponse(to, VideoWSResponse.requestVideoStateChange("视频状态改变", data));
    }
}
