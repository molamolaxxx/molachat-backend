package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mola.molachat.chatter.dto.ChatterDTO;
import com.mola.molachat.chatter.service.ChatterService;
import com.mola.molachat.common.utils.HttpUtil;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.InvokeLimiter;
import com.mola.molachat.robot.model.KeyValue;
import io.jsonwebtoken.lang.Assert;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

/**
 * @author : molamola
 * @Project: InvincibleSchedulerEngine
 * @Description:
 * @date : 2023-07-22 22:30
 **/
@Service
@Slf4j
public class ChatGptSolution {

    private final Map<String, GptInvokeFuture> gptInvokeFutureMap = Maps.newConcurrentMap();

    @Resource
    private CmdProxyInvokeSolution cmdProxyInvokeSolution;

    @Resource
    private ChatterService chatterService;

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Resource
    private KvUtils kvUtils;

    @Getter
    private final Map<String, InvokeLimiter> limiters = Maps.newHashMap();

    @PostConstruct
    public void init() {
        String limiterStr = kvUtils.getString("chatGptLimiters");
        if (StringUtils.isNotBlank(limiterStr)) {
            for (String s : limiterStr.split(";")) {
                String[] split = s.split(":");
                if (split.length != 2) {
                    continue;
                }
                limiters.put(split[0], new InvokeLimiter(Integer.parseInt(split[1])));
            }
        }
    }

    /**
     * 调用chatgpt
     * @param input
     * @return
     */
    public String invoke(String input) {
        return invoke(input, null, false,null, null, null, null);
    }

    public String invoke(String input, String systemPrompt, boolean useStream,
                         Map<String, Object> streamOptions,
                         Function<String, Boolean> responseConsumer, Double temperature, String sessionId) {
        ChatterDTO chatGptChatter = chatterService.selectById("chatGpt");
        Assert.notNull(chatGptChatter, "chatGpt robot is null");
        Assert.isTrue(chatGptChatter.isRobot(), "chatGpt robot is not robot");
        String virtualChatterId = String.format("%s_%s", "system", UUID.randomUUID());

        String result = null;
        try {
            JSONObject body = new JSONObject();
            String modelName = findModelName(sessionId, "chatGpt");

            InvokeLimiter invokeLimiter = limiters.get(modelName);
            if (invokeLimiter != null) {
                invokeLimiter.tryAcquire();
            }

            log.info("ChatGptSolution 当前使用模型:{}", modelName);
            body.put("model", modelName);
            List<Map<String, String>> prompt = getInvokePrompt(input, systemPrompt);
            log.info(JSONObject.toJSONString(prompt));
            body.put("messages", prompt);
            body.put("stream", useStream);
            body.put("max_tokens", 32000);
            if (temperature != null) {
                body.put("temperature", temperature);
            }
            if (streamOptions != null) {
                body.put("stream_options", streamOptions);
            }
            // 关闭思维链
            Map<String, String> thinkMode = Maps.newHashMap();
            thinkMode.put("type", "disabled");
            body.put("thinking", thinkMode);

            // headers
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("Content-Type", "application/json"));
            headers.add(new BasicHeader("Authorization", "Bearer " + findApiKey(sessionId, chatGptChatter)));

            String modelUrl = findModelUrl(sessionId, "chatGpt");
            // 非流
            if (!useStream) {
                String res = HttpUtil.INSTANCE.post(modelUrl, body, 300000, headers.toArray(new Header[]{}));
                log.info("ChatGptSolution invoke, body = {}, res = {}", body, res);
                return parseResult(res);
            }

            // 流式处理
            HttpUtil.INSTANCE.postWithStreamRes(modelUrl, body, 300000,
                    headers.toArray(new Header[]{}), responseConsumer);
            return null;
        } catch (InterruptedException e) {
            throw new RuntimeException("请求超时");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            gptInvokeFutureMap.remove(virtualChatterId);
        }
    }


    private String findModelName(String sessionId, String robotId) {
        String modelName = kvUtils.getString("chatGptModelName_" + sessionId);
        if (StringUtils.isBlank(modelName)) {
            modelName = kvUtils.getString("chatGptModelName_" + robotId);
        }
        return modelName;
    }

    private String findModelUrl(String sessionId, String robotId) {
        String res = kvUtils.getString("modelUrl_" + sessionId);
        if (StringUtils.isBlank(res)) {
            res = kvUtils.getString("modelUrl_" + robotId);
        }
        return res;
    }


    private String findApiKey(String sessionId, ChatterDTO robotChatter) {
        String res = kvUtils.getString("chatGptApiKey_" + sessionId);
        if (StringUtils.isBlank(res)) {
            res = robotChatter.getApiKey();
        }
        return res;
    }

    /**
     * 获取chatgpt apikey
     * @return
     */
    public Set<String> fetchApiKeys() {
        KeyValue keyValue = keyValueFactory.selectOne("chatgptApiKeys");
        if (Objects.isNull(keyValue)) {
            return Sets.newHashSet();
        }
        String value = keyValue.getValue();
        return Sets.newHashSet(StringUtils.split(value, ";"));
    }

    public void removeApiKey(String apiKey) {
        KeyValue keyValue = keyValueFactory.selectOne("chatgptApiKeys");
        if (Objects.isNull(keyValue)) {
            return;
        }
        String value = keyValue.getValue();
        Set<String> keys = Sets.newHashSet(StringUtils.split(value, ";"));
        keys.remove(apiKey);
        keyValue.setValue(String.join( ";", keys));
        keyValueFactory.save(keyValue);
    }

    public static String parseResult(String result) {
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

    public static String parseStreamContent(String result, String contentKeyName) {
        JSONObject jsonObject = JSONObject.parseObject(result);
        Assert.isTrue(jsonObject.containsKey("choices"), "choices is empty");
        JSONArray choices = jsonObject.getJSONArray("choices");
        for (Object choice : choices) {
            JSONObject inner = (JSONObject) choice;
            JSONObject delta = inner.getJSONObject("delta");
            return delta.getString(contentKeyName);
        }
        return StringUtils.EMPTY;
    }

    public static boolean isStreamResultStop(String result) {
        JSONObject jsonObject = JSONObject.parseObject(result);
        Assert.isTrue(jsonObject.containsKey("choices"), "choices is empty");
        JSONArray choices = jsonObject.getJSONArray("choices");
        for (Object choice : choices) {
            JSONObject inner = (JSONObject) choice;
            return Objects.equals(inner.getString("finish_reason"), "stop");
        }
        return false;
    }

    public static boolean hasUsage(String result) {
        JSONObject jsonObject = JSONObject.parseObject(result);
        return jsonObject.get("usage") != null;
    }

    public static int queryTokenNum(String result, String key) {
        JSONObject jsonObject = JSONObject.parseObject(result);
        if (jsonObject.get("usage") != null) {
            JSONObject usage = jsonObject.getJSONObject("usage");
            if (usage.get(key) != null) {
                return usage.getIntValue(key);
            }
        }
        return 0;
    }

    public static int queryCachedTokenNum(String result) {
        JSONObject jsonObject = JSONObject.parseObject(result);
        if (jsonObject.get("usage") != null) {
            JSONObject usage = jsonObject.getJSONObject("usage");
            if (usage.get("cached_tokens") != null) {
                return usage.getIntValue("cached_tokens");
            }
            if (usage.get("prompt_tokens_details") != null) {
                JSONObject promptTokensDetails = usage.getJSONObject("prompt_tokens_details");
                return promptTokensDetails.getIntValue("cached_tokens");
            }
        }
        return 0;
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

    private static List<Map<String, String>> getInvokePrompt(String input, String systemPrompt) {
        Map<String, String> sysLine = Maps.newHashMap();
        sysLine.put("role", "system");
        sysLine.put("content", systemPrompt);

        Map<String, String> line = Maps.newHashMap();
        line.put("role", "user");
        line.put("content", input);
        return StringUtils.isNotBlank(systemPrompt) ?
                Lists.newArrayList(sysLine, line) : Lists.newArrayList(line);
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
