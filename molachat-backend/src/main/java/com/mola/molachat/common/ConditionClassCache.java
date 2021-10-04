package com.mola.molachat.common;

import com.mola.molachat.condition.CacheCondition;
import com.mola.molachat.condition.RedisExistCondition;
import org.springframework.context.annotation.Condition;

import java.util.HashMap;
import java.util.Map;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-06-13 23:59
 **/
public class ConditionClassCache {

    private static Map<Class<? extends Condition>, String> conditionClassCache = new HashMap<>();

    static {
        conditionClassCache.put(RedisExistCondition.class, "redis");
        conditionClassCache.put(CacheCondition.class, "cache");
    }

    public static String getCache(Class<? extends Condition> clazz) {
        return conditionClassCache.get(clazz);
    }

    public static boolean isCacheTypeExist(String cacheType) {
        for (Class clazz : conditionClassCache.keySet()) {
            if (conditionClassCache.get(clazz).equals(cacheType)) {
                return true;
            }
        }
        return false;
    }
}
