package com.mola.molachat.rpc.proxyservice;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.rpc.client.ReverseProxyCallbackService;
import com.mola.molachat.rpc.client.ReverseProxyService;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 执行在代理端
 * @date : 2023-03-09 02:17
 **/
@Component
@Slf4j
public class ReverseProxyServiceProvider implements ReverseProxyService {

    @Resource
    private ReverseProxyCallbackService reverseProxyCallbackService;

    @Override
    public ServerResponse<String> processChatGptRequestAndSendBackInProxy(JSONObject body, String apiKey, String toChatterId, String appKey) {
        try {
            // headers
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("Content-Type", "application/json"));
            headers.add(new BasicHeader("Authorization", "Bearer " + apiKey));
            CompletableFuture.runAsync (() -> {
                String res = null;
                try {
                    res = HttpService.PROXY.post("https://api.openai.com/v1/chat/completions", body, 300000, headers.toArray(new Header[]{}));
                    reverseProxyCallbackService.chatGptCallback(res, toChatterId, appKey, false, apiKey);
                } catch (Exception e) {
                    res = e.getMessage();
                    log.error("processChatGptRequestAndSendBackInProxy error in async task, apiKey = " + apiKey, e);
                    reverseProxyCallbackService.chatGptCallback(res, toChatterId, appKey, true, apiKey);
                }
            });
        } catch (Exception e) {
            log.error("processChatGptRequestAndSendBackInProxy error, apiKey = " + apiKey, e);
            return ServerResponse.createByErrorMessage(e.getMessage());
        }
        return ServerResponse.createBySuccess("ok");
    }
}
