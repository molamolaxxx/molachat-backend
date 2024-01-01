package com.mola.molachat.robot.event;

import com.mola.molachat.chatter.model.RobotChatter;
import com.mola.molachat.common.event.event.BaseEvent;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:30
 **/
@Data
public class BaseRobotEvent extends BaseEvent {

    private RobotChatter robotChatter;

    private String sessionId;
}
