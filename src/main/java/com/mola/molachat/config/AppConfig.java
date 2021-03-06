package com.mola.molachat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2021-03-24 21:16
 **/
@ConfigurationProperties(prefix = "app")
@EnableConfigurationProperties(AppConfig.class)
@Configuration
@Data
public class AppConfig {

    /**
     * 应用版本
     */
    private String version;

    /**
     * 应用id
     */
    private String id;

    /**
     * 秘钥，用于整合
     */
    private String secret;

    /**
     * webSocket的类型，分为spring、tomcat和netty
     */
    private String serverType;

    /**
     * 机器人appKey
     */
    private String robotList;
}
