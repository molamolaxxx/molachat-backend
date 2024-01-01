package com.mola.molachat.server.encoder;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.server.websocket.WSResponse;

import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import javax.websocket.EndpointConfig;

/**
 * @Author: molamola
 * @Date: 19-8-9 上午11:12
 * @Version 1.0
 * 将ws传输的对象转化成流数据
 */
public class ServerEncoder implements Encoder.Text<WSResponse> {

    @Override
    public String encode(WSResponse wsResponse) throws EncodeException {
        String jsonObject = JSONObject.toJSONString(wsResponse);
        return jsonObject;
    }

    @Override
    public void init(EndpointConfig endpointConfig) {
    }

    @Override
    public void destroy() {
    }
}
