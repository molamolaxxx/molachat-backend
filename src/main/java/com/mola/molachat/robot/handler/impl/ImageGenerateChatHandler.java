package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.config.SelfConfig;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.robot.action.FileMessageSendAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.service.http.HttpService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 青云客远程聊天处理器，调用httpClient
 * @date : 2022-08-27 11:47
 **/
@Slf4j
@Component
public class ImageGenerateChatHandler implements IRobotEventHandler<MessageReceiveEvent, MessageSendAction> {

    @Resource
    private SelfConfig config;

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        FileMessageSendAction messageSendAction = new FileMessageSendAction();
        try {
            String content = messageReceiveEvent.getMessage().getContent();
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            Assert.notNull(robotChatter, "robotChatter is null");
            JSONObject body = new JSONObject();
            body.put("inputs", content);
            byte[] bytes = HttpService.INSTANCE.postReturnByte("https://api-inference.huggingface.co/models/IDEA-CCNL/Taiyi-Stable-Diffusion-1B-Anime-Chinese-v0.1",
                    body, 60000, new Header[]{new BasicHeader("Authorization", "Bearer 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")});
            String fileName = RandomStringUtils.randomAlphabetic(3) + "_" + messageReceiveEvent.getSessionId() + ".jpg";
            String url = config.getUploadFilePath() + File.separator + fileName;
            FileOutputStream outputStream = new FileOutputStream(url);
            outputStream.write(bytes);
            messageSendAction.setUrl("files/" + fileName);
            messageSendAction.setFileName(fileName);
            return messageSendAction;
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler error " + JSONObject.toJSONString(messageReceiveEvent), e);
            // 不可用告警
            MessageSendAction error = new MessageSendAction();
            error.setResponsesText("图片正在加载，请稍后再试喔~");
            return error;
        }
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }
}
