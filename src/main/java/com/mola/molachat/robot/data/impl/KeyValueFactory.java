package com.mola.molachat.robot.data.impl;

import com.google.common.collect.Lists;
import com.mola.molachat.common.condition.CacheCondition;
import com.mola.molachat.robot.data.KeyValueFactoryInterface;
import com.mola.molachat.robot.model.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 19:23
 **/
@Component
@Conditional(CacheCondition.class)
@Slf4j
public class KeyValueFactory implements KeyValueFactoryInterface {

    private static Map<String, KeyValue> kvMap;

    public KeyValueFactory(){
        kvMap = new ConcurrentHashMap<>();
    }

    @Override
    public KeyValue save(KeyValue keyValue) {
        Assert.notNull(keyValue, "keyValue is null");
        Assert.notNull(keyValue.getKey(), "key is null");
        kvMap.put(keyValue.getKey(), keyValue);
        return kvMap.get(keyValue.getKey());
    }

    @Override
    public void remove(String key) {
        Assert.notNull(key, "key is null");
        kvMap.remove(key);
    }

    @Override
    public KeyValue selectOne(String key) {
        return kvMap.get(key);
    }

    @Override
    public List<KeyValue> list() {
        return Lists.newArrayList(kvMap.values());
    }
}
