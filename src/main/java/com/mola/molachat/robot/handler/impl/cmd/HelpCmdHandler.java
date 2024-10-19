package com.mola.molachat.robot.handler.impl.cmd;

import com.alibaba.nacos.common.utils.MapUtils;
import com.mola.cmd.proxy.client.consumer.CmdSender;
import com.mola.molachat.robot.event.CommandInputEvent;
import com.mola.molachat.robot.handler.impl.BaseCmdRobotHandler;
import com.mola.molachat.session.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: base64解码
 * @date : 2022-09-12 16:26
 **/
@Component
@Slf4j
public class HelpCmdHandler extends BaseCmdRobotHandler {

    @Resource
    private List<BaseCmdRobotHandler> cmdRobotHandlers;

    @Override
    public String getCommand() {
        return "help";
    }

    @Override
    protected String executeCommand(CommandInputEvent baseEvent) {
        try {
            StringBuilder stringBuilder = new StringBuilder();

            Message message = baseEvent.getMessageReceiveEvent().getMessage();
            Map<String, String> remoteCmdDescMap = CmdSender.INSTANCE.fetchDescriptionMap(message.getSessionId());
            if (MapUtils.isNotEmpty(remoteCmdDescMap)) {
                stringBuilder.append("------ remote command help ------").append("\n");
                remoteCmdDescMap.forEach((cmd, desc) -> {
                    stringBuilder.append(String.format("【%s】: %s", cmd, desc));
                    stringBuilder.append("\n");
                });
            }

            stringBuilder.append("------ local command help ------").append("\n");
            for (int i = 0; i < cmdRobotHandlers.size(); i++) {
                BaseCmdRobotHandler cmdRobotHandler = cmdRobotHandlers.get(i);
                stringBuilder.append(String.format("【%s】: %s", cmdRobotHandler.getCommand(), cmdRobotHandler.getDesc()));
                stringBuilder.append("\n");
            }
            return stringBuilder.toString();
        } catch (Exception e) {
            log.error("help exec failed", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer order() {
        return Integer.MAX_VALUE;
    }

    @Override
    public String getDesc() {
        return "帮助";
    }
}
