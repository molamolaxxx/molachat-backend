package com.mola.molachat.data.impl.redis;

import com.mola.molachat.condition.RedisExistCondition;
import com.mola.molachat.data.impl.cache.OtherData;
import com.mola.molachat.utils.RedisUtil;
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
@Conditional(RedisExistCondition.class)
@Slf4j
public class RedisOtherData extends OtherData {

    @Resource
    private RedisUtil redisUtil;

    @PostConstruct
    public void postConstruct(){
        List<String> availableChildApiKeys = (List<String>) redisUtil.get("gpt3ChildTokens");
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
        redisUtil.set("gpt3ChildTokens", availableChildApiKeys);
    }
}
