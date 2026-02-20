package com.mola.molachat.robot.handler.impl.cmd;

import com.mola.molachat.common.utils.Base64Util;
import com.mola.molachat.common.utils.OperatorUtils;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.robot.model.CmdDescription;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: base64解码
 * @date : 2022-09-12 16:26
 **/
@Component
public class EvalCmdHandler extends BaseCmdRobotHandler {

    @Override
    public String getCommand() {
        return "eval";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            return String.valueOf(OperatorUtils.operate(baseEvent.getCommandInput()));
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
        return "计算表达式";
    }


    @Override
    public List<CmdDescription> cmdDescriptions(String robotId, String sessionId) {
        return CmdDescription.builder()
                .cmdName("eval")
                .cmdDesc("计算表达式")
                .executeScript(String.format("popupAndSendCmd('eval','%s','计算表达式')",
                        Base64Util.encodeBase64("1 + (2 + 3)")))
                .buildSingleton();
    }
}
