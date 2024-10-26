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
import com.mola.molachat.robot.model.KeyValue;
import io.jsonwebtoken.lang.Assert;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;

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

    /**
     * 调用chatgpt
     * @param input
     * @return
     */
    public String invoke(String input) {
        return invoke(input, null);
    }

    public String invoke(String input, String systemPrompt) {
        ChatterDTO chatGptChatter = chatterService.selectById("chatGpt");
        Assert.notNull(chatGptChatter, "chatGpt robot is null");
        Assert.isTrue(chatGptChatter.isRobot(), "chatGpt robot is not robot");
        String virtualChatterId = String.format("%s_%s", "system", UUID.randomUUID());
        String result = null;
        try {
            JSONObject body = new JSONObject();
            String modelName = kvUtils.getStringOrDefault("chatGptModelName", "Atom-13B-Chat");
            body.put("model", modelName);
            List<Map<String, String>> prompt = getInvokePrompt(input, systemPrompt);
            log.info(JSONObject.toJSONString(prompt));
            body.put("messages", prompt);
            body.put("stream", false);

            // headers
            List<Header> headers = new ArrayList<>();
            headers.add(new BasicHeader("Content-Type", "application/json"));
            headers.add(new BasicHeader("Authorization", "Bearer " + chatGptChatter.getApiKey()));
            String res = HttpUtil.INSTANCE.post("https://api.atomecho.cn/v1/chat/completions",
                    body, 300000, headers.toArray(new Header[]{}));
            return parseResult(res);
        } catch (InterruptedException e) {
            throw new RuntimeException("请求超时");
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            gptInvokeFutureMap.remove(virtualChatterId);
        }
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

    private List<Map<String, String>> getInvokePrompt(String input, String systemPrompt) {
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
