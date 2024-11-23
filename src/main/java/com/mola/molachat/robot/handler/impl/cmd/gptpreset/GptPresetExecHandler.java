package com.mola.molachat.robot.handler.impl.cmd.gptpreset;

import com.alibaba.nacos.common.utils.Objects;
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
public class GptPresetExecHandler extends BaseCmdRobotHandler {



    @Resource
    private ChatGptSolution chatGptSolution;

    @Resource
    private KeyValueFactoryInterface keyValueFactory;

    @Override
    public String getCommand() {
        return "gpt";
    }

    @Override
    public String getDesc() {
        return "gpt预存模板执行，命令：gpt xxljob地址是什么";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            String[] splitRes = StringUtils.split(baseEvent.getCommandInput(), " ");
            if (null == splitRes || splitRes.length == 0) {
                return "命令格式错误";
            }
            KeyValue keyValue = keyValueFactory.selectOne("prompt:" +
                    baseEvent.getMessageReceiveEvent().getMessage().getSessionId());
            if (Objects.isNull(keyValue)) {
                return "未找到gpt预存模板";
            }

            String text = String.join(" ", splitRes);
            return chatGptSolution.invoke(text, keyValue.getValue());
        } catch (Exception e) {
            log.error("gpt预存模板执行失败, input = " + baseEvent.getCommandInput(), e);
            return "gpt预存模板执行失败";
        }
    }


    @Override
    public Integer order() {
        return 0;
    }

    @Override
    public boolean isDefaultHandler() {
        return true;
    }
}
