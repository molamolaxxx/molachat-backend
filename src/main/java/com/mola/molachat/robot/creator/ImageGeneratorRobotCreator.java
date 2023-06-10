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
public class ImageGeneratorRobotCreator implements RobotCreator{

    @Resource
    private AppConfig appConfig;

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public RobotChatter create() {
        RobotChatter robot = new RobotChatter();
        robot.setId(matchedAppKey());
        robot.setName("小图师");
        robot.setSignature("请输入文字，我来生成图片");
        robot.setStatus(ChatterStatusEnum.ONLINE.getCode());
        robot.setTag(ChatterTagEnum.ROBOT.getCode());
        robot.setImgUrl("img/blue.jpg");
        robot.setIp("127.0.0.1");
        robot.setAppKey(matchedAppKey());
        robot.setApiKey(appConfig.getRobotApiKey().get(matchedAppKey()));
        robot.setEventBusBeanName("imageGenerateRobotEventBus");
        return (RobotChatter) chatterFactory.create(robot);
    }

    @Override
    public String matchedAppKey() {
        return "stableDiffusion";
    }
}
