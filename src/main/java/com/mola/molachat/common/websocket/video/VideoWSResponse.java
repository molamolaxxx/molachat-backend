package com.mola.molachat.common.websocket.video;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.websocket.WSResponse;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-19 02:22
 **/
public class VideoWSResponse {

    /**
     * 视频模块请求异常（响应）
     * @param msg 前端展示信息
     * @param ext 附带参数
     * @return
     */
    public static WSResponse<JSONObject> failed(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("msg", msg);
        ext.put("videoResponseType", VideoResponseCode.RESPONSE_FAILED);
        return WSResponse.videoResponse("视频通话出现错误", ext);
    }

    /**
     * 视频确认返回（响应）
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestVideoAccept(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoResponseType", VideoResponseCode.RESPONSE_ACCEPT);
        return WSResponse.videoResponse(msg, ext);
    }

    /**
     * 视频拒绝返回（响应）
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestVideoRefuse(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoResponseType", VideoResponseCode.RESPONSE_REFUSE);
        return WSResponse.videoResponse(msg, ext);
    }

    /**
     * 请求打开视频(请求)
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestVideoOn(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoRequestType", VideoRequestCode.REQUEST_VIDEO_ON);
        return WSResponse.videoRequest(msg, ext);
    }

    /**
     * 请求挂断视频(请求)
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestVideoOff(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoRequestType", VideoRequestCode.REQUEST_VIDEO_OFF);
        return WSResponse.videoRequest(msg, ext);
    }

    /**
     * 请求取消视频(请求)
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestVideoCancel(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoRequestType", VideoRequestCode.REQUEST_CANCEL);
        return WSResponse.videoRequest(msg, ext);
    }

    /**
     * 信令交换
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestSignalChange(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoRequestType", VideoRequestCode.SIGNALLING_CHANGE);
        return WSResponse.videoRequest(msg, ext);
    }

    /**
     * 视频状态改变
     * @param msg
     * @param ext
     * @return
     */
    public static WSResponse<JSONObject> requestVideoStateChange(String msg, JSONObject ext) {
        if (null == ext) {
            ext = new JSONObject();
        }
        ext.put("videoRequestType", VideoRequestCode.VIDEO_STATE_CHANGE);
        return WSResponse.videoRequest(msg, ext);
    }
}
