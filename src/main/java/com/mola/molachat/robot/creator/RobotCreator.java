package com.mola.molachat.robot.creator;

import com.mola.molachat.entity.RobotChatter;

public interface RobotCreator {

    RobotChatter create();

    String matchedAppKey();
}
