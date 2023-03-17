package com.mola.molachat.rpc.client;

import com.mola.molachat.common.ServerResponse;

/**
 * 需要反向代理的方法
 */
public interface ImageGenerateService {

    /**
     * 服务端提交任务到执行端点
     * @param prompt
     * @param isWideImage
     * @return
     */
    ServerResponse submitTask(String prompt, boolean isWideImage, String sessionId);

    ServerResponse<String> getRes(String sessionId);
}
