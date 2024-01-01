package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSONObject;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.cmd.proxy.client.resp.CmdInvokeResponse;
import com.mola.cmd.proxy.client.resp.CmdResponseContent;
import com.mola.molachat.robot.constant.CmdProxyConstant;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 代理命令处理器
 * @date : 2023-08-26 23:49
 **/
@Service
public class CmdProxyInvokeSolution {

    /**
     * 客户端代理服务端的chatgpt
     * @param body
     * @param toChatterId 发送请求的人
     * @return
     */
    public void sendChatGptRequestCmd(JSONObject body, String apiKey, String toChatterId, String appKey){
        String[] cmdArgs = new String[]{body.toJSONString(), apiKey, toChatterId, appKey};
        CmdInvokeResponse<CmdResponseContent> result = CmdSender.INSTANCE.send(CmdProxyConstant.CHAT_GPT,
                CmdProxyConstant.CHAT_GPT, cmdArgs);

        if (Objects.isNull(result) || Objects.isNull(result.getData())) {
            throw new RuntimeException("sendChatGptRequestCmd result is null");
        }
        if (!result.isSuccess()) {
            throw new RuntimeException("sendChatGptRequestCmd error accuracy, msg = " + result.getMsg());
        }
    }


    public void getImageGenerateTaskResult(String prompt, boolean isWideImage, String sessionId) {
        String[] cmdArgs = new String[]{prompt, sessionId};
        CmdInvokeResponse<CmdResponseContent> result = CmdSender.INSTANCE.send("submitTask",
                CmdProxyConstant.IMAGE_GENERATE, cmdArgs);

        if (Objects.isNull(result) || Objects.isNull(result.getData())) {
            throw new RuntimeException("submitTask result is null");
        }
        if (!result.isSuccess()) {
            throw new RuntimeException("submitTask error accuracy, msg = " + result.getMsg());
        }
    }
}
