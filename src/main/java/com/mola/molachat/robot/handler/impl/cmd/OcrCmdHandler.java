package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.common.model.ResponseCode;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.robot.solution.OcrSolution;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.service.SessionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: ocr
 * @date : 2022-09-12 16:26
 **/
@Component
@Slf4j
public class OcrCmdHandler extends BaseCmdRobotHandler {

    private static final String NO_IMAGE = "没有可识别的图片";
    private static final String NO_RESULT = "没有匹配的结果";
    private static final String OCR_EXCEPTION = "orc识别异常";


    @Resource
    private SessionService sessionService;

    @Resource
    private OcrSolution ocrSolution;

    @Override
    public String getCommand() {
        return "ocr";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            MessageReceiveEvent messageReceiveEvent = baseEvent.getMessageReceiveEvent();
            String sessionId = messageReceiveEvent.getSessionId();
            SessionDTO session = sessionService.findSession(sessionId);
            Assert.notNull(session, "session is null in OcrCmdHandler，" + sessionId);
            List<Message> messageList = session.getMessageList();
            if (CollectionUtils.isEmpty(messageList) || messageList.size() == 1) {
                return NO_IMAGE;
            }
            Message message = messageList.get(messageList.size() - 2);
            if (!(message instanceof FileMessage)) {
                return NO_IMAGE;
            }
            FileMessage fm = (FileMessage) message;
            ServerResponse<String> ocrResult = ocrSolution.ocr(fm);
            if (ocrResult.getStatus() != ResponseCode.SUCCESS.getCode()) {
                return ocrResult.getMsg();
            }
            String ocrText = ocrResult.getData();
            if (StringUtils.isBlank(ocrText)) {
                return NO_RESULT;
            }
            return ocrText;
        } catch (Exception e) {
            log.error("OcrCmdHandler exception !" + baseEvent.getSessionId(), e);
            return OCR_EXCEPTION;
        }
    }

    @Override
    public Integer order() {
        return 0;
    }

    @Override
    public String getDesc() {
        return "图片文字识别";
    }

    @Override
    public List<CmdDescription> cmdDescriptions(String robotId, String sessionId) {
        return CmdDescription.builder()
                .cmdName("ocr")
                .cmdDesc("图片文字提取")
                .executeScript("sendMessageInner('ocr')")
                .buildSingleton();
    }
}
