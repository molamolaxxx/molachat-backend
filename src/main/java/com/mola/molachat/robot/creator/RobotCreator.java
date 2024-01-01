package com.mola.molachat.robot.creator;

import com.mola.molachat.chatter.model.RobotChatter;

public interface RobotCreator {

    RobotChatter create();

    String matchedAppKey();
}
