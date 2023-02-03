package com.mola.molachat.robot.bus;

import com.mola.molachat.robot.handler.IRobotEventHandler;
import com.mola.molachat.robot.handler.impl.Gpt3RobotHandler;
import com.mola.molachat.robot.handler.impl.RobotHeuristicHandler;
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
public class GptRobotEventBus extends RobotEventBus {

    @Resource
    private Gpt3RobotHandler gpt3RobotHandler;

    @Resource
    private RobotHeuristicHandler robotHeuristicHandler;

    protected List<IRobotEventHandler> getRobotEventHandlers() {
        return Arrays.asList(gpt3RobotHandler, robotHeuristicHandler);
    }
}
