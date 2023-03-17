package com.mola.molachat.rpc;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.rpc.client.ReverseProxyService;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-03-09 02:17
 **/
@Component
@Slf4j
public class ReverseProxyServiceProvider implements ReverseProxyService {

    @Override
    public ServerResponse<String> getChatGptResFromProxyServer(JSONObject body, String appKey) {
        try {
            // headers
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("Content-Type", "application/json"));
            headers.add(new BasicHeader("Authorization", "Bearer " + appKey));
            String res = HttpService.PROXY.post("https://api.openai.com/v1/chat/completions", body, 300000, headers.toArray(new Header[]{}));
            return ServerResponse.createBySuccess(res);
        } catch (Exception e) {
            log.error("getChatGptResFromProxyServer error, appKey = " + appKey, e);
            return ServerResponse.createByErrorMessage(e.getMessage());
        }
    }
}
