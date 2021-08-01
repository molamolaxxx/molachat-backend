package com.mola.molachat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import javax.annotation.Resource;
import javax.servlet.MultipartConfigElement;

/**
 * @Author: molamola
 * @Date: 19-9-12 下午12:11
 * @Version 1.0
 */
@Configuration
public class TomcatConfig {

    @Resource
    private SelfConfig config;

    @Value("${server.port}")
    private Integer port;

    @Bean
    public MultipartConfigElement multipartConfigElement(){

        MultipartConfigFactory factory = new MultipartConfigFactory();
        //最大储存文件200m
        factory.setMaxFileSize(DataSize.ofMegabytes(config.getMaxFileSize()));
        //最大请求文按键300M
        factory.setMaxRequestSize(DataSize.ofMegabytes(config.getMaxRequestSize()));

        return factory.createMultipartConfig();
    }

}
