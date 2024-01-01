package com.mola.molachat.chatter.data.impl;

import com.alibaba.fastjson.JSONObject;
import com.mola.molachat.common.annotation.RefreshChatterList;
import com.mola.molachat.common.condition.LevelDBCondition;
import com.mola.molachat.common.LevelDBClient;
import com.mola.molachat.chatter.model.Chatter;
import com.mola.molachat.chatter.model.RobotChatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 利用redis进行chatter数据持久化
 * @date : 2020-06-08 13:49
 **/
@Component
@Conditional(LevelDBCondition.class)
@Slf4j
public class LevelDBChatterFactory extends ChatterFactory {

    private static final String CHATTER_NAMESPACE = "chatter:";

    /**
     * redis-key前缀
     */
    private String levelDBKeyPrefix;

    @Resource
    private LevelDBClient levelDBClient;

    @PostConstruct
    public void postConstruct(){
        levelDBKeyPrefix = CHATTER_NAMESPACE;
        // 从levelDB中读取list放入缓存
        Map<String, String> map = levelDBClient.list(levelDBKeyPrefix);
        map.forEach((k, v) -> {
            super.save(parseFromJsonStr(v));
        });
    }

    private Chatter parseFromJsonStr(String jsonStr) {
        JSONObject jsonObject = JSONObject.parseObject(jsonStr);
        jsonObject.remove("videoState");
        if (jsonObject.containsKey("appKey")) {
            return jsonObject.toJavaObject(RobotChatter.class);
        } else {
            return jsonObject.toJavaObject(Chatter.class);
        }
    }

    @Override
    public Chatter create(Chatter chatter) {
        Chatter target = super.create(chatter);
        levelDBClient.put(levelDBKeyPrefix + chatter.getId(), JSONObject.toJSONString(chatter));
        return target;
    }

    @Override
    public Chatter save(Chatter chatter) {
        Chatter target = super.save(chatter);
        levelDBClient.put(levelDBKeyPrefix + chatter.getId(), JSONObject.toJSONString(chatter));
        return target;
    }

    @Override
    @RefreshChatterList
    public Chatter update(Chatter chatter) {
        Chatter target = super.update(chatter);
        levelDBClient.put(levelDBKeyPrefix + chatter.getId(), JSONObject.toJSONString(chatter));
        return target;
    }

    @Override
    @RefreshChatterList
    public Chatter remove(Chatter chatter) {
        Chatter target = super.remove(chatter);
        levelDBClient.delete(levelDBKeyPrefix + chatter.getId());
        return target;
    }

    @Override
    public Chatter select(String id) {
        // 取一级缓存
        Chatter firstCache = super.select(id);
        if (null == firstCache) {
            // 取二级缓存
            Chatter chatter = JSONObject.parseObject(levelDBClient.get(levelDBKeyPrefix + id), Chatter.class);
            if (null != chatter) {
                super.save(chatter);
            }
        }
        return super.select(id);
    }

    @Override
    public List<Chatter> list() {
        return super.list();
    }

    @Override
    public BlockingQueue queue(String id) {
        return super.queue(id);
    }

}
