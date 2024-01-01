package com.mola.molachat.server.websocket;

/**
 * @Author: molamola
 * @Date: 19-8-8 下午3:58
 * @Version 1.0
 * 定义客户端发送到服务端的action
 */
public class ActionCode {

    /**
     * 发送消息
     */
    public static final int SEND_MESSAGE = 595;

    /**
     * 创建session
     */
    public static final int CREATE_SESSION = 220;

    /**
     * 发送心跳
     */
    public static final int HEART_BEAT = 276;

    /**
     * 视频请求
     */
    public static final int VIDEO_REQUEST = 378;

    /**
     * 无action
     */
    public static final int NOP = -1;



}
