package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.utils.TranslateUtils;
import org.springframework.stereotype.Component;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-09-12 16:26
 **/
@Component
public class TranslateCmdHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "trans";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            String commandInput = baseEvent.getCommandInput();
            if (TranslateUtils.isEnglish(commandInput)) {
                return TranslateUtils.translate("en","zh-CN", baseEvent.getCommandInput());
            }
            return TranslateUtils.translate("zh-CN","en", baseEvent.getCommandInput());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer order() {
        return 0;
    }

    @Override
    public String getDesc() {
        return "翻译服务";
    }
}
