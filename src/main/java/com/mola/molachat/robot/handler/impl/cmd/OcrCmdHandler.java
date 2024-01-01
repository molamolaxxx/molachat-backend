package com.mola.molachat.robot.handler.impl.cmd;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.session.model.FileMessage;
import com.mola.molachat.session.model.Message;
import com.mola.molachat.session.dto.SessionDTO;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.event.MessageReceiveEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.session.service.SessionService;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.FileUtils;
import com.mola.molachat.common.utils.OcrHttpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.net.URLEncoder;
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
    private static final String FILE_IS_NOT_IMAGE = "ocr不支持非图片类型";
    private static final String NO_RESULT = "没有匹配的结果";
    private static final String FILE_IS_NOT_EXIST = "文件不存在";
    private static final String OCR_EXCEPTION = "orc识别异常";
    private static final String QUERY_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";

    public static class TokenHolder {

        private static String accessToken;

        public static void refresh(String accessToken) {
            TokenHolder.accessToken = accessToken;
        }
    }


    @Resource
    private SessionService sessionService;

    @Resource
    private SelfConfig selfConfig;

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
            if (!FileUtils.isImage(fm.getFileName())) {
                return FILE_IS_NOT_IMAGE;
            }
            String fPath = selfConfig.getUploadFilePath() + File.separator + fm.fetchRealStoredFileName(false);
            File file = new File(fPath);
            if (!file.exists()) {
                return FILE_IS_NOT_EXIST;
            }
            byte[] imgData = FileUtils.readFileByBytes(fPath);
            String imgStr = Base64Util.encode(imgData);
            String imgParam = URLEncoder.encode(imgStr, "UTF-8");
            String param = "image=" + imgParam;
            String result = OcrHttpUtil.post(QUERY_OCR_URL, TokenHolder.accessToken,
                    param);
            return getOcrResult(result);
        } catch (Exception e) {
            log.error("OcrCmdHandler exception !" + baseEvent.getSessionId(), e);
            return OCR_EXCEPTION;
        }
    }

    private String getOcrResult(String result) {
        StringBuilder stringBuilder = new StringBuilder();
        JSONObject jsonObject = JSONObject.parseObject(result);
        JSONArray wordsResult = jsonObject.getJSONArray("words_result");
        if (CollectionUtils.isEmpty(wordsResult)) {
            return NO_RESULT;
        }
        List<String> allLines = Lists.newArrayList();
        for (Object o : wordsResult) {
            JSONObject json = (JSONObject) o;
            String words = json.getString("words");
            allLines.add(words);
        }
        return String.join("\n", allLines);
    }

    @Override
    public Integer order() {
        return 0;
    }

    @Override
    public String getDesc() {
        return "图片文字识别";
    }
}
