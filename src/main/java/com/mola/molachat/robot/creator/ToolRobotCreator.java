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
public class ToolRobotCreator implements RobotCreator{

    @Resource
    private AppConfig appConfig;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public RobotChatter create() {
        RobotChatter robot = new RobotChatter();
        robot.setId(matchedAppKey());
        robot.setName("工具人胡桃");
        robot.setSignature("我是一个可爱的工具人");
        robot.setStatus(ChatterStatusEnum.ONLINE.getCode());
        robot.setTag(ChatterTagEnum.ROBOT.getCode());
        robot.setImgUrl("https://img.clinicmed.net/uploadimg/image/20210223/16140627506034a49e61bcb2.79408198.jpeg");
        robot.setIp("127.0.0.1");
        robot.setAppKey(matchedAppKey());
        robot.setApiKey(appConfig.getRobotApiKey().get(matchedAppKey()));
        robot.setEventBusBeanName("robotEventBus");
        return (RobotChatter) chatterFactory.create(robot);
    }

    @Override
    public String matchedAppKey() {
        return "toolRobot";
    }
}
