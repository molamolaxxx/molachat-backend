package com.mola.molachat.data;

import com.mola.molachat.entity.Chatter;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * @Author: molamola
 * @Date: 19-8-5 下午3:03
 * @Version 1.0
 */
public interface ChatterFactoryInterface {

    /**
     * 增加一个chatter
     * @return
     */
    Chatter create(Chatter chatter);

    /**
     * 保存一个chatter
     * @param chatter
     * @return
     */
    Chatter save(Chatter chatter);

    /**
     * 更新一个chatter
     */
    Chatter update(Chatter chatter);

    /**
     * 移除一个chatter
     * @return
     */
    Chatter remove(Chatter chatter);

    /**
     * 选择一个chatter
     * @return
     */
    Chatter select(String id);

    /**
     * 获取chatter列表
     * @return
     */
    List<Chatter> list();

    /**
     * 获取消息缓存队列
     * @param id
     * @return
     */
    BlockingQueue queue(String id);
}
