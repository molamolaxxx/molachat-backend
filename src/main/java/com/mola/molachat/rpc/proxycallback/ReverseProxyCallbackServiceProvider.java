package com.mola.molachat.rpc.proxycallback;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.data.OtherDataInterface;
import com.mola.molachat.robot.handler.impl.ChatGptRobotHandler;
import com.mola.molachat.rpc.client.ReverseProxyCallbackService;
import com.mola.molachat.service.RobotService;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 代理回调，执行在服务端
 * @date : 2023-04-09 13:34
 **/
@Component
public class ReverseProxyCallbackServiceProvider implements ReverseProxyCallbackService {

    @Resource
    private RobotService robotService;

    @Resource
    private OtherDataInterface otherDataInterface;

    @Override
    public void chatGptCallback(String result, String toChatterId, String appKey, boolean exception, String apiKey) {
        if (exception) {
            if (StringUtils.containsIgnoreCase(result, "You exceeded your current quota")
                    || StringUtils.containsIgnoreCase(result, "Incorrect API key provided")) {
                final String usedAppKeyFinal = apiKey;
                otherDataInterface.operateGpt3ChildTokens((tokens) -> tokens.remove(usedAppKeyFinal));
            }
            robotService.pushMessage(appKey, toChatterId, ChatGptRobotHandler.PROXY_ERROR);
            return;
        }
        JSONObject jsonObject = JSONObject.parseObject(result);
        Assert.isTrue(jsonObject.containsKey("choices"), "choices is empty");
        JSONArray choices = jsonObject.getJSONArray("choices");
        for (Object choice : choices) {
            JSONObject inner = (JSONObject) choice;
            JSONObject object = inner.getJSONObject("message");
            String text = object.getString("content");
            if (StringUtils.isBlank(text)) {
                continue;
            }
            if (text.startsWith("\n")) {
                text = text.substring(1);
            }
            robotService.pushMessage(appKey, toChatterId, text);
        }
        return;
    }
}
