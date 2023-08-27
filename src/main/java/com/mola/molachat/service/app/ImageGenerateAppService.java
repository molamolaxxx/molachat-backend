package com.mola.molachat.service.app;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.common.constant.CmdProxyConstant;
import com.mola.molachat.config.AppConfig;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 执行在代理端
 * @date : 2023-03-17 22:41
 **/
@Component
@Slf4j
public class ImageGenerateAppService implements  InitializingBean {

    @Resource
    private AppConfig appConfig;

    private BlockingDeque<ImageGenerateTask> taskQueue = new LinkedBlockingDeque<>(100);

    private Map<String, String> resMap = new ConcurrentHashMap<>();

    public ServerResponse submitTask(String prompt, boolean isWideImage, String sessionId) {
        if (appConfig.getUseCmdProxy()) {
            String[] cmdArgs = new String[]{prompt, sessionId};
            CmdInvokeResponse<CmdResponseContent> result = CmdSender.INSTANCE.send("submitTask",
                    CmdProxyConstant.IMAGE_GENERATE, cmdArgs);

            return ServerResponse.createBySuccess();
        }
        for (ImageGenerateTask imageGenerateTask : taskQueue) {
            if (imageGenerateTask.sessionId.equals(sessionId)) {
                return ServerResponse.createByErrorMessage("有任务正在运行，请稍后再提交喔~");
            }
        }
        taskQueue.push(new ImageGenerateTask(prompt, isWideImage, sessionId));
        return ServerResponse.createBySuccess();
    }

    public ServerResponse<String> getRes(String sessionId) {
        if (appConfig.getUseCmdProxy()) {
            String[] cmdArgs = new String[]{sessionId};
            CmdInvokeResponse<CmdResponseContent> result = CmdSender.INSTANCE.send("getResult",
                    CmdProxyConstant.IMAGE_GENERATE, cmdArgs);

            if (Objects.isNull(result) || Objects.isNull(result.getData())
                    || Objects.isNull(result.getData().resultMap)) {
                throw new RuntimeException("submitTask result is null");
            }
            if (!result.isSuccess()) {
                throw new RuntimeException("submitTask error accuracy, msg = " + result.getMsg());
            }
            return ServerResponse.createBySuccess(result.getData().resultMap.get("result"));
        }
        String res = resMap.get(sessionId);
        if (null != res) {
            resMap.remove(sessionId);
        }
        Assert.isTrue(!"error".equals(res), "任务执行失败");
        return ServerResponse.createBySuccess(res);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (appConfig.getUseCmdProxy()) {
            return;
        }
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
