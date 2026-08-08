package com.mola.molachat.server.websocket;

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
     * 发送流式消息
     */
    public static final int STREAM_MESSAGE = 66;

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

    /**
     * ws心跳返回
     */
    public static final int HEART_BEAT = 277;

    /**
     * 命令二次确认弹窗
     */
    public static final int CMD_CONFIRM = 488;

    /**
     * 普通ACP内部会话已切换
     */
    public static final int ACP_SESSION_CHANGED = 489;
}
