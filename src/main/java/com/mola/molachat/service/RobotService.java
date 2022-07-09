package com.mola.molachat.service;

import com.mola.molachat.entity.Message;
import com.mola.molachat.entity.RobotChatter;

/**
 * 提供机器人操作的最外层服务
 */
public interface RobotService {

    /**
     * 收到外部的消息
     * @param message
     * @param sessionId
     * @param robot
     */
    void onReceiveMessage(Message message, String sessionId, RobotChatter robot);

    /**
     * 判断是否是robot
     * @param chatterId
     * @return
     */
    Boolean isRobot(String chatterId);

    /**
     * 获取机器人
     * @param appKey
     * @return
     */
    RobotChatter getRobot(String appKey);

    /**
     * 消息推送
     * @param appKey 发送方（机器人）
     * @param toChatterId 接收方
     * @param content 消息
     */
    void pushMessage(String appKey, String toChatterId, String content);
}
