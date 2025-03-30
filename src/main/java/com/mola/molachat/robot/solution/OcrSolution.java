package com.mola.molachat.robot.solution;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.mola.molachat.common.config.SelfConfig;
import com.mola.molachat.common.model.ServerResponse;
import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.FileUtils;
import com.mola.molachat.common.utils.OcrHttpUtil;
import com.mola.molachat.session.model.FileMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.File;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-03-29 10:24
 **/
@Component
@Slf4j
public class OcrSolution {

    @Resource
    private SelfConfig selfConfig;

    private static final String NO_IMAGE = "没有可识别的图片";
    private static final String FILE_IS_NOT_IMAGE = "ocr不支持非图片类型";
    private static final String NO_RESULT = "没有匹配的结果";
    private static final String FILE_IS_NOT_EXIST = "文件不存在";
    private static final String OCR_EXCEPTION = "orc识别异常";
    private static final String QUERY_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";


    public ServerResponse<String> ocr(FileMessage fm) {
        try {
            if (!FileUtils.isImage(fm.getFileName())) {
                return ServerResponse.createByErrorMessage(FILE_IS_NOT_IMAGE);
            }
            String fPath = selfConfig.getUploadFilePath() + File.separator + fm.fetchRealStoredFileName(false);
            File file = new File(fPath);
            if (!file.exists()) {
                return ServerResponse.createByErrorMessage(FILE_IS_NOT_EXIST);
            }
            byte[] imgData = FileUtils.readFileByBytes(fPath);
            String imgStr = Base64Util.encode(imgData);
            String imgParam = URLEncoder.encode(imgStr, "UTF-8");
            String param = "image=" + imgParam;
            String result = OcrHttpUtil.post(QUERY_OCR_URL, TokenHolder.accessToken,
                    param);
            return ServerResponse.createBySuccess(getOcrResult(result));
        } catch (Exception e) {
            log.error("OcrSolution.ocr exception", e);
            return ServerResponse.createByErrorMessage(e.getMessage());
        }
    }

    private String getOcrResult(String result) {
        StringBuilder stringBuilder = new StringBuilder();
        JSONObject jsonObject = JSONObject.parseObject(result);
        JSONArray wordsResult = jsonObject.getJSONArray("words_result");
        if (CollectionUtils.isEmpty(wordsResult)) {
            return "";
        }
        List<String> allLines = Lists.newArrayList();
        for (Object o : wordsResult) {
            JSONObject json = (JSONObject) o;
            String words = json.getString("words");
            allLines.add(words);
        }
        return String.join("\n", allLines);
    }


    public static class TokenHolder {

        private static String accessToken;

        public static void refresh(String accessToken) {
            TokenHolder.accessToken = accessToken;
        }
    }

}
