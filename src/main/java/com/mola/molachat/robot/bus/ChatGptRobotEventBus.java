package com.mola.molachat.robot.bus;

import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.handler.impl.ChatGptRobotHandler;
import com.mola.molachat.robot.handler.impl.RobotHeuristicHandler;
import com.mola.molachat.robot.handler.impl.cmd.StopChatStreamRobotHandler;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:openai chatgpt
 * @date : 2020-12-07 15:23
 **/
@Component
public class ChatGptRobotEventBus extends RobotEventBus {

    @Resource
    private ChatGptRobotHandler chatGptRobotHandler;

    @Resource
    private RobotHeuristicHandler robotHeuristicHandler;

    @Resource
    private StopChatStreamRobotHandler stopChatStreamRobotHandler;

    protected List<IRobotEventHandler> getRobotEventHandlers() {
        return Arrays.asList(stopChatStreamRobotHandler, chatGptRobotHandler, robotHeuristicHandler);
    }
}
