package com.mola.molachat.config;

import com.mola.molachat.common.constant.SessionConstant;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.ChatterTagEnum;
import com.mola.molachat.robot.bus.RobotEventBusRegistry;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.utils.BeanUtilsPlug;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-05 19:44
 **/
@Configuration
public class TestRobotConfig {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private RobotEventBusRegistry robotEventBusRegistry;

    @Resource
    private SessionService sessionService;

    private String testId = "robot1234";

    private String appKey = "robot1234";

    @PostConstruct
    public void initRobot() {
        Chatter chatter = chatterFactory.select(testId);
        RobotChatter robot = null;
        if (null == chatter) {
            robot = new RobotChatter();
            robot.setId(testId);
            robot.setName("测试机器人");
            robot.setSignature("我是一个测试机器人");
            robot.setStatus(ChatterStatusEnum.ONLINE.getCode());
            robot.setTag(ChatterTagEnum.ROBOT.getCode());
            robot.setImgUrl("img/header/6.jpeg");
            robot.setIp("127.0.0.1");
            robot.setAppKey(appKey);
            chatterFactory.create(robot);
        } else {
            robot = (RobotChatter) BeanUtilsPlug.copyPropertiesReturnTarget(chatter, new RobotChatter());
            robot.setAppKey(appKey);
            robot.setTag(ChatterTagEnum.ROBOT.getCode());
            chatterFactory.remove(chatter);
            chatterFactory.save(robot);
        }
        sessionService.findCommonAndGroupSession(robot.getId(), SessionConstant.COMMON_SESSION_ID);
    }
}
