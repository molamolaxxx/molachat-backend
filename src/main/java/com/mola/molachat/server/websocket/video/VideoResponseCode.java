package com.mola.molachat.server.websocket.video;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-18 16:42
 **/
public class VideoResponseCode {

    public static String VIDEO_REQUEST_MSG = "response_video";

    /**
     * 请求失败响应
     */
    public static final int RESPONSE_FAILED = 2269;

    /**
     * 对方同意响应
     */
    public static final int RESPONSE_ACCEPT = 2270;

    /**
     * 对方拒绝响应
     */
    public static final int RESPONSE_REFUSE = 2271;

}
