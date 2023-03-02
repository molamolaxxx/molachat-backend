package com.mola.molachat.config;

import com.mola.molachat.common.constant.SessionConstant;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.enumeration.ChatterStatusEnum;
import com.mola.molachat.enumeration.ChatterTagEnum;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.utils.BeanUtilsPlug;
import org.apache.commons.lang.StringUtils;
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
public class RobotConfig {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Resource
    private SessionService sessionService;

    @Resource
    private AppConfig appConfig;

    @PostConstruct
    public void initRobot() {
        String robotList = appConfig.getRobotList();
        if (StringUtils.isNotEmpty(robotList)) {
            for (String appKey : robotList.split(",")) {
                addRobot(appKey);
            }
        }
    }

    /**
     * 加一个机器人
     * @param appKey
     */
    private void addRobot(String appKey) {
        Chatter chatter = chatterFactory.select(appKey);
        RobotChatter robot = null;
        if (null == chatter) {
            robot = new RobotChatter();
            robot.setId(appKey);
            robot.setName("翻斗鱼");
            robot.setSignature("我是一个24岁的学生");
            robot.setStatus(ChatterStatusEnum.ONLINE.getCode());
            robot.setTag(ChatterTagEnum.ROBOT.getCode());
            robot.setImgUrl("img/mola2.jpg");
            robot.setIp("127.0.0.1");
            robot.setAppKey(appKey);
            robot.setApiKey(appConfig.getRobotApiKey().get(appKey));
            robot.setEventBusBeanName("chatGptRobotEventBus");
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
