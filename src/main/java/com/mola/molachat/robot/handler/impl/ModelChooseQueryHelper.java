package com.mola.molachat.robot.handler.impl;

import com.google.common.collect.Lists;
import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.common.utils.KvUtils;
import com.mola.molachat.robot.model.CmdDescription;
import com.mola.molachat.session.model.Message;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-12-19 23:35
 **/
@Component
public class ModelChooseQueryHelper {

    public static final String MODEL_CHOOSE = "#modelChoose#-";

    @Resource
    private KvUtils kvUtils;

    public List<CmdDescription> getModelChoose(String robotId, String sessionId) {
        String modelChoose = kvUtils.getStringOrDefault("modelChoose_" + robotId, "");
        List<CmdDescription> descriptionList = Lists.newArrayList();
        if (StringUtils.isNotBlank(modelChoose)) {
            String currentModelName = findModelName(sessionId, robotId);
            String[] split = modelChoose.split("\n");
            for (int i = 0; i < split.length; i++) {
                String modelSetting = split[i];
                String[] options = modelSetting.split(";");
                String desc = Objects.equals(options[0], currentModelName) ? "切换模型:" + options[0] + "(当前使用)" : "切换模型:" + options[0];
                descriptionList.add(CmdDescription.builder()
                        .cmdName(MODEL_CHOOSE + i)
                        .cmdDesc(desc)
                        .executeScript("sendMessageInner('" + MODEL_CHOOSE + i + "')")
                        .build());
            }
        }
        return descriptionList;
    }

    public String findModelName(String sessionId, String robotId) {
        String modelName = kvUtils.getString("chatGptModelName_" + sessionId);
        if (StringUtils.isBlank(modelName)) {
            modelName = kvUtils.getString("chatGptModelName_" + robotId);
        }
        return modelName;
    }

    public void handlerModelChoose(Message message, RobotChatter robotChatter) {
        int idx = Integer.parseInt(message.getContent().replace(ModelChooseQueryHelper.MODEL_CHOOSE, ""));
        String modelChoose = kvUtils.getStringOrDefault("modelChoose_" + robotChatter.getId(), "");
        String[] options = modelChoose.split("\n")[idx].split(";");
        kvUtils.set("chatGptModelName_" + message.getSessionId(), options[0], robotChatter.getId());
        kvUtils.set("modelUrl_" + message.getSessionId(), options[1], robotChatter.getId());
        kvUtils.set("chatGptApiKey_" + message.getSessionId(), options[2], robotChatter.getId());
    }
}
