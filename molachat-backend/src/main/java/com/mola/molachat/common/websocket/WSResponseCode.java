package com.mola.molachat.common.websocket;

/**
 * @Author: molamola
 * @Date: 19-8-8 下午3:51
 * @Version 1.0
 * websocket传递消息码
 */
public class WSResponseCode {

    /**
     * 发送消息
     */
    public static final int MESSAGE = 65;

    /**
     * 传递异常
     */
    public static final int EXCEPTION = 368;

    /**
     * 传递联系人列表
     */
    public static final int LIST = 809;

    /**
     * 创建session
     */
    public static final int CREATE_SESSION = 122;

    /**
     * 视频响应
     */
    public static final int VIDEO_RESPONSE = 379;
}
