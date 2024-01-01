package com.mola.molachat.common.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * @Author: molamola
 * @Date: 19-8-26 上午11:43
 * @Version 1.0
 * 个人配置
 */
@Component
@Data
@Slf4j
public class SelfConfig {

    /**
     * 检查连接状态为离线的超时时间
     */
    @Value("${self-conf.connect-timeout}")
    private long CONNECT_TIMEOUT;

    /**
     * 检查连接状态为断线的超时时间
     */
    @Value("${self-conf.close-timeout}")
    private long CLOSE_TIMEOUT;

    /**
     * 最大客户端数量
     */
    @Value("${self-conf.max-client-num}")
    private int MAX_CLIENT_NUM;

    /**
     * session最大保存信息数
     */
    @Value("${self-conf.max-session-message-num}")
    private int MAX_SESSION_MESSAGE_NUM;

    /**
     * chatter消息缓冲队列最大消息数
     */
    @Value("${self-conf.max-message-queue-num}")
    private int MAX_MESSAGE_QUEUE_NUM;
    /**
     * 上传文件保存地址
     */
    @Value("${self-conf.upload-file-path}")
    private String uploadFilePath;

    @Value("${self-conf.max-file-size}")
    private Integer maxFileSize;

    @Value("${self-conf.max-request-size}")
    private Integer maxRequestSize;

    /**
     * 是否使用redis作为持久化存储
     */
    @Value("${self-conf.cache-type}")
    private String cacheType;


}
