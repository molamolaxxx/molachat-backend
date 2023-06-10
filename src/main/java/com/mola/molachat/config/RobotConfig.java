package com.mola.molachat.config;

import com.mola.molachat.common.constant.SessionConstant;
import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.enumeration.ChatterTagEnum;
import com.mola.molachat.robot.creator.RobotCreator;
import com.mola.molachat.service.SessionService;
import com.mola.molachat.utils.BeanUtilsPlug;
import org.apache.commons.lang.StringUtils;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Objects;

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

    @Resource
    private List<RobotCreator> robotCreators;

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
        // 如果存在，刷新机器人
        if (Objects.nonNull(chatter)) {
            RobotChatter robot = (RobotChatter) BeanUtilsPlug.copyPropertiesReturnTarget(chatter, new RobotChatter());
            robot.setAppKey(appKey);
            robot.setTag(ChatterTagEnum.ROBOT.getCode());
            chatterFactory.remove(chatter);
            chatterFactory.save(robot);
            sessionService.findCommonAndGroupSession(robot.getId(), SessionConstant.COMMON_SESSION_ID);
            return;
        }

        // 不存在，通过预设创建
        RobotCreator robotCreator = robotCreators.stream()
                .filter(c -> Objects.equals(appKey, c.matchedAppKey()))
                .findAny().orElse(null);
        if (Objects.isNull(robotCreator)) {
            return;
        }
        RobotChatter robotChatter = robotCreator.create();
        sessionService.findCommonAndGroupSession(robotChatter.getId(), SessionConstant.COMMON_SESSION_ID);

    }
}
