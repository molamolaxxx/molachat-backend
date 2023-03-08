package com.mola.molachat.config;

import com.google.common.collect.Maps;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

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

    /**
     * levelDB的本地存储位置
     */
    private String levelDBStoragePrefix = "/tmp/levelDB";

    /**
     * http代理地址
     */
    private String httpProxy;

    /**
     * 机器人的外部apikey
     */
    private Map<String, String> robotApiKey = Maps.newHashMap();

    /**
     * 最大保留chatter的数量
     */
    private Integer maxRemainChatterCount = 5;
}
