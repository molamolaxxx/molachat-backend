package com.mola.molachat.robot.data.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.condition.LevelDBCondition;
import com.mola.molachat.common.LevelDBClient;
import com.mola.molachat.robot.model.KeyValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-09-23 19:28
 **/
@Component
@Conditional(LevelDBCondition.class)
@Slf4j
public class LevelDBKeyValueFactory extends KeyValueFactory {

    private static final String KEY_VALUE_NAMESPACE = "keyValue:";


    /**
     * key前缀
     */
    private String levelDBKeyPrefix;

    @Resource
    private LevelDBClient levelDBClient;

    @PostConstruct
    public void postConstruct(){
        levelDBKeyPrefix = KEY_VALUE_NAMESPACE;
        // 从levelDB中读取list放入缓存
        Map<String, String> map = levelDBClient.list(levelDBKeyPrefix);
        map.forEach((k, v) -> super.save(JSONObject.parseObject(v, KeyValue.class)));
    }

    @Override
    public KeyValue save(KeyValue keyValue) {
        KeyValue target = super.save(keyValue);
        levelDBClient.put(levelDBKeyPrefix + target.getKey(), JSONObject.toJSONString(target));
        return target;
    }

    @Override
    public void remove(String key) {
        super.remove(key);
        levelDBClient.delete(levelDBKeyPrefix + key);
    }

    @Override
    public KeyValue selectOne(String key) {
        // 取一级缓存
        KeyValue firstCache = super.selectOne(key);
        if (null == firstCache) {
            // 取二级缓存
            KeyValue kv = JSONObject.parseObject(levelDBClient.get(levelDBKeyPrefix + key), KeyValue.class);
            if (null != kv) {
                super.save(kv);
            }
        }
        return super.selectOne(key);
    }

    @Override
    public List<KeyValue> list() {
        return super.list();
    }
}
