package com.mola.molachat.common.condition;

import com.mola.molachat.common.utils.EnvUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;

/**
 * @author : molamola
 * @Project: molachat
 * @Description: 判断redis环境是否存在的condition
 * @date : 2020-05-07 21:37
 **/
@Slf4j
@Component
public class RedisExistCondition extends BaseExistCondition {

    private static final String REDIS_HOST_KEY = "spring.redis.host";

    private static final String REDIS_PORT_KEY = "spring.redis.port";

    private static final String REDIS_PORT_DEFAULT = "6379";

    private static final String REDIS_HOST_DEFAULT = "127.0.0.1";

    @Override
    public boolean matches(ConditionContext conditionContext, AnnotatedTypeMetadata annotatedTypeMetadata) {
        boolean matches = super.matches(conditionContext, annotatedTypeMetadata);
        if (matches) {
            // 环境条件，判断redis是否存在
            try {
                String host = EnvUtils.getEnvProperty(conditionContext, REDIS_HOST_KEY);
                String port = EnvUtils.getEnvProperty(conditionContext, REDIS_PORT_KEY);
                if (null == host) {
                    host = REDIS_HOST_DEFAULT;
                }
                if (null == port) {
                    port = REDIS_HOST_DEFAULT;
                }
                Jedis jedis = new Jedis(host, Integer.valueOf(port));
                String ping = jedis.ping();
                if (ping.equalsIgnoreCase("PONG")) {
                    log.info("聊天服务器缓存类型：使用了redis作为缓存");
                    return true;
                }
            } catch (Exception e) {
                log.error("聊天服务器缓存类型：使用redis作为缓存失败，加载默认缓存机制");
                // 加载默认缓存
                super.loadDefaultCondition(conditionContext);
                return false;
            }
        }
        return false;
    }
}
