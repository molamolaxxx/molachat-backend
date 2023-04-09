package com.mola.molachat.rpc.client;

/**
 * 代理服务器获得回答后回调
 */
public interface ReverseProxyCallbackService {

    void chatGptCallback(String result, String toChatterId, String appKey, boolean exception, String apiKey);
}
