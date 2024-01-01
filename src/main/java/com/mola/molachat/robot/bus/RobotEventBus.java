package com.mola.molachat.robot.bus;

import com.mola.molachat.common.event.EventBus;
import com.mola.molachat.common.event.action.BaseAction;
import com.mola.molachat.robot.event.BaseRobotEvent;
import com.mola.molachat.robot.handler.IRobotEventHandler;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:23
 **/
@Component
public class RobotEventBus implements EventBus<BaseRobotEvent, BaseAction>, InitializingBean {

    @Resource
    private List<IRobotEventHandler> robotEventHandlers;

    @Override
    public BaseAction handler(BaseRobotEvent baseEvent) {
        BaseAction finalAction = BaseAction.empty();
        for (IRobotEventHandler robotEventHandler : getRobotEventHandlers()) {
            if (null == robotEventHandler.acceptEvent()) {
                continue;
            }
            if (robotEventHandler.acceptEvent().equals(baseEvent.getClass())) {
                BaseAction action = robotEventHandler.handler(baseEvent);
                if (action.getSkip()) {
                    continue;
                }
                finalAction = action;
                if (action.getFinalExec()) {
                    break;
                }
            }
        }
        return finalAction;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        List<IRobotEventHandler> robotEventHandlers = new ArrayList<>();
        for (IRobotEventHandler robotEventHandler : this.robotEventHandlers) {
            robotEventHandlers.add(robotEventHandler);
        }
        robotEventHandlers.sort(Comparator.comparing(IRobotEventHandler::order, Comparator.reverseOrder()));
        this.robotEventHandlers = robotEventHandlers;
    }

    protected List<IRobotEventHandler> getRobotEventHandlers() {
        return robotEventHandlers;
    }
}
