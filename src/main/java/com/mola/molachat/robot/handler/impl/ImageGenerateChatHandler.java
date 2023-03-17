package com.mola.molachat.robot.handler.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.ResponseCode;
import com.mola.molachat.common.ServerResponse;
import com.mola.molachat.config.SelfConfig;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.robot.action.FileMessageSendAction;
import com.mola.molachat.robot.action.MessageSendAction;
import com.mola.molachat.robot.bus.ImageGenerateRobotEventBus;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.rpc.client.ImageGenerateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;

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

    @Resource
    private ImageGenerateService imageGenerateService;

    @Resource
    private ImageGenerateRobotEventBus imageGenerateRobotEventBus;

    @Override
    public MessageSendAction handler(MessageReceiveEvent messageReceiveEvent) {
        FileMessageSendAction messageSendAction = new FileMessageSendAction();
        try {
            String content = messageReceiveEvent.getMessage().getContent();
            RobotChatter robotChatter = messageReceiveEvent.getRobotChatter();
            Assert.notNull(robotChatter, "robotChatter is null");
            ServerResponse serverResponse = imageGenerateService.submitTask(content, false, messageReceiveEvent.getSessionId());
            if (serverResponse.getStatus() == ResponseCode.SUCCESS.getCode()) {
                String res = null;
                imageGenerateRobotEventBus.handler(
                        RobotHeuristicHandler.getHeuristicEvent(
                                "图片正在光速生成中，请耐心等待喔~", robotChatter, messageReceiveEvent.getSessionId()));
                while ((res = imageGenerateService.getRes(messageReceiveEvent.getSessionId()).getData()) == null) {
                    Thread.sleep(5000);
                }
                String fileName = RandomStringUtils.randomAlphabetic(3) + "_" + messageReceiveEvent.getSessionId() + ".jpg";
                String filePath = config.getUploadFilePath() + File.separator + fileName;
                byte[] imageBytes = Base64.getDecoder().decode(res);
                BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
                ImageIO.write(bufferedImage, "jpg", new File(filePath));
                messageSendAction.setUrl("files/" + fileName);
                messageSendAction.setFileName(fileName);
                imageGenerateRobotEventBus.handler(
                        RobotHeuristicHandler.getHeuristicEvent(
                                "图片已生成完成，请查收～", robotChatter, messageReceiveEvent.getSessionId()));
                return messageSendAction;
            }
            // 不可用告警
            MessageSendAction error = new MessageSendAction();
            error.setResponsesText(serverResponse.getMsg());
            return error;
        } catch (Exception e) {
            log.error("RemoteRobotChatHandler error " + JSONObject.toJSONString(messageReceiveEvent), e);
            // 不可用告警
            MessageSendAction error = new MessageSendAction();
            error.setResponsesText("图片加载失败，请稍后再试喔~");
            return error;
        }
    }

    @Override
    public Class<? extends BaseRobotEvent> acceptEvent() {
        return MessageReceiveEvent.class;
    }
}
