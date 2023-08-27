package com.mola.molachat.service.app;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.service.ChatterService;
import io.jsonwebtoken.lang.Assert;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author : molamola
 * @Project: InvincibleSchedulerEngine
 * @Description:
 * @date : 2023-07-22 22:30
 **/
@Service
public class GptTurboInvokeService {

    private final Map<String, GptInvokeFuture> gptInvokeFutureMap = Maps.newConcurrentMap();

    @Resource
    private CmdProxyInvokeAppService cmdProxyInvokeAppService;

    @Resource
    private ChatterService chatterService;

    public String invoke(String input) {
        ChatterDTO chatGptChatter = chatterService.selectById("chatGpt");
        Assert.notNull(chatGptChatter, "chatGpt robot is null");
        Assert.isTrue(chatGptChatter.isRobot(), "chatGpt robot is not robot");
        String virtualChatterId = String.format("%s_%s", "system", UUID.randomUUID());
        String result = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", "gpt-3.5-turbo");
            List<Map<String, String>> prompt = getInvokePrompt(input);
            body.put("messages", prompt);

            cmdProxyInvokeAppService.sendChatGptRequestCmd(body, chatGptChatter.getApiKey(),
                    virtualChatterId, chatGptChatter.getAppKey());

            GptInvokeFuture future = GptInvokeFuture.of();
            gptInvokeFutureMap.put(virtualChatterId, future);
            future.cdl.await(60L, TimeUnit.SECONDS);
            if (future.exception) {
                throw new RuntimeException(future.result);
            }
            return parseResult(future.result);
        } catch (InterruptedException e) {
            throw new RuntimeException("请求超时");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            gptInvokeFutureMap.remove(virtualChatterId);
        }
    }

    private String parseResult(String result) {
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
            return text;
        }
        return StringUtils.EMPTY;
    }

    public void callback(String virtualChatterId, String result, boolean exception) {
        if (!gptInvokeFutureMap.containsKey(virtualChatterId)) {
            return;
        }
        GptInvokeFuture future = gptInvokeFutureMap.get(virtualChatterId);
        future.exception = exception;
        future.result = result;
        future.cdl.countDown();
    }

    private List<Map<String, String>> getInvokePrompt(String input) {
        Map<String, String> line = Maps.newHashMap();
        line.put("role", "user");
        line.put("content", input);
        return Lists.newArrayList(line);
    }

    private static class GptInvokeFuture {
        CountDownLatch cdl;
        String result;
        boolean exception;

        public static GptInvokeFuture of() {
            GptInvokeFuture future = new GptInvokeFuture();
            future.cdl = new CountDownLatch(1);
            return future;
        }
    }
}
