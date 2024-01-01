package com.mola.molachat.common.config;

import com.mola.molachat.common.utils.FastJson2JsonRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import javax.annotation.Resource;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-06-08 13:58
 **/
@Configuration
public class RedisConfig {

    @Resource
    private SelfConfig selfConfig;

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        // 初始化redisTemplate
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        //开启事务
        redisTemplate.setEnableTransactionSupport(true);

        //序列化方式，采用json
        FastJson2JsonRedisSerializer serializer = new FastJson2JsonRedisSerializer(Object.class);

        //key序列化方式，采用string
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        //值的序列化，采用String
        redisTemplate.setValueSerializer(serializer);

        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(serializer);
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());
        //设置连接池，此处使用Luttuce nio client
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
