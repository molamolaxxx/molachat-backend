package com.mola.molachat.robot.creator;

import com.mola.molachat.config.AppConfig;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.ChatterTagEnum;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-06-10 23:05
 **/
@Component
public class ChatGptRobotCreator implements RobotCreator{

    @Resource
    private AppConfig appConfig;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public RobotChatter create() {
        RobotChatter robot = new RobotChatter();
        robot.setId(matchedAppKey());
        robot.setName("turbo3.5x");
        robot.setSignature("我是一个24岁的学生");
        robot.setStatus(ChatterStatusEnum.ONLINE.getCode());
        robot.setTag(ChatterTagEnum.ROBOT.getCode());
        robot.setImgUrl("img/mola2.jpg");
        robot.setIp("127.0.0.1");
        robot.setAppKey(matchedAppKey());
        robot.setApiKey(appConfig.getRobotApiKey().get(matchedAppKey()));
        robot.setEventBusBeanName("chatGptRobotEventBus");
        return (RobotChatter) chatterFactory.create(robot);
    }

    @Override
    public String matchedAppKey() {
        return "chatGpt";
    }
}
