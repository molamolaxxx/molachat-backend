package com.mola.molachat.service.app;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.molachat.common.constant.CmdProxyConstant;
import com.mola.molachat.config.AppConfig;
import com.mola.molachat.data.OtherDataInterface;
import com.mola.molachat.robot.handler.impl.ChatGptRobotHandler;
import com.mola.molachat.service.RobotService;
import kotlin.Unit;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-08-27 00:01
 **/
@Service
public class CmdProxyCallbackAppService implements InitializingBean {

    @Resource
    private RobotService robotService;

    @Resource
    private OtherDataInterface otherDataInterface;

    @Resource
    private GptTurboInvokeService gptTurboInvokeService;

    @Resource
    private AppConfig appConfig;


    @Override
    public void afterPropertiesSet() throws Exception {
        if (!appConfig.getUseCmdProxy()) {
            return;
        }
        // chatgpt
        registerChatgptCallback();
    }

    public void registerChatgptCallback() {
        CmdSender.INSTANCE.registerCallback(CmdProxyConstant.CHAT_GPT, CmdProxyConstant.CHAT_GPT, (res) -> {
            Map<String, String> resultMap = res.getResultMap();
            boolean exception = resultMap.containsKey("exception");
            String toChatterId = resultMap.get("toChatterId");
            String appKey = resultMap.get("appKey");
            String result = resultMap.get("result");
            String apiKey = resultMap.get("apiKey");

            if (resultMap.size() == 0) {
                return Unit.INSTANCE;
            }

            if (StringUtils.startsWith(toChatterId, "system")) {
                gptTurboInvokeService.callback(toChatterId, result, exception);
                return Unit.INSTANCE;
            }

            if (exception) {
                if (StringUtils.containsIgnoreCase(result, "You exceeded your current quota")
                        || StringUtils.containsIgnoreCase(result, "Incorrect API key provided")) {
                    final String usedAppKeyFinal = apiKey;
                    otherDataInterface.operateGpt3ChildTokens((tokens) -> tokens.remove(usedAppKeyFinal));
                }
                robotService.pushMessage(appKey, toChatterId, ChatGptRobotHandler.PROXY_ERROR);
                return Unit.INSTANCE;
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
            return Unit.INSTANCE;
        });
    }
}
