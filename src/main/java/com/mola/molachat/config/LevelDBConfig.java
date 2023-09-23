package com.mola.molachat.config;

import com.mola.molachat.data.LevelDBClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2023-03-02 17:57
 **/
@Configuration
public class LevelDBConfig {

    @Bean(initMethod = "init")
    public LevelDBClient levelDBClient() {
        return new LevelDBClient();
    }
}
