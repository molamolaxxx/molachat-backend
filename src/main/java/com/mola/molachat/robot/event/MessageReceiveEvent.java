package com.mola.molachat.robot.event;

import com.mola.molachat.session.model.Message;
import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2022-08-27 11:29
 **/
@Data
public class MessageReceiveEvent extends BaseRobotEvent{

    private Message message;
}
