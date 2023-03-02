package com.mola.molachat.config;

import com.mola.molachat.condition.LevelDBCondition;
import com.mola.molachat.data.LevelDBClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-03-02 17:57
 **/
@Configuration
@Conditional(LevelDBCondition.class)
public class LevelDBConfig {

    @Bean(initMethod = "init")
    public LevelDBClient levelDBClient() {
        return new LevelDBClient();
    }
}
