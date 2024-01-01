package com.mola.molachat.robot.data.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.condition.RedisExistCondition;
import com.mola.molachat.common.config.AppConfig;
import com.mola.molachat.robot.model.KeyValue;
import com.mola.molachat.common.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Set;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 19:28
 **/
@Component
@Conditional(RedisExistCondition.class)
@Slf4j
public class RedisKeyValueFactory extends KeyValueFactory {

    private static final String KEY_VALUE_NAMESPACE = "keyValue:";


    @Resource
    private RedisUtil redisUtil;

    @Resource
    private AppConfig appConfig;

    /**
     * key前缀
     */
    private String redisKeyPrefix;

    @PostConstruct
    public void postConstruct(){
        redisKeyPrefix = appConfig.getId() + ":" + KEY_VALUE_NAMESPACE;
        // 从redis中读取list放入缓存
        Set keys = redisUtil.keys(redisKeyPrefix);
        for (Object key : keys) {
            KeyValue keyValue = (KeyValue) redisUtil.get((String) key);
            super.save(keyValue);
        }
    }

    @Override
    public KeyValue save(KeyValue keyValue) {
        KeyValue target = super.save(keyValue);
        redisUtil.set(redisKeyPrefix + target.getKey(), JSONObject.toJSONString(target));
        return target;
    }

    @Override
    public void remove(String key) {
        super.remove(key);
        redisUtil.del(redisKeyPrefix + key);
    }

    @Override
    public KeyValue selectOne(String key) {
        // 取一级缓存
        KeyValue firstCache = super.selectOne(key);
        if (null == firstCache) {
            // 取二级缓存
            Object secondCache = redisUtil.get(redisKeyPrefix + key);
            if (null != secondCache) {
                super.save((KeyValue) secondCache);
            }
        }
        return super.selectOne(key);
    }

    @Override
    public List<KeyValue> list() {
        return super.list();
    }
}
