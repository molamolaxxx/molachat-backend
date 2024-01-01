package com.mola.molachat.robot.data;

import com.mola.molachat.robot.model.KeyValue;

import java.util.List;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 19:07
 **/
public interface KeyValueFactoryInterface {


    /**
     * 创建kv
     * @param keyValue
     * @return
     */
    KeyValue save(KeyValue keyValue);

    /**
     * 移除kv
     * @param key
     * @return
     */
    void remove(String key);

    /**
     * 根据key 查询kv
     * @param key
     * @return
     */
    KeyValue selectOne(String key);

    /**
     * 查询所有kv
     * @return
     */
    List<KeyValue> list();
}
