package com.mola.molachat.data.impl.leveldb;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.mola.molachat.condition.LevelDBCondition;
import com.mola.molachat.data.LevelDBClient;
import com.mola.molachat.data.impl.cache.OtherData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-02-21 12:00
 **/
@Component
@Conditional(LevelDBCondition.class)
@Slf4j
public class LevelDBOtherData extends OtherData {

    @Resource
    private LevelDBClient levelDBClient;

    @PostConstruct
    public void postConstruct(){
        List<String> availableChildApiKeys = JSONObject.parseObject(levelDBClient.get("gpt3ChildTokens"),
                new TypeReference<List<String>>(){});
        if (!CollectionUtils.isEmpty(availableChildApiKeys)) {
            super.availableChildApiKeys.addAll(availableChildApiKeys);
        }
    }

    @Override
    public Set<String> getGpt3ChildTokens() {
        return super.getGpt3ChildTokens();
    }

    @Override
    public void operateGpt3ChildTokens(Consumer<Set<String>> operate) {
        super.operateGpt3ChildTokens(operate);
        levelDBClient.put("gpt3ChildTokens", JSONObject.toJSONString(availableChildApiKeys));
    }
}
