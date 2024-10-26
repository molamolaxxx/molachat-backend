package com.mola.molachat.robot.handler.impl.cmd.gptpreset;

import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.robot.solution.ChatGptSolution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: gpt预存指令
 * @date : 2023-09-23 18:34
 **/
@Component
@Slf4j
public class GptPresetSaveHandler extends BaseCmdRobotHandler {

    @Resource
    private ChatGptSolution chatGptSolution;

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Override
    public String getCommand() {
        return "prompt";
    }

    @Override
    public String getDesc() {
        return "gpt预存模板存储，命令：prompt xxljob地址是https://xxxx";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            String[] splitRes = StringUtils.split(baseEvent.getCommandInput(), " ");
            if (null == splitRes || splitRes.length == 0) {
                return "命令格式错误";
            }
            String text = String.join(" ", splitRes);

            keyValueFactory.save(KeyValue.builder()
                    .owner(baseEvent.getMessageReceiveEvent().getMessage().getChatterId())
                    .desc("系统变量")
                    .share(false)
                    .key("prompt:" +
                            baseEvent.getMessageReceiveEvent().getMessage().getSessionId())
                    .value(text).build());

            return "gpt预存模板保存成功";
        } catch (Exception e) {
            log.error("gpt预存模板保存失败, input = " + baseEvent.getCommandInput(), e);
            return "gpt预存模板保存失败";
        }
    }


    @Override
    public Integer order() {
        return 0;
    }
}
