package com.mola.molachat.rpc.client;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.ServerResponse;

/**
 * 需要反向代理的方法
 */
public interface ReverseProxyService {

    /**
     * 客户端代理服务端的chatgpt
     * @param body
     * @return
     */
    ServerResponse<String> getChatGptResFromProxyServer(JSONObject body, String appKey);

}
