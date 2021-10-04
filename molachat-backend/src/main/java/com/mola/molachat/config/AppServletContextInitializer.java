package com.mola.molachat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.WebAppRootListener;

import javax.servlet.ServletContext;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2020-05-25 19:40
 * 用于代替servlet3.0的
 **/
@Configuration
@Slf4j
public class AppServletContextInitializer implements ServletContextInitializer {
    @Override
    public void onStartup(ServletContext servletContext){
        log.info("servletContext:配置");
        servletContext.addListener(WebAppRootListener.class);
        // 配置单次socket发送最大buffer字节数，因为有的时候信令报文特别大
        servletContext.setInitParameter("org.apache.tomcat.websocket.textBufferSize","1024000");
    }
}
