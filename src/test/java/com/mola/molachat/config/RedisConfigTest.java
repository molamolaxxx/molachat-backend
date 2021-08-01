package com.mola.molachat.config;

import org.junit.Before;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.Set;

public class RedisConfigTest{

    private Jedis jedis;

    @Before
    public void init() {
        jedis = new Jedis("101.133.140.160",6379);
    }

    @Test
    public void changeRedisKeys() {
        final Set<String> keys = jedis.keys("*");
        for (String key : keys) {
            String keyAfter = "molachat001:" + key;
            final String value = jedis.get(key);
            jedis.del(key);
            jedis.set(keyAfter, value);
        }
    }

    @Test
    public void keys() {
        final Set<String> keys = jedis.keys("*");
        for (String key : keys) {
            System.out.println(jedis.get(key));
        }
    }
}