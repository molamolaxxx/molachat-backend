package com.mola.molachat.service.impl;

import com.mola.molachat.data.ChatterFactoryInterface;
import com.mola.molachat.entity.Chatter;
import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.RobotChatter;
import com.mola.molachat.service.RobotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-12-07 15:58
 **/
@Service
@Slf4j
public class RobotServiceImpl implements RobotService {

    @Resource
    private ChatterFactoryInterface chatterFactory;

    @Override
    public void onReceiveMessage(Message message, String sessionId, RobotChatter robot) {
        if ("common-session".equals(sessionId)) {
            log.info("机器人收到群聊文件消息，内容 = {}", message.getContent());
        } else {
            log.info("机器人收到单聊文件消息，内容 = {}", message.getContent());
        }
    }

    @Override
    public Boolean isRobot(String chatterId) {
        if (StringUtils.isEmpty(chatterId)) {
            return false;
        }
        final Chatter chatter = chatterFactory.select(chatterId);
        if (null == chatter) {
            return false;
        }
        return chatter instanceof RobotChatter;
    }
}
