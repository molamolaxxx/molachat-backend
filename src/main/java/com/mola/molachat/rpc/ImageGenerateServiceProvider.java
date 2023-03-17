package com.mola.molachat.rpc;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.rpc.client.ImageGenerateService;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-03-17 22:41
 **/
@Component
@Slf4j
public class ImageGenerateServiceProvider implements ImageGenerateService, InitializingBean {

    private BlockingDeque<ImageGenerateTask> taskQueue = new LinkedBlockingDeque<>(100);

    private Map<String, String> resMap = new ConcurrentHashMap<>();

    @Override
    public ServerResponse submitTask(String prompt, boolean isWideImage, String sessionId) {
        for (ImageGenerateTask imageGenerateTask : taskQueue) {
            if (imageGenerateTask.sessionId.equals(sessionId)) {
                return ServerResponse.createByErrorMessage("有任务正在运行，请稍后再提交喔~");
            }
        }
        taskQueue.push(new ImageGenerateTask(prompt, isWideImage, sessionId));
        return ServerResponse.createBySuccess();
    }

    @Override
    public ServerResponse<String> getRes(String sessionId) {
        String res = resMap.get(sessionId);
        if (null != res) {
            resMap.remove(sessionId);
        }
        Assert.isTrue(!"error".equals(res), "任务执行失败");
        return ServerResponse.createBySuccess(res);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        new Thread(
                () -> {
                    while (true) {
                        ImageGenerateTask task = null;
                        try {
                            task = taskQueue.poll(5000, TimeUnit.MILLISECONDS);
                        } catch (InterruptedException e) {
                        }
                        if (null != task) {
                            task.run();
                            if (task.base64String != null) {
                                resMap.put(task.sessionId, task.base64String);
                            }
                        }
                    }
                }
        ).start();
    }

    public class ImageGenerateTask implements Runnable {
        private String prompt;
        private boolean isWideImage;
        private String sessionId;
        private String base64String;

        public ImageGenerateTask(String prompt, boolean isWideImage, String sessionId) {
            Assert.notNull(prompt, "prompt is null");
            Assert.notNull(sessionId, "sessionId is null");
            this.prompt = prompt;
            this.isWideImage = isWideImage;
            this.sessionId = sessionId;
        }

        @Override
        public void run() {
            try {
                log.info("execute task: " + JSONObject.toJSONString(this));
                JSONObject body = new JSONObject();
                body.put("prompt", prompt);
                body.put("steps", 35);
                body.put("width", 512);
                body.put("height", 512);
                body.put("sampler_index", "Euler");
                String res =  HttpService.INSTANCE.post("http://127.0.0.1:12345/sdapi/v1/txt2img",
                        body, 600000, null);
                JSONObject jsonObject = JSONObject.parseObject(res);
                JSONArray array = jsonObject.getJSONArray("images");
                for (Object o : array) {
                    Assert.isTrue(o instanceof String, "o not instanceof String");
                    this.base64String = (String) o;
                    return;
                }
            } catch (Exception e) {
                log.error("ImageGenerateTask error, prompt = " + prompt, e);
                resMap.put(sessionId, "error");
            }
        }
    }
}
