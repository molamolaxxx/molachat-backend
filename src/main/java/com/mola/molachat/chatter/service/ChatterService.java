package com.mola.molachat.chatter.service;

import com.mola.molachat.session.model.Message;
import com.mola.molachat.chatter.dto.ChatterDTO;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * @Author: molamola
 * @Date: 19-8-6 下午4:17
 * @Version 1.0
 */
public interface ChatterService {

    /**
     * 创建用户
     * @param chatterDTO
     * @return
     */
    ChatterDTO create(ChatterDTO chatterDTO);

    /**
     * 保存用户
     * @param chatterDTO
     * @return
     */
    ChatterDTO save(ChatterDTO chatterDTO);

    /**
     * 更新用户信息
     * @param chatterDTO
     * @return
     */
    ChatterDTO update(ChatterDTO chatterDTO);

    /**
     * 更新机器人信息
     * @param chatterDTO
     * @return
     */
    ChatterDTO updateRobot(ChatterDTO chatterDTO);

    /**
     * @param chatterDTO
     * @return
     */
    ChatterDTO remove(ChatterDTO chatterDTO);

    /**
     * 列出当前chatter列表
     * @return
     */
    List<ChatterDTO> list();

    /**
     * 根据id查找chatter
     * @param chatterId
     * @return
     */
    ChatterDTO selectById(String chatterId);

    /**
     * 设置chatter的状态
     */
    void setChatterStatus(String chatterId, Integer status);

    /**
     *
     * @param chatterId
     * @param tag
     */
    void setChatterTag(String chatterId, Integer tag);

    /**
     * 获取消息队列
     */
    BlockingQueue<Message> getQueueById(String chatterId);

    /**
     * 消息队列入队
     */
    void offerMessageIntoQueue(Message message,String chatterId);

    /**
     * 添加分数
     */
    void addPoint(String id, Integer point);

    /**
     * cas改变videoState
     * @param pre
     * @param cur
     */
    boolean casVideoState(String chatterId, Integer pre, Integer cur);

    /**
     * 改变videoState
     * @param chatterId
     * @param state
     * @return
     */
    void changeVideoState(String chatterId, Integer state);

    /**
     * 判断是否在线已经超过限定人数
     * @return
     */
    boolean isOnlineChatterOverflow();
}
