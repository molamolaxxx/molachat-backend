package com.mola.molachat.schedule;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.robot.handler.impl.cmd.OcrCmdHandler;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-04-30 10:22
 **/
@Configuration
@EnableScheduling
@Slf4j
public class TokenRefreshScheduleTask {


    /**
     * 刷新token
     */
    @Scheduled(initialDelay = 0, fixedRate = 24 * 3600 * 7)
    private void refreshOcrToken() throws Exception {
        HttpService instance = HttpService.INSTANCE;
        String url = String.format("https://aip.baidubce.com/oauth/2.0/token?client_id=%s&client_secret=%s&grant_type=client_credentials",
                "n6wIwRQXbEO41SPUxU2pWh5L",
                "cpcb2P7T1knDb4ZZvqxUOR6qErDvVK4i");

        // headers
        List<Header> headers = new ArrayList<>();
        headers.add(new BasicHeader("Content-Type", "application/json"));
        headers.add(new BasicHeader("Accept", "application/json"));
        String result = instance.post(
                url, null, 2000, headers.toArray(new Header[]{})
        );
        JSONObject jsonObject = JSONObject.parseObject(result);
        if (jsonObject.containsKey("access_token")) {
            OcrCmdHandler.TokenHolder.refresh(jsonObject.getString("access_token"));
        }
    }
}
